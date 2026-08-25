import React, { useState } from 'react';
import {
  Card,
  CardBody,
  Title,
  DataList,
  DataListItem,
  DataListItemRow,
  DataListItemCells,
  DataListCell,
  Alert,
  Spinner,
  Progress,
  Button,
} from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import ConversionBackendSettings from './ConversionBackendSettings';
import ConversionOutputSettings from './ConversionOutputSettings';
import ConversionPolicySettings from './ConversionPolicySettings';
import type { ConversionFormOptions } from './conversionFormTypes';
import type { ApiService, ConversionResultItem } from '../../api/types';
import styles from '../../styles/shared.module.css';

interface Props {
  loading: boolean;
  error: string | null;
  progress: number;
  selectedServices: ApiService[];
  conversionResults: ConversionResultItem[];
  onConvert: (options: ConversionFormOptions) => void;
  onBack: () => void;
}

const ConversionForm: React.FC<Props> = ({ loading, error, progress, selectedServices, conversionResults, onConvert, onBack }) => {
  const { t } = useTranslation();

  const [isExternal, setIsExternal] = useState(false);
  const [externalBackendUrl, setExternalBackendUrl] = useState('');
  const [loggingTarget, setLoggingTarget] = useState<'gateway' | 'workload'>('gateway');
  const [anonymousTarget, setAnonymousTarget] = useState<'httproute' | 'gateway'>('httproute');
  const [ipCheckMode, setIpCheckMode] = useState<'authorizationPolicy' | 'authPolicyOpa'>('authorizationPolicy');
  const [includeMigratedFromLabel, setIncludeMigratedFromLabel] = useState(true);
  const [includeTlsPolicy, setIncludeTlsPolicy] = useState(false);
  const [tlsIssuerKind, setTlsIssuerKind] = useState('ClusterIssuer');
  const [tlsIssuerName, setTlsIssuerName] = useState('letsencrypt-prod');
  const [includeDnsPolicy, setIncludeDnsPolicy] = useState(false);
  const [dnsHostname, setDnsHostname] = useState('');
  const [dnsProviderSecretName, setDnsProviderSecretName] = useState('');

  const hasLoggingPolicy = selectedServices.some(svc =>
    svc.policies?.some(p => p.enabled && p.name === 'logging'));
  const hasAnonymousPolicy = selectedServices.some(svc =>
    svc.policies?.some(p => p.enabled
      && (p.name === 'default_credentials' || p.name === 'anonymous_access')));
  const hasIpCheckPolicy = selectedServices.some(svc =>
    svc.policies?.some(p => p.enabled && p.name === 'ip_check'));

  const results = conversionResults;

  const handleConvertClick = () => {
    onConvert({
      isExternal,
      externalBackendUrl,
      loggingTarget,
      anonymousTarget,
      ipCheckMode,
      includeMigratedFromLabel,
      includeTlsPolicy,
      tlsIssuerKind,
      tlsIssuerName,
      includeDnsPolicy,
      dnsHostname,
      dnsProviderSecretName,
    });
  };

  return (
    <Card>
      <CardBody>
        <Title headingLevel="h3" size="lg">{t('conversion.targetTitle')}</Title>
        <DataList aria-label={t('conversion.ariaTarget')} style={{ marginTop: '16px' }}>
          {selectedServices.map(svc => (
            <DataListItem key={svc.id}>
              <DataListItemRow>
                <DataListItemCells
                  dataListCells={[
                    <DataListCell key="name" width={3}>
                      <strong>{svc.name}</strong>
                      <br />
                      <small>{svc.systemName}</small>
                    </DataListCell>,
                    <DataListCell key="auth">
                      Auth: {svc.authentication?.type || 'none'}
                    </DataListCell>,
                    <DataListCell key="backends">
                      Backends: {svc.backends?.length || 0}
                    </DataListCell>,
                  ]}
                />
              </DataListItemRow>
            </DataListItem>
          ))}
        </DataList>

        <ConversionBackendSettings
          isExternal={isExternal}
          externalBackendUrl={externalBackendUrl}
          onExternalChange={setIsExternal}
          onExternalUrlChange={setExternalBackendUrl}
        />

        <ConversionOutputSettings
          selectedServices={selectedServices}
          includeMigratedFromLabel={includeMigratedFromLabel}
          includeTlsPolicy={includeTlsPolicy}
          tlsIssuerKind={tlsIssuerKind}
          tlsIssuerName={tlsIssuerName}
          includeDnsPolicy={includeDnsPolicy}
          dnsHostname={dnsHostname}
          dnsProviderSecretName={dnsProviderSecretName}
          onIncludeMigratedFromLabelChange={setIncludeMigratedFromLabel}
          onIncludeTlsPolicyChange={setIncludeTlsPolicy}
          onTlsIssuerKindChange={setTlsIssuerKind}
          onTlsIssuerNameChange={setTlsIssuerName}
          onIncludeDnsPolicyChange={setIncludeDnsPolicy}
          onDnsHostnameChange={setDnsHostname}
          onDnsProviderSecretNameChange={setDnsProviderSecretName}
        />

        <ConversionPolicySettings
          hasLoggingPolicy={hasLoggingPolicy}
          hasAnonymousPolicy={hasAnonymousPolicy}
          hasIpCheckPolicy={hasIpCheckPolicy}
          loggingTarget={loggingTarget}
          anonymousTarget={anonymousTarget}
          ipCheckMode={ipCheckMode}
          onLoggingTargetChange={setLoggingTarget}
          onAnonymousTargetChange={setAnonymousTarget}
          onIpCheckModeChange={setIpCheckMode}
        />

        {error && <Alert variant="danger" title={error} style={{ marginTop: '16px' }} />}

        {loading && (
          <div style={{ marginTop: '16px' }}>
            <Progress value={progress} title={t('conversion.progressTitle')} />
            <div style={{ textAlign: 'center', marginTop: '8px' }}>
              <Spinner size="md" /> {t('conversion.converting')}
            </div>
          </div>
        )}

        <div className={styles.actionRow} style={{ marginTop: '24px' }}>
          <Button variant="secondary" onClick={onBack}>
            {t('conversion.btnBack')}
          </Button>
          <Button variant="primary" onClick={handleConvertClick} isDisabled={loading}>
            {loading
              ? t('conversion.btnConverting')
              : results.length > 0
                ? t('conversion.btnReconvert')
                : t('conversion.btnConvert')}
          </Button>
        </div>
      </CardBody>
    </Card>
  );
};

export default ConversionForm;
