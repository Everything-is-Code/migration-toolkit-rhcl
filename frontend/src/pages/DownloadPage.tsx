import React, { useState } from 'react';
import { apiErrorI18nMessage, apiErrorI18nMessageAsync } from '../utils/apiError';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  Button,
  Alert,
  DataList,
  DataListItem,
  DataListItemRow,
  DataListItemCells,
  DataListCell,
  Label,
  Stack,
  StackItem,
  Tooltip,
} from '@patternfly/react-core';
import { DownloadIcon, PlayIcon } from '@patternfly/react-icons';
import { useTranslation, Trans } from 'react-i18next';
import { applyApi, downloadApi } from '../api/client';
import { useAppState } from '../components/AppStateContext';
import { useClearStaleConversionResults } from '../components/conversion/useClearStaleConversionResults';
import { useClearStaleValidationSnapshot } from '../components/conversion/useClearStaleValidationSnapshot';
import ApplyConfirmModal from '../components/download/ApplyConfirmModal';
import ImportResultTable from '../components/import/ImportResultTable';
import type { ApplyResult } from '../components/import/importUtils';
import type { ConversionResultItem } from '../api/types';
import { getApplyGuardrails } from '../utils/applyGuardrails';
import { buildApplyPayload } from '../utils/clusterApply';
import { getClusterConnectionUiState } from '../utils/clusterCapabilityUi';
import { useNavigate } from 'react-router-dom';
import styles from '../styles/shared.module.css';

