import React, { useState, useEffect } from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  CardTitle,
  Grid,
  GridItem,
  Progress,
  ProgressVariant,
  Label,
  Button,
  Alert,
  Spinner,
  Stack,
  StackItem,
} from '@patternfly/react-core';
import { CheckCircleIcon, ExclamationTriangleIcon, TimesCircleIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { servicesApi } from '../api/client';
import { CompatibilityResult } from '../api/types';
import { useAppState } from '../components/AppStateContext';
import { useNavigate } from 'react-router-dom';
import { loadSupportedPolicies } from './supportedPolicies';
import { runCompatibilityChecks } from './compatibilityChecks';
import {
  PF_DANGER,
  PF_SPACER_MD,
  PF_SPACER_SM,
  PF_SUCCESS,
  PF_WARNING,
} from '../styles/pfTokens';
import shared from '../styles/shared.module.css';
import styles from './CompatibilityPage.module.css';

const StatusIcon: React.FC<{ status: string }> = ({ status }) => {
  switch (status) {
    case 'SUPPORTED': return <CheckCircleIcon color={PF_SUCCESS} />;
    case 'WARNING': return <ExclamationTriangleIcon color={PF_WARNING} />;
    case 'UNSUPPORTED': return <TimesCircleIcon color={PF_DANGER} />;
    default: return null;
  }
};

const statusClass = (status: string): string => {
  if (status === 'SUPPORTED') return shared.statusOk;
  if (status === 'WARNING') return shared.statusWarning;
  return shared.statusError;
};

const ScoreColor = (score: number): ProgressVariant => {
  if (score >= 80) return ProgressVariant.success;
  if (score >= 50) return ProgressVariant.warning;
  return ProgressVariant.danger;
};

const CompatibilityPage: React.FC = () => {
  const { appState } = useAppState();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [results, setResults] = useState<CompatibilityResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (appState.selectedServices.length > 0) {
      checkAll();
    }
  }, []);

  const checkAll = async () => {
    setLoading(true);
    setError(null);
    const { results: nextResults, error: nextError } = await runCompatibilityChecks(
      appState.selectedServices,
      appState.connection,
      {
        loadSupportedPolicies,
        checkCompatibility: servicesApi.checkCompatibility,
        errorMessage: t('compatibility.errorCheck'),
      },
    );
    setResults(nextResults);
    setError(nextError);
    setLoading(false);
  };

  const noServices = appState.selectedServices.length === 0;

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: PF_SPACER_MD }}>
          <div>
            <Title headingLevel="h1" size="2xl">{t('compatibility.title')}</Title>
            {!noServices && (
              <p className={shared.pageDescription}>
                {t('compatibility.description', { count: appState.selectedServices.length })}
              </p>
            )}
          </div>
          <div style={{ display: 'flex', gap: PF_SPACER_SM, alignItems: 'center' }}>
            <Button variant="secondary" onClick={() => navigate('/services')}>{t('compatibility.btnCancel')}</Button>
            {!noServices && (
              <Button variant="secondary" onClick={checkAll} isDisabled={loading}>{t('compatibility.btnRecheck')}</Button>
            )}
            <Button
              variant="primary"
              onClick={() => navigate('/convert')}
              isDisabled={noServices || results.length === 0}
            >
              {t('compatibility.btnGenerateYaml')}
            </Button>
          </div>
        </div>
      </PageSection>
      <PageSection>
        {noServices ? (
          <Alert variant="warning" title={t('compatibility.warningTitle')} />
        ) : loading ? (
          <div className={shared.centeredBlock} style={{ padding: '60px' }}>
            <Spinner size="xl" />
            <p style={{ marginTop: PF_SPACER_MD }}>{t('compatibility.loading')}</p>
          </div>
        ) : error ? (
          <Alert variant="danger" title={error} />
        ) : (
          <Stack hasGutter>
            {results.map(result => (
              <StackItem key={result.serviceId}>
                <Card>
                  <CardTitle>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <Title headingLevel="h3" size="lg">{result.serviceName}</Title>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <Label
                          color={result.level === 'HIGH' ? 'green' : result.level === 'MEDIUM' ? 'orange' : 'red'}
                        >
                          {result.level}
                        </Label>
                        <span className={styles.scoreValue}>
                          Migration Score: {result.score}%
                        </span>
                      </div>
                    </div>
                    <Progress
                      value={result.score}
                      variant={ScoreColor(result.score)}
                      style={{ marginTop: PF_SPACER_SM }}
                    />
                  </CardTitle>
                  <CardBody>
                    <Grid hasGutter sm={12} md={6} lg={4}>
                      {result.items.map((item, i) => (
                        <GridItem key={i}>
                          <div className={`${styles.itemCard} ${statusClass(item.status)}`}>
                            <StatusIcon status={item.status} />
                            <div>
                              <div className={styles.itemName}>{item.name}</div>
                              <div className={styles.itemMessage}>{item.message}</div>
                              {(item.requiredVersion || item.capability) && (
                                <div className={styles.itemMeta}>
                                  {item.capability && (
                                    <span style={{ marginRight: PF_SPACER_SM }}>
                                      {t('compatibility.capability', { capability: item.capability })}
                                    </span>
                                  )}
                                  {item.requiredVersion && (
                                    <span>
                                      {t('compatibility.requiredVersion', { version: item.requiredVersion })}
                                    </span>
                                  )}
                                </div>
                              )}
                            </div>
                          </div>
                        </GridItem>
                      ))}
                    </Grid>
                  </CardBody>
                </Card>
              </StackItem>
            ))}
          </Stack>
        )}
      </PageSection>
    </>
  );
};

export default CompatibilityPage;
