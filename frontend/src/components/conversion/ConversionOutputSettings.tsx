import React from 'react';
import {
  Checkbox,
  TextInput,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Form,
} from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import type { ApiService } from '../../api/types';
import { clusterApi } from '../../api/client';
import { toKebabName } from './conversionUtils';
import styles from '../../styles/shared.module.css';

interface Props {
  selectedServices: ApiService[];
  includeMigratedFromLabel: boolean;
  includeTlsPolicy: boolean;
  tlsIssuerKind: string;
  tlsIssuerName: string;
  includeDnsPolicy: boolean;
  dnsHostname: string;
  dnsProviderSecretName: string;
  onIncludeMigratedFromLabelChange: (checked: boolean) => void;
  onIncludeTlsPolicyChange: (checked: boolean) => void;
  onTlsIssuerKindChange: (value: string) => void;
  onTlsIssuerNameChange: (value: string) => void;
  onIncludeDnsPolicyChange: (checked: boolean) => void;
  onDnsHostnameChange: (value: string) => void;
  onDnsProviderSecretNameChange: (value: string) => void;
}

const ConversionOutputSettings: React.FC<Props> = ({
  selectedServices,
  includeMigratedFromLabel,
  includeTlsPolicy,
  tlsIssuerKind,
  tlsIssuerName,
  includeDnsPolicy,
  dnsHostname,
  dnsProviderSecretName,
  onIncludeMigratedFromLabelChange,
  onIncludeTlsPolicyChange,
  onTlsIssuerKindChange,
  onTlsIssuerNameChange,
  onIncludeDnsPolicyChange,
  onDnsHostnameChange,
  onDnsProviderSecretNameChange,
}) => {
  const { t } = useTranslation();

  const handleDnsPolicyToggle = async (_e: React.FormEvent<HTMLInputElement>, checked: boolean) => {
    onIncludeDnsPolicyChange(checked);
    if (!checked || dnsHostname.trim()) return;
    const first = selectedServices[0];
    const kebab = toKebabName(
      (first?.systemName || first?.name || '').trim() || 'app',
    );
    try {
      const res = await clusterApi.getDomain();
      const domain = res.data?.domain?.trim();
      if (domain) onDnsHostnameChange(`${kebab}.${domain}`);
    } catch {
      // Domain API failure: leave hostname empty/editable.
    }
  };

  return (
    <div className={styles.bluePanel}>
      <div className={styles.bluePanelTitle}>
        {t('conversion.outputSettings', 'Output Settings')}
      </div>
      <Form>
        <FormGroup>
          <Checkbox
            id="include-migrated-from-label"
            label={t('conversion.includeMigratedFromLabel', 'Add migrated-from: 3scale label')}
            isChecked={includeMigratedFromLabel}
            onChange={(_e, checked) => onIncludeMigratedFromLabelChange(checked)}
          />
        </FormGroup>
        <FormGroup>
          <Checkbox
            id="include-tls-policy"
            label={t('conversion.includeTlsPolicy', 'Generate TLSPolicy (cert-manager)')}
            isChecked={includeTlsPolicy}
            onChange={(_e, checked) => {
              onIncludeTlsPolicyChange(checked);
              if (checked) {
                onTlsIssuerKindChange(tlsIssuerKind || 'ClusterIssuer');
                onTlsIssuerNameChange(tlsIssuerName || 'letsencrypt-prod');
              }
            }}
            description={t(
              'conversion.includeTlsPolicyDesc',
              'Opt-in Kuadrant TLSPolicy targeting the Gateway. Requires a ClusterIssuer (default: letsencrypt-prod). Secret {name}-tls is issued by cert-manager — no Certificate CR is generated.',
            )}
          />
        </FormGroup>
        {includeTlsPolicy && (
          <>
            <FormGroup label={t('conversion.tlsIssuerKind', 'TLS issuer kind')} fieldId="tls-issuer-kind">
              <TextInput
                id="tls-issuer-kind"
                value={tlsIssuerKind}
                onChange={(_e, val) => onTlsIssuerKindChange(val)}
                aria-label={t('conversion.tlsIssuerKind', 'TLS issuer kind')}
              />
            </FormGroup>
            <FormGroup label={t('conversion.tlsIssuerName', 'TLS issuer name')} fieldId="tls-issuer-name">
              <TextInput
                id="tls-issuer-name"
                value={tlsIssuerName}
                onChange={(_e, val) => onTlsIssuerNameChange(val)}
                aria-label={t('conversion.tlsIssuerName', 'TLS issuer name')}
              />
              <FormHelperText>
                <HelperText>
                  <HelperTextItem>
                    {t('conversion.tlsIssuerHelp', 'Prefills ClusterIssuer / letsencrypt-prod when TLSPolicy is enabled. Edit if your cluster uses a different issuer.')}
                  </HelperTextItem>
                </HelperText>
              </FormHelperText>
            </FormGroup>
          </>
        )}
        <FormGroup>
          <Checkbox
            id="include-dns-policy"
            label={t('conversion.includeDnsPolicy', 'Generate DNSPolicy + Gateway hostname')}
            isChecked={includeDnsPolicy}
            onChange={handleDnsPolicyToggle}
            description={t(
              'conversion.includeDnsPolicyDesc',
              'Sets hostname on both Gateway http and https listeners and emits dnspolicy.yaml. Prefill uses {kebabName}.{clusterDomain} (domain already has apps.).',
            )}
          />
        </FormGroup>
        {includeDnsPolicy && (
          <>
            <FormGroup
              label={t('conversion.dnsHostname', 'Gateway hostname')}
              fieldId="dns-hostname"
              isRequired
            >
              <TextInput
                id="dns-hostname"
                value={dnsHostname}
                onChange={(_e, val) => onDnsHostnameChange(val)}
                aria-label={t('conversion.dnsHostname', 'Gateway hostname')}
                placeholder="my-app.apps.cluster.example.com"
              />
              <FormHelperText>
                <HelperText>
                  <HelperTextItem>
                    {t('conversion.dnsHostnameHelp', 'Applied to both http and https listeners. Override the prefill if needed.')}
                  </HelperTextItem>
                </HelperText>
              </FormHelperText>
            </FormGroup>
            <FormGroup
              label={t('conversion.dnsProviderSecretName', 'DNS provider Secret name (optional)')}
              fieldId="dns-provider-secret"
            >
              <TextInput
                id="dns-provider-secret"
                value={dnsProviderSecretName}
                onChange={(_e, val) => onDnsProviderSecretNameChange(val)}
                aria-label={t('conversion.dnsProviderSecretName', 'DNS provider Secret name (optional)')}
              />
              <FormHelperText>
                <HelperText>
                  <HelperTextItem>
                    {t('conversion.dnsProviderSecretHelp', 'If set, DNSPolicy includes providerRefs[{name}]. If blank, omit providerRefs and rely on the cluster default-provider Secret. Never embed credentials in the package.')}
                  </HelperTextItem>
                </HelperText>
              </FormHelperText>
            </FormGroup>
          </>
        )}
      </Form>
    </div>
  );
};

export default ConversionOutputSettings;
