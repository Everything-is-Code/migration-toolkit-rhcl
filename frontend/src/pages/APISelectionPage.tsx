import React, { useState, useEffect } from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  DataList,
  DataListItem,
  DataListItemRow,
  DataListItemCells,
  DataListCell,
  Button,
  Alert,
  Spinner,
  Badge,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
  SearchInput,
  EmptyState,
  EmptyStateIcon,
  EmptyStateBody,
  Label,
} from '@patternfly/react-core';
import { CubesIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { servicesApi } from '../api/client';
import { ApiService, Policy } from '../api/types';
import { AppState } from '../App';
import { useNavigate } from 'react-router-dom';
import shared from '../styles/shared.module.css';
import styles from './APISelectionPage.module.css';

interface Props {
  appState: AppState;
  setAppState: React.Dispatch<React.SetStateAction<AppState>>;
}

const renderConfigValue = (v: unknown): string => {
  if (v === null || v === undefined) return '';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
};

const PolicyPanel: React.FC<{ policies: Policy[] }> = ({ policies }) => {
  const { t } = useTranslation();
  if (policies.length === 0) return null;
  return (
    <div className={styles.policyPanel}>
      <div className={styles.policyPanelTitle}>{t('apiSelection.policyDefinitions', 'Policy Definitions')}</div>
      {policies.map((p, i) => (
        <div key={i} style={{ marginBottom: i < policies.length - 1 ? 8 : 0, opacity: p.enabled ? 1 : 0.45 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 3 }}>
            <code className={styles.policyCode}>
              {p.name}
            </code>
            {p.version && <span className={styles.policyMeta}>{p.version}</span>}
            {!p.enabled && <span className={styles.policyMeta}>(disabled)</span>}
          </div>
          {p.configuration && Object.keys(p.configuration).length > 0 && (
            <div style={{ paddingLeft: 12 }}>
              {Object.entries(p.configuration).map(([k, v]) => (
                <div key={k} className={styles.policyConfigLine}>
                  <span className={shared.mutedText}>{k}:</span>{' '}
                  <span style={{ fontFamily: 'monospace' }}>{renderConfigValue(v)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  );
};

const APISelectionPage: React.FC<Props> = ({ appState, setAppState }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [services, setServices] = useState<ApiService[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(
    appState.selectedServices.length > 0 ? appState.selectedServices[0].id : null
  );
  const [search, setSearch] = useState('');

  useEffect(() => {
    if (appState.connection.connected) {
      loadServices();
    }
  }, []);

  const loadServices = async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await servicesApi.list(appState.connection.url, appState.connection.accessToken);
      setServices(resp.data);
    } catch (e: any) {
      setError(t('apiSelection.errorFetch', { message: e.response?.data || e.message }));
    } finally {
      setLoading(false);
    }
  };

  const handleNext = () => {
    const selected = services.filter(s => s.id === selectedId);
    setAppState(prev => ({ ...prev, selectedServices: selected }));
    navigate('/compatibility');
  };

  const filtered = services.filter(s =>
    s.name.toLowerCase().includes(search.toLowerCase()) ||
    (s.systemName || '').toLowerCase().includes(search.toLowerCase())
  );

  if (!appState.connection.connected) {
    return (
      <PageSection>
        <Alert variant="warning" title={t('apiSelection.warningTitle')}>
          <Button variant="link" onClick={() => navigate('/')}>{t('apiSelection.goToConnection')}</Button>
        </Alert>
      </PageSection>
    );
  }

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('apiSelection.title')}</Title>
        <p className={shared.pageDescription}>
          {t('apiSelection.description')}
        </p>
      </PageSection>
      <PageSection>
        <Card>
          <CardBody>
            {error && <Alert variant="danger" title={error} style={{ marginBottom: '16px' }} />}
            <Toolbar>
              <ToolbarContent>
                <ToolbarItem>
                  <SearchInput
                    placeholder={t('apiSelection.searchPlaceholder')}
                    value={search}
                    onChange={(_e, val) => setSearch(val)}
                    onClear={() => setSearch('')}
                  />
                </ToolbarItem>
                <ToolbarItem>
                  <Button variant="secondary" onClick={loadServices} isDisabled={loading}>
                    {loading ? <Spinner size="sm" /> : t('apiSelection.btnRefresh')}
                  </Button>
                </ToolbarItem>
                <ToolbarItem align={{ default: 'alignRight' }}>
                  {selectedId && (
                    <Badge isRead={false}>
                      {services.find(s => s.id === selectedId)?.name ?? selectedId}
                    </Badge>
                  )}
                </ToolbarItem>
              </ToolbarContent>
            </Toolbar>

            {loading ? (
              <div className={shared.centeredBlock}>
                <Spinner size="xl" />
                <p>{t('apiSelection.loading')}</p>
              </div>
            ) : filtered.length === 0 ? (
              <EmptyState>
                <EmptyStateIcon icon={CubesIcon} />
                <Title headingLevel="h4" size="lg">{t('apiSelection.emptyTitle')}</Title>
                <EmptyStateBody>{t('apiSelection.emptyBody')}</EmptyStateBody>
              </EmptyState>
            ) : (
              <DataList
                aria-label={t('apiSelection.ariaLabel')}
                style={{ marginTop: '16px' }}
                selectedDataListItemId={selectedId ?? undefined}
                onSelectDataListItem={(_e, id) => {
                  if (id === 'api-list-header') return;
                  setSelectedId(id === selectedId ? null : id);
                }}
              >
                <DataListItem
                  id="api-list-header"
                  aria-labelledby="api-list-header-label"
                  className={styles.listHeader}
                >
                  <DataListItemRow>
                    <DataListItemCells
                      dataListCells={[
                        <DataListCell key="h-name" width={2}>
                          <span id="api-list-header-label" className={styles.colHeader}>
                            {t('apiSelection.colName')}
                          </span>
                        </DataListCell>,
                        <DataListCell key="h-state">
                          <span className={styles.colHeader}>
                            {t('apiSelection.colState')}
                          </span>
                        </DataListCell>,
                        <DataListCell key="h-auth">
                          <span className={styles.colHeader}>
                            {t('apiSelection.colAuth')}
                          </span>
                        </DataListCell>,
                        <DataListCell key="h-policy">
                          <span className={styles.colHeader}>
                            {t('apiSelection.colPolicies')}
                          </span>
                        </DataListCell>,
                        <DataListCell key="h-backends">
                          <span className={styles.colHeader}>
                            {t('apiSelection.colBackends')}
                          </span>
                        </DataListCell>,
                      ]}
                    />
                  </DataListItemRow>
                </DataListItem>
                {filtered.map(service => {
                  const isSelected = selectedId === service.id;
                  const allPolicies = service.policies ?? [];
                  const enabledPolicies = allPolicies.filter(p => p.enabled);
                  return (
                    <DataListItem
                      key={service.id}
                      id={service.id}
                      aria-labelledby={`service-${service.id}`}
                      className={`${styles.serviceRow}${isSelected ? ` ${styles.isSelected}` : ''}`}
                    >
                      <DataListItemRow>
                        <DataListItemCells
                          dataListCells={[
                            <DataListCell key="name" width={2}>
                              <span id={`service-${service.id}`} style={{ fontWeight: 'bold' }}>
                                {service.name}
                              </span>
                              <br />
                              <small className={shared.mutedText}>{service.systemName}</small>
                              {isSelected && allPolicies.length > 0 && (
                                <PolicyPanel policies={allPolicies} />
                              )}
                            </DataListCell>,
                            <DataListCell key="state">
                              <Badge
                                isRead={service.state !== 'published'}
                                className={service.state === 'published' ? styles.publishedBadge : undefined}
                              >
                                {service.state || 'unknown'}
                              </Badge>
                            </DataListCell>,
                            <DataListCell key="auth">
                              {service.authentication?.type || 'N/A'}
                            </DataListCell>,
                            <DataListCell key="policy">
                              {enabledPolicies.length > 0 && (
                                <Label isCompact color="blue">
                                  {t('apiSelection.policyCount', { count: enabledPolicies.length })}
                                </Label>
                              )}
                            </DataListCell>,
                            <DataListCell key="backends">
                              {service.backends && service.backends.length > 0
                                ? service.backends.map(b => b.name || b.systemName || b.id).join(', ')
                                : t('apiSelection.backendCount', { count: 0 })}
                            </DataListCell>,
                          ]}
                        />
                      </DataListItemRow>
                    </DataListItem>
                  );
                })}
              </DataList>
            )}

            <div className={shared.actionRow} style={{ marginTop: '24px' }}>
              <Button variant="secondary" onClick={() => navigate('/')}>{t('apiSelection.btnBack')}</Button>
              <Button
                variant="primary"
                onClick={handleNext}
                isDisabled={selectedId === null}
              >
                {t('apiSelection.btnNext')}
              </Button>
            </div>
          </CardBody>
        </Card>
      </PageSection>
    </>
  );
};

export default APISelectionPage;