const DownloadPage: React.FC = () => {
  const { appState } = useAppState();
  useClearStaleConversionResults();
  useClearStaleValidationSnapshot();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [downloading, setDownloading] = useState<Record<string, boolean>>({});
  const [applying, setApplying] = useState<Record<string, boolean>>({});
  const [applyResults, setApplyResults] = useState<Record<string, ApplyResult[]>>({});
  const [confirmTarget, setConfirmTarget] = useState<ConversionResultItem | null>(null);
  const [applyConfirmNamespace, setApplyConfirmNamespace] = useState('');
  const [appliedNamespaces, setAppliedNamespaces] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  const results = appState.conversionResults.filter(r => r.yamlFiles && Object.keys(r.yamlFiles).length > 0);
  const clusterState = getClusterConnectionUiState(appState.clusterVersions);
  const clusterUnreachable = clusterState === 'unreachable';

  const handleDownload = async (result: typeof results[0]) => {
    setDownloading(prev => ({ ...prev, [result.serviceId]: true }));
    setError(null);
    try {
      const resp = await downloadApi.downloadZip(result.packageName, result.yamlFiles);
      const url = window.URL.createObjectURL(new Blob([resp.data]));
      const link = document.createElement('a');
      link.href = url;
      link.download = `${result.packageName}.zip`;
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (e: unknown) {
      const message = await apiErrorI18nMessageAsync(e, t, t('download.errorDownloadFallback'));
      setError(t('download.errorDownload', { message }));
    } finally {
      setDownloading(prev => ({ ...prev, [result.serviceId]: false }));
    }
  };

  const handleDownloadAll = async () => {
    for (const result of results) {
      await handleDownload(result);
    }
  };

  const handleApplyConfirm = async () => {
    if (!confirmTarget?.yamlFiles) {
      return;
    }
    const guardrails = getApplyGuardrails(confirmTarget, appState);
    if (!guardrails.enabled) {
      setError(t(guardrails.reasonKey!));
      setConfirmTarget(null);
      setApplyConfirmNamespace('');
      return;
    }
    const namespace = applyConfirmNamespace.trim();
    if (!namespace) {
      return;
    }
    const { serviceId, packageName, yamlFiles } = confirmTarget;
    setApplying(prev => ({ ...prev, [serviceId]: true }));
    setError(null);
    try {
      const payload = buildApplyPayload(yamlFiles, namespace);
      const res = await applyApi.apply(namespace, payload, 'CONVERT', packageName || undefined);
      const applyRes: ApplyResult[] = res.data?.results ?? [];
      setApplyResults(prev => ({ ...prev, [serviceId]: applyRes }));
      setAppliedNamespaces(prev => ({ ...prev, [serviceId]: namespace }));
    } catch (e: unknown) {
      const data = (e && typeof e === 'object' && 'response' in e)
        ? (e as { response?: { data?: { results?: ApplyResult[] } } }).response?.data
        : undefined;
      if (Array.isArray(data?.results)) {
        setApplyResults(prev => ({ ...prev, [serviceId]: data.results! }));
      } else {
        const message = apiErrorI18nMessage(e, t, t('download.errorApplyFallback'));
        setError(t('download.errorApply', { message }));
      }
    } finally {
      setApplying(prev => ({ ...prev, [serviceId]: false }));
      setConfirmTarget(null);
      setApplyConfirmNamespace('');
    }
  };

  const openApplyConfirm = (result: ConversionResultItem) => {
    setConfirmTarget(result);
    setApplyConfirmNamespace(appState.namespace);
  };

  const renderApplyButton = (result: ConversionResultItem) => {
    const guardrails = getApplyGuardrails(result, appState);
    const isApplying = applying[result.serviceId];
    const button = (
      <Button
        variant="secondary"
        onClick={() => openApplyConfirm(result)}
        isDisabled={!guardrails.enabled || isApplying}
        icon={<PlayIcon />}
      >
        {isApplying ? t('download.btnApplying') : t('download.btnApplyCluster')}
      </Button>
    );

    if (guardrails.enabled) {
      return button;
    }

    return (
      <Tooltip content={t(guardrails.reasonKey!)}>
        <span style={{ display: 'inline-block' }}>{button}</span>
      </Tooltip>
    );
  };

  if (results.length === 0) {
    return (
      <PageSection>
        <Alert variant="warning" title={t('download.warningTitle')}>
          <Button variant="link" onClick={() => navigate('/convert')}>{t('download.goToConvert')}</Button>
        </Alert>
      </PageSection>
    );
  }

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('download.title')}</Title>
        <p className={styles.pageDescription}>
          {t('download.description')}
        </p>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          {clusterUnreachable && (
            <StackItem>
              <Alert variant="warning" title={t('download.applyDisabledClusterTitle')} isInline>
                {t('download.applyDisabledCluster')}
              </Alert>
            </StackItem>
          )}

          {error && (
            <StackItem>
              <Alert variant="danger" title={error} />
            </StackItem>
          )}

          <StackItem>
            <Alert variant="info" isInline title={t('download.namespaceLabel', { namespace: appState.namespace })}>
              {t('download.namespaceHint')}
            </Alert>
          </StackItem>

          <StackItem>
            <Card>
              <CardBody>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                  <Title headingLevel="h3" size="lg">{t('download.packageTitle')}</Title>
                  <Button variant="secondary" onClick={handleDownloadAll}>
                    <DownloadIcon /> {t('download.btnDownloadAll')}
                  </Button>
                </div>

                <DataList aria-label={t('download.ariaList')}>
                  {results.map(result => (
                    <DataListItem key={result.serviceId}>
                      <DataListItemRow>
                        <DataListItemCells
                          dataListCells={[
                            <DataListCell key="name" width={2}>
                              <div>
                                <strong>{result.serviceName}</strong>
                                <br />
                                <code className={styles.mutedText} style={{ fontSize: '0.85rem' }}>
                                  {result.packageName}.zip
                                </code>
                              </div>
                            </DataListCell>,
                            <DataListCell key="files">
                              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                                {Object.keys(result.yamlFiles).map(f => (
                                  <Label key={f} isCompact>{f}</Label>
                                ))}
                              </div>
                            </DataListCell>,
                            <DataListCell key="score">
                              Score: <strong>{result.compatibilityScore}%</strong>
                            </DataListCell>,
                            <DataListCell key="action">
                              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                                <Button
                                  variant="primary"
                                  onClick={() => handleDownload(result)}
                                  isDisabled={downloading[result.serviceId]}
                                  icon={<DownloadIcon />}
                                >
                                  {downloading[result.serviceId] ? t('download.btnDownloading') : t('download.btnDownloadZip')}
                                </Button>
                                {renderApplyButton(result)}
                              </div>
                            </DataListCell>,
                          ]}
                        />
                      </DataListItemRow>
                    </DataListItem>
                  ))}
                </DataList>
              </CardBody>
            </Card>
          </StackItem>

          {Object.entries(applyResults).map(([serviceId, rows]) => (
            rows.length > 0 ? (
              <StackItem key={serviceId}>
                <ImportResultTable results={rows} namespace={appliedNamespaces[serviceId] ?? appState.namespace} />
              </StackItem>
            ) : null
          ))}

          <StackItem>
            <Card>
              <CardBody>
                <Title headingLevel="h3" size="lg">{t('download.nextStepsTitle')}</Title>
                <ol style={{ marginTop: '12px', paddingLeft: '20px', lineHeight: '2' }}>
                  <li><Trans i18nKey="download.step1" components={{ strong: <strong /> }} /></li>
                  <li><Trans i18nKey="download.step2" components={{ code: <code /> }} /></li>
                  <li>{t('download.step3')}</li>
                  <li><Trans i18nKey="download.step4" components={{ code: <code /> }} /></li>
                  <li>{t('download.step5')}</li>
                </ol>
              </CardBody>
            </Card>
          </StackItem>

          <StackItem>
            <Button variant="secondary" onClick={() => navigate('/validate')}>{t('download.btnBack')}</Button>
          </StackItem>
        </Stack>
      </PageSection>

      <ApplyConfirmModal
        isOpen={confirmTarget !== null}
        namespace={applyConfirmNamespace}
        packageName={confirmTarget?.packageName ?? ''}
        fileCount={confirmTarget?.yamlFiles ? Object.keys(confirmTarget.yamlFiles).length : 0}
        applying={confirmTarget ? Boolean(applying[confirmTarget.serviceId]) : false}
        onNamespaceChange={setApplyConfirmNamespace}
        onClose={() => {
          setConfirmTarget(null);
          setApplyConfirmNamespace('');
        }}
        onConfirm={handleApplyConfirm}
      />
    </>
  );
};

export default DownloadPage;
