import React, { useState } from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Alert,
  Button,
  Stack,
  StackItem,
} from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { conversionApi } from '../api/client';
import { corsConversionHintKey } from '../utils/clusterCapabilityUi';
import { loadSupportedPolicies } from '../utils/supportedPolicies';
import { apiErrorMessage } from '../utils/apiError';
import { useAppState } from '../components/AppStateContext';
import ConversionForm from '../components/conversion/ConversionForm';
import ConversionResults from '../components/conversion/ConversionResults';
import type { ConversionFormOptions } from '../components/conversion/conversionFormTypes';
import styles from '../styles/shared.module.css';

const ConversionPage: React.FC = () => {
  const { appState, setAppState } = useAppState();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState(0);

  const hasCorsPolicy = appState.selectedServices.some(svc =>
    svc.policies?.some(p => p.enabled && p.name === 'cors'));
  const corsNative = appState.clusterVersions?.capabilities?.corsNative === true;
  const corsHintKey = corsConversionHintKey(hasCorsPolicy, corsNative);

  const handleConvert = async (options: ConversionFormOptions) => {
    setLoading(true);
    setError(null);
    setProgress(10);

    try {
      if (options.includeDnsPolicy && !options.dnsHostname.trim()) {
        setError(t(
          'conversion.errorDnsHostnameRequired',
          'Gateway hostname is required when DNSPolicy generation is enabled.',
        ));
        return;
      }
      const supportedPolicies = await loadSupportedPolicies();
      const resp = await conversionApi.convert({
        threescaleUrl: appState.connection.url,
        accessToken: appState.connection.accessToken,
        tenant: appState.connection.tenant,
        namespace: appState.namespace,
        serviceIds: appState.selectedServices.map(s => s.id),
        externalBackendUrl: options.isExternal && options.externalBackendUrl
          ? options.externalBackendUrl
          : undefined,
        supportedPolicies,
        loggingTarget: options.loggingTarget,
        anonymousTarget: options.anonymousTarget,
        includeMigratedFromLabel: options.includeMigratedFromLabel,
        ipCheckMode: options.ipCheckMode,
        includeTlsPolicy: options.includeTlsPolicy || undefined,
        tlsIssuerKind: options.includeTlsPolicy ? options.tlsIssuerKind : undefined,
        tlsIssuerName: options.includeTlsPolicy ? options.tlsIssuerName : undefined,
        includeDnsPolicy: options.includeDnsPolicy || undefined,
        dnsHostname: options.includeDnsPolicy ? options.dnsHostname.trim() || undefined : undefined,
        dnsProviderSecretName:
          options.includeDnsPolicy && options.dnsProviderSecretName.trim()
            ? options.dnsProviderSecretName.trim()
            : undefined,
      });
      setProgress(100);
      setAppState(prev => ({ ...prev, conversionResults: resp.data.results }));
    } catch (e: unknown) {
      setError(t('conversion.errorConvert', { message: apiErrorMessage(e, 'Conversion failed') }));
    } finally {
      setLoading(false);
    }
  };

  if (appState.selectedServices.length === 0) {
    return (
      <PageSection>
        <Alert variant="warning" title={t('conversion.warningTitle')}>
          <Button variant="link" onClick={() => navigate('/services')}>
            {t('conversion.goToApiList')}
          </Button>
        </Alert>
      </PageSection>
    );
  }

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('conversion.title')}</Title>
        <p className={styles.pageDescription}>
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
            <ConversionForm
              loading={loading}
              error={error}
              progress={progress}
              onConvert={handleConvert}
            />
          </StackItem>
          {appState.conversionResults.length > 0 && (
            <StackItem>
              <ConversionResults
                results={appState.conversionResults}
                onNavigateYaml={() => navigate('/yaml')}
              />
            </StackItem>
          )}
        </Stack>
      </PageSection>
    </>
  );
};

export default ConversionPage;
