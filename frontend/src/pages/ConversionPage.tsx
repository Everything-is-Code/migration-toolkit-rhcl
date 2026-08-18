import React, { useState } from 'react';
import { loadSupportedPolicies } from './SupportedPoliciesPage';
import { corsConversionHintKey } from './clusterCapabilityUi';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  Button,
  Alert,
  Spinner,
  Progress,
  DataList,
  DataListItem,
  DataListItemRow,
  DataListItemCells,
  DataListCell,
  Label,
  Stack,
  StackItem,
  Checkbox,
  TextInput,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Form,
  Radio,
} from '@patternfly/react-core';
import { CheckCircleIcon, TimesCircleIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { clusterApi, conversionApi } from '../api/client';
import { ConversionResultItem } from '../api/types';
import { AppState } from '../App';
import { useNavigate } from 'react-router-dom';

/** Match backend toKebabCase for hostname prefill: {kebab}.{clusterDomain}. */
function toKebabName(raw: string): string {
  return raw
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

interface Props {
  appState: AppState;
  setAppState: React.Dispatch<React.SetStateAction<AppState>>;
}

const ConversionPage: React.FC<Props> = ({ appState, setAppState }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<ConversionResultItem[]>(appState.conversionResults);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState(0);
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

  // Show the corresponding target setting only when any selected service
  // has a Logging / Anonymous Access / IP Check policy enabled.
  const hasLoggingPolicy = appState.selectedServices.some(svc =>
    svc.policies?.some(p => p.enabled && p.name === 'logging'));
  const hasAnonymousPolicy = appState.selectedServices.some(svc =>
    svc.policies?.some(p => p.enabled
      && (p.name === 'default_credentials' || p.name === 'anonymous_access')));
  const hasIpCheckPolicy = appState.selectedServices.some(svc =>
    svc.policies?.some(p => p.enabled && p.name === 'ip_check'));
  const hasCorsPolicy = appState.selectedServices.some(svc =>
    svc.policies?.some(p => p.enabled && p.name === 'cors'));
  const corsNative = appState.clusterVersions?.capabilities?.corsNative === true;
  const corsHintKey = corsConversionHintKey(hasCorsPolicy, corsNative);

  const handleConvert = async () => {
    setLoading(true);
    setError(null);
    setProgress(10);

    try {
      const supportedPolicies = await loadSupportedPolicies();
      const resp = await conversionApi.convert({
        threescaleUrl: appState.connection.url,
        accessToken: appState.connection.accessToken,
        tenant: appState.connection.tenant,
        namespace: appState.namespace,
        serviceIds: appState.selectedServices.map(s => s.id),
        externalBackendUrl: isExternal && externalBackendUrl ? externalBackendUrl : undefined,
        supportedPolicies,
        loggingTarget,
        anonymousTarget,
        includeMigratedFromLabel,
        ipCheckMode,
        includeTlsPolicy: includeTlsPolicy || undefined,
        tlsIssuerKind: includeTlsPolicy ? tlsIssuerKind : undefined,
        tlsIssuerName: includeTlsPolicy ? tlsIssuerName : undefined,
        includeDnsPolicy: includeDnsPolicy || undefined,
        dnsHostname: includeDnsPolicy ? dnsHostname || undefined : undefined,
        dnsProviderSecretName:
          includeDnsPolicy && dnsProviderSecretName.trim()
            ? dnsProviderSecretName.trim()
            : undefined,
      });
      setProgress(100);
      const convResults: ConversionResultItem[] = resp.data.results;
      setResults(convResults);
      setAppState(prev => ({ ...prev, conversionResults: convResults }));
    } catch (e: any) {
      setError(t('conversion.errorConvert', { message: e.response?.data?.error || e.message }));
    } finally {
      setLoading(false);
    }
  };

  if (appState.selectedServices.length === 0) {
    return (
      <PageSection>
        <Alert variant="warning" title={t('conversion.warningTitle')}>
          <Button variant="link" onClick={() => navigate('/services')}>{t('conversion.goToApiList')}</Button>
        </Alert>
      </PageSection>
    );
  }

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('conversion.title')}</Title>
        <p style={{ marginTop: '8px', color: '#6a6e73' }}>
          {t('conversion.description', { count: appState.selectedServices.length })}
        </p>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          {corsHintKey && (
            <StackItem>
              <Alert
                variant={corsNative ? 'info' : 'warning'}
                isInline
                title={t(corsHintKey)}
              />
            </StackItem>
          )}
          <StackItem>
            <Card>
              <CardBody>
                <Title headingLevel="h3" size="lg">{t('conversion.targetTitle')}</Title>
                <DataList aria-label={t('conversion.ariaTarget')} style={{ marginTop: '16px' }}>
                  {appState.selectedServices.map(svc => (
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

                <div style={{ marginTop: '20px', padding: '16px', background: '#f0f4f8', border: '1px solid #bee1f4', borderRadius: '6px' }}>
                  <div style={{ fontWeight: 600, fontSize: '14px', marginBottom: '12px', color: '#004080' }}>
                    {t('conversion.backendType', 'Backend Settings')}
                  </div>
                  <Form>
                    <FormGroup>
                      <Checkbox
                        id="external-backend"
                        label={t('conversion.externalBackend', 'Backend is an external service (AWS ECS / external HTTPS endpoint)')}
                        isChecked={isExternal}
                        onChange={(_e, checked) => setIsExternal(checked)}
                      />
                    </FormGroup>
                    {isExternal && (
                      <>
                        <FormGroup
                          label={t('conversion.externalBackendUrl', 'External Backend URL')}
                          fieldId="external-backend-url"
                          isRequired
                        >
                          <TextInput
                            id="external-backend-url"
                            type="url"
                            value={externalBackendUrl}
                            onChange={(_e, val) => setExternalBackendUrl(val)}
                            placeholder={t('conversion.externalBackendUrlPlaceholder')}
                          />
                          <FormHelperText>
                            <HelperText>
                              <HelperTextItem>
                                {t('conversion.externalBackendUrlHelp', 'e.g.: https://foo.ecs.us-east-2.on.aws')}
                              </HelperTextItem>
                            </HelperText>
                          </FormHelperText>
                        </FormGroup>
                        <div style={{
                          marginTop: '12px',
                          padding: '12px',
                          background: '#fff8e1',
                          border: '1px solid #f0ab00',
                          borderRadius: '4px',
                          fontSize: '13px',
                          color: '#795600',
                        }}>
                          <div style={{ fontWeight: 600, marginBottom: '6px' }}>
                            {t('conversion.externalNote', 'The following resources will be additionally generated for external services:')}
                          </div>
                          <ul style={{ margin: 0, paddingLeft: '18px', lineHeight: '1.8' }}>
                            <li dangerouslySetInnerHTML={{ __html: t('conversion.externalNoteEnvoy') }} />
                            <li dangerouslySetInnerHTML={{ __html: t('conversion.externalNoteRoute') }} />
                          </ul>
                        </div>
                      </>
                    )}
                  </Form>
                </div>

                {/* Output settings form */}
                <div style={{ marginTop: '16px', padding: '16px', background: '#f0f4f8', border: '1px solid #bee1f4', borderRadius: '6px' }}>
                  <div style={{ fontWeight: 600, fontSize: '14px', marginBottom: '16px', color: '#004080' }}>
                    {t('conversion.outputSettings', 'Output Settings')}
                  </div>
                  <Form>
                    <FormGroup>
                      <Checkbox
                        id="include-migrated-from-label"
                        label={t('conversion.includeMigratedFromLabel', 'Add migrated-from: 3scale label')}
                        isChecked={includeMigratedFromLabel}
                        onChange={(_e, checked) => setIncludeMigratedFromLabel(checked)}
                      />
                    </FormGroup>
                    <FormGroup>
                      <Checkbox
                        id="include-tls-policy"
                        label={t('conversion.includeTlsPolicy', 'Generate TLSPolicy (cert-manager)')}
                        isChecked={includeTlsPolicy}
                        onChange={(_e, checked) => {
                          setIncludeTlsPolicy(checked);
                          if (checked) {
                            setTlsIssuerKind((prev) => prev || 'ClusterIssuer');
                            setTlsIssuerName((prev) => prev || 'letsencrypt-prod');
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
                        <FormGroup
                          label={t('conversion.tlsIssuerKind', 'TLS issuer kind')}
                          fieldId="tls-issuer-kind"
                        >
                          <TextInput
                            id="tls-issuer-kind"
                            value={tlsIssuerKind}
                            onChange={(_e, val) => setTlsIssuerKind(val)}
                            aria-label={t('conversion.tlsIssuerKind', 'TLS issuer kind')}
                          />
                        </FormGroup>
                        <FormGroup
                          label={t('conversion.tlsIssuerName', 'TLS issuer name')}
                          fieldId="tls-issuer-name"
                        >
                          <TextInput
                            id="tls-issuer-name"
                            value={tlsIssuerName}
                            onChange={(_e, val) => setTlsIssuerName(val)}
                            aria-label={t('conversion.tlsIssuerName', 'TLS issuer name')}
                          />
                          <FormHelperText>
                            <HelperText>
                              <HelperTextItem>
                                {t(
                                  'conversion.tlsIssuerHelp',
                                  'Prefills ClusterIssuer / letsencrypt-prod when TLSPolicy is enabled. Edit if your cluster uses a different issuer.',
                                )}
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
                        onChange={async (_e, checked) => {
                          setIncludeDnsPolicy(checked);
                          if (!checked) {
                            return;
                          }
                          // Prefill once when enabling if the field is still empty.
                          if (dnsHostname.trim()) {
                            return;
                          }
                          const first = appState.selectedServices[0];
                          const kebab = toKebabName(
                            (first?.systemName || first?.name || '').trim() || 'app',
                          );
                          try {
                            const res = await clusterApi.getDomain();
                            const domain = res.data?.domain?.trim();
                            // cluster domain already includes apps. prefix — do not add another.
                            if (domain) {
                              setDnsHostname(`${kebab}.${domain}`);
                            }
                          } catch {
                            // Domain API failure: leave hostname empty/editable (no hard fail).
                          }
                        }}
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
                            onChange={(_e, val) => setDnsHostname(val)}
                            aria-label={t('conversion.dnsHostname', 'Gateway hostname')}
                            placeholder="my-app.apps.cluster.example.com"
                          />
                          <FormHelperText>
                            <HelperText>
                              <HelperTextItem>
                                {t(
                                  'conversion.dnsHostnameHelp',
                                  'Applied to both http and https listeners. Override the prefill if needed.',
                                )}
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
                            onChange={(_e, val) => setDnsProviderSecretName(val)}
                            aria-label={t(
                              'conversion.dnsProviderSecretName',
                              'DNS provider Secret name (optional)',
                            )}
                          />
                          <FormHelperText>
                            <HelperText>
                              <HelperTextItem>
                                {t(
                                  'conversion.dnsProviderSecretHelp',
                                  'If set, DNSPolicy includes providerRefs[{name}]. If blank, omit providerRefs and rely on the cluster default-provider Secret. Never embed credentials in the package.',
                                )}
                              </HelperTextItem>
                            </HelperText>
                          </FormHelperText>
                        </FormGroup>
                      </>
                    )}
                  </Form>
                </div>

                {/* Policy settings form (shown only when Logging / Anonymous Access / IP Check policies are configured) */}
                {(hasLoggingPolicy || hasAnonymousPolicy || hasIpCheckPolicy) && (
                  <div style={{ marginTop: '16px', padding: '16px', background: '#f0f4f8', border: '1px solid #bee1f4', borderRadius: '6px' }}>
                    <div style={{ fontWeight: 600, fontSize: '14px', marginBottom: '16px', color: '#004080' }}>
                      {t('conversion.policySettings', 'Policy Settings')}
                    </div>

                    {hasLoggingPolicy && (
                      <div style={{ marginBottom: (hasAnonymousPolicy || hasIpCheckPolicy) ? '16px' : 0 }}>
                        <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px', color: '#151515' }}>
                          {t('conversion.loggingTarget', 'Logging Policy Target')}
                        </div>
                        <div style={{ display: 'flex', gap: '24px' }}>
                          <Radio
                            id="logging-target-gateway"
                            name="loggingTarget"
                            label={t('conversion.loggingTargetGateway', 'Gateway Pod (recommended)')}
                            isChecked={loggingTarget === 'gateway'}
                            onChange={() => setLoggingTarget('gateway')}
                            description={t('conversion.loggingTargetGatewayDesc', 'context: GATEWAY / istio.io/gateway-name selector')}
                          />
                          <Radio
                            id="logging-target-workload"
                            name="loggingTarget"
                            label={t('conversion.loggingTargetWorkload', 'Workload Pod')}
                            isChecked={loggingTarget === 'workload'}
                            onChange={() => setLoggingTarget('workload')}
                            description={t('conversion.loggingTargetWorkloadDesc', 'context: SIDECAR_INBOUND / app selector')}
                          />
                        </div>
                      </div>
                    )}

                    {hasAnonymousPolicy && (
                      <div style={{ marginBottom: hasIpCheckPolicy ? '16px' : 0 }}>
                        <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px', color: '#151515' }}>
                          {t('conversion.anonymousTarget', 'Anonymous Access Policy Target')}
                        </div>
                        <div style={{ display: 'flex', gap: '24px' }}>
                          <Radio
                            id="anonymous-target-gateway"
                            name="anonymousTarget"
                            label={t('conversion.anonymousTargetGateway', 'Gateway')}
                            isChecked={anonymousTarget === 'gateway'}
                            onChange={() => setAnonymousTarget('gateway')}
                            description={t('conversion.anonymousTargetGatewayDesc', 'targetRef.kind: Gateway — Applies to all routes via Gateway')}
                          />
                          <Radio
                            id="anonymous-target-httproute"
                            name="anonymousTarget"
                            label={t('conversion.anonymousTargetHttpRoute', 'HTTPRoute (recommended)')}
                            isChecked={anonymousTarget === 'httproute'}
                            onChange={() => setAnonymousTarget('httproute')}
                            description={t('conversion.anonymousTargetHttpRouteDesc', 'targetRef.kind: HTTPRoute — Applies to specific routes only')}
                          />
                        </div>
                      </div>
                    )}

                    {hasIpCheckPolicy && (
                      <div>
                        <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px', color: '#151515' }}>
                          {t('conversion.ipCheckMode', 'IP Check target')}
                        </div>
                        <div style={{ display: 'flex', gap: '24px' }}>
                          <Radio
                            id="ip-check-authorization-policy"
                            name="ipCheckMode"
                            label={t('conversion.ipCheckModeAuthz', 'AuthorizationPolicy (recommended)')}
                            isChecked={ipCheckMode === 'authorizationPolicy'}
                            onChange={() => setIpCheckMode('authorizationPolicy')}
                            description={t('conversion.ipCheckModeAuthzDesc', 'Istio AuthorizationPolicy with remoteIpBlocks')}
                          />
                          <Radio
                            id="ip-check-auth-policy-opa"
                            name="ipCheckMode"
                            label={t('conversion.ipCheckModeOpa', 'AuthPolicy / OPA')}
                            isChecked={ipCheckMode === 'authPolicyOpa'}
                            onChange={() => setIpCheckMode('authPolicyOpa')}
                            description={t('conversion.ipCheckModeOpaDesc', 'Kuadrant AuthPolicy authorization with OPA/Rego')}
                          />
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {error && <Alert variant="danger" title={error} style={{ marginTop: '16px' }} />}

                {loading && (
                  <div style={{ marginTop: '16px' }}>
                    <Progress value={progress} title={t('conversion.progressTitle')} />
                    <div style={{ textAlign: 'center', marginTop: '8px' }}>
                      <Spinner size="md" /> {t('conversion.converting')}
                    </div>
                  </div>
                )}

                <div style={{ marginTop: '24px', display: 'flex', gap: '8px' }}>
                  <Button variant="secondary" onClick={() => navigate('/compatibility')}>{t('conversion.btnBack')}</Button>
                  <Button
                    variant="primary"
                    onClick={handleConvert}
                    isDisabled={loading}
                  >
                    {loading ? t('conversion.btnConverting') : results.length > 0 ? t('conversion.btnReconvert') : t('conversion.btnConvert')}
                  </Button>
                </div>
              </CardBody>
            </Card>
          </StackItem>

          {results.length > 0 && (
            <StackItem>
              <Card>
                <CardBody>
                  <Title headingLevel="h3" size="lg">{t('conversion.resultTitle')}</Title>
                  <DataList aria-label={t('conversion.ariaResult')} style={{ marginTop: '16px' }}>
                    {results.map(result => (
                      <DataListItem key={result.serviceId}>
                        <DataListItemRow>
                          <DataListItemCells
                            dataListCells={[
                              <DataListCell key="icon">
                                {result.status === 'FAILED'
                                  ? <TimesCircleIcon color="red" />
                                  : <CheckCircleIcon color="green" />}
                              </DataListCell>,
                              <DataListCell key="name" width={2}>
                                <strong>{result.serviceName}</strong>
                              </DataListCell>,
                              <DataListCell key="score">
                                Score: {result.compatibilityScore}%
                              </DataListCell>,
                              <DataListCell key="files">
                                {result.files ? (
                                  <div>
                                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                                      {result.files.map(f => {
                                        const isExternalFile = f === 'envoyfilter.yaml';
                                        return (
                                          <Label
                                            key={f}
                                            isCompact
                                            color={isExternalFile ? 'orange' : 'blue'}
                                            title={isExternalFile ? t('conversion.externalFileTitle') : undefined}
                                          >
                                            {f}
                                          </Label>
                                        );
                                      })}
                                    </div>
                                    {result.files.includes('envoyfilter.yaml') && (
                                      <div style={{
                                        marginTop: '6px',
                                        fontSize: '12px',
                                        color: '#795600',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '4px',
                                      }}>
                                        <span style={{ color: '#f0ab00' }}>●</span>
                                        {t('conversion.externalFilesNote', 'Includes resources for external services (EnvoyFilter + Host rewrite)')}
                                      </div>
                                    )}
                                  </div>
                                ) : result.error}
                              </DataListCell>,
                            ]}
                          />
                        </DataListItemRow>
                      </DataListItem>
                    ))}
                  </DataList>
                  <div style={{ marginTop: '16px' }}>
                    <Button variant="primary" onClick={() => navigate('/yaml')}>
                      {t('conversion.btnNext')}
                    </Button>
                  </div>
                </CardBody>
              </Card>
            </StackItem>
          )}
        </Stack>
      </PageSection>
    </>
  );
};

export default ConversionPage;
