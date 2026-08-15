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
import { AppState } from '../App';
import { useNavigate } from 'react-router-dom';
import { loadSupportedPolicies } from './SupportedPoliciesPage';
import {
  PF_BG_DANGER,
  PF_BG_DEFAULT,
  PF_BG_WARNING,
  PF_BORDER_RADIUS,
  PF_COLOR_MUTED,
  PF_COLOR_SUBTLE,
  PF_DANGER,
  PF_FONT_SIZE_2XL,
  PF_FONT_SIZE_SM,
  PF_FONT_SIZE_XS,
  PF_SPACER_MD,
  PF_SPACER_SM,
  PF_SPACER_XS,
  PF_SUCCESS,
  PF_WARNING,
} from '../styles/pfTokens';

interface Props {
  appState: AppState;
  setAppState: React.Dispatch<React.SetStateAction<AppState>>;
}

const StatusIcon: React.FC<{ status: string }> = ({ status }) => {
  switch (status) {
    case 'SUPPORTED': return <CheckCircleIcon color={PF_SUCCESS} />;
    case 'WARNING': return <ExclamationTriangleIcon color={PF_WARNING} />;
    case 'UNSUPPORTED': return <TimesCircleIcon color={PF_DANGER} />;
    default: return null;
  }
};

const statusBackground = (status: string): string => {
  if (status === 'SUPPORTED') return PF_BG_DEFAULT;
  if (status === 'WARNING') return PF_BG_WARNING;
  return PF_BG_DANGER;
};

const ScoreColor = (score: number): ProgressVariant => {
  if (score >= 80) return ProgressVariant.success;
  if (score >= 50) return ProgressVariant.warning;
  return ProgressVariant.danger;
};

const CompatibilityPage: React.FC<Props> = ({ appState, setAppState: _setAppState }) => {
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
    const all: CompatibilityResult[] = [];
    for (const service of appState.selectedServices) {
      try {
        const policies = await loadSupportedPolicies();
        const resp = await servicesApi.checkCompatibility(
          service.id, appState.connection.url, appState.connection.accessToken,
          policies
        );
        all.push(resp.data);
      } catch {
        all.push({
          serviceId: service.id,
          serviceName: service.name,
          score: 0,
          level: 'LOW',
          items: [{ name: 'Error', status: 'UNSUPPORTED', message: t('compatibility.errorCheck') }],
        });
      }
    }
    setResults(all);
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
              <p style={{ marginTop: PF_SPACER_SM, color: PF_COLOR_MUTED }}>
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
          <div style={{ textAlign: 'center', padding: '60px' }}>
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
                        <span style={{ fontSize: PF_FONT_SIZE_2XL, fontWeight: 'bold' }}>
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
                          <div style={{
                            display: 'flex',
                            alignItems: 'flex-start',
                            gap: PF_SPACER_SM,
                            padding: PF_SPACER_SM,
                            borderRadius: PF_BORDER_RADIUS,
                            background: statusBackground(item.status),
                          }}>
                            <StatusIcon status={item.status} />
                            <div>
                              <div style={{ fontWeight: 'bold', fontSize: PF_FONT_SIZE_SM }}>{item.name}</div>
                              <div style={{ fontSize: PF_FONT_SIZE_SM, color: PF_COLOR_MUTED }}>{item.message}</div>
                              {(item.requiredVersion || item.capability) && (
                                <div style={{ marginTop: PF_SPACER_XS, fontSize: PF_FONT_SIZE_XS, color: PF_COLOR_SUBTLE }}>
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
