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
  Pagination,
  PaginationVariant,
} from '@patternfly/react-core';
import { CubesIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { servicesApi } from '../api/client';
import { ApiService, Policy } from '../api/types';
import { AppState } from '../App';
import { useNavigate } from 'react-router-dom';
import shared from '../styles/shared.module.css';
import styles from './APISelectionPage.module.css';

const DEFAULT_PER_PAGE = 20;

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
  const [selectedService, setSelectedService] = useState<ApiService | null>(
    appState.selectedServices.length > 0 ? appState.selectedServices[0] : null
  );
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [perPage, setPerPage] = useState(DEFAULT_PER_PAGE);
  const [hasMore, setHasMore] = useState(false);
  const [total, setTotal] = useState<number | null>(null);

  useEffect(() => {
    if (appState.connection.connected) {
      loadServices(page, perPage);
    }
  }, [page, perPage]);

  const loadServices = async (pageNum: number, pageSize: number) => {
    setLoading(true);
    setError(null);
    try {
      const resp = await servicesApi.list(
        appState.connection.url,
        appState.connection.accessToken,
        pageNum,
        pageSize,
      );
      const data = resp.data;
      setServices(data.items ?? []);
      setHasMore(Boolean(data.hasMore));
      setTotal(typeof data.total === 'number' ? data.total : null);
      setPage(data.page ?? pageNum);
      setPerPage(data.perPage ?? pageSize);
    } catch (e: any) {
      setError(t('apiSelection.errorFetch', { message: e.response?.data || e.message }));
    } finally {
      setLoading(false);
    }
  };

  const handleNext = () => {
    if (!selectedService) return;
    setAppState(prev => ({ ...prev, selectedServices: [selectedService] }));
    navigate('/compatibility');
  };

  const filtered = services.filter(s =>
    s.name.toLowerCase().includes(search.toLowerCase()) ||
    (s.systemName || '').toLowerCase().includes(search.toLowerCase())
  );

  // Keep next enabled when total is unknown but hasMore.
  const itemCount = total ?? ((page - 1) * perPage + services.length + (hasMore ? 1 : 0));

  if (!appState.connection.connected) {
    return (
      <PageSection>
        <Alert variant="warning" title={t('apiSelection.warningTitle')}>
          <Button variant="link" onClick={() => navigate('/')}>{t('apiSelection.goToConnection')}</Button>
        </Alert>
      </PageSection>
    );
  }

  const selectedId = selectedService?.id ?? null;

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
                  <Button
                    variant="secondary"
                    onClick={() => loadServices(page, perPage)}
                    isDisabled={loading}
                  >
                    {loading ? <Spinner size="sm" /> : t('apiSelection.btnRefresh')}
                  </Button>
                </ToolbarItem>
                <ToolbarItem align={{ default: 'alignRight' }}>
                  {selectedService && (
                    <Badge isRead={false}>
                      {selectedService.name}
                    </Badge>
                  )}
                </ToolbarItem>
                <ToolbarItem align={{ default: 'alignRight' }} variant="pagination">
                  <Pagination
                    itemCount={itemCount}
                    page={page}
                    perPage={perPage}
                    perPageOptions={[
                      { title: '10', value: 10 },
                      { title: '20', value: 20 },
                      { title: '50', value: 50 },
                      { title: '100', value: 100 },
                    ]}
                    onSetPage={(_e, newPage) => setPage(newPage)}
                    onPerPageSelect={(_e, newPerPage) => {
                      setPerPage(newPerPage);
                      setPage(1);
                    }}
                    isCompact
                    isDisabled={loading}
                    toggleTemplate={
                      total == null
                        ? ({ firstIndex, lastIndex }) => (
                            <>
                              {firstIndex} - {lastIndex}
                              {hasMore ? '+' : ''}
                            </>
                          )
                        : undefined
                    }
                  />
                </ToolbarItem>
              </ToolbarContent>
            </Toolbar>
            {search && (
              <p className={shared.mutedText} style={{ marginBottom: '8px' }}>
                {t('apiSelection.searchPageHint')}
              </p>
            )}

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
                  if (id === selectedId) {
                    setSelectedService(null);
                    return;
                  }
                  const found = services.find(s => s.id === id) ?? null;
                  setSelectedService(found);
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
                                title={t('apiSelection.stateTooltip')}
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

            <Pagination
              itemCount={itemCount}
              page={page}
              perPage={perPage}
              perPageOptions={[
                { title: '10', value: 10 },
                { title: '20', value: 20 },
                { title: '50', value: 50 },
                { title: '100', value: 100 },
              ]}
              onSetPage={(_e, newPage) => setPage(newPage)}
              onPerPageSelect={(_e, newPerPage) => {
                setPerPage(newPerPage);
                setPage(1);
              }}
              variant={PaginationVariant.bottom}
              isDisabled={loading}
              style={{ marginTop: '16px' }}
              toggleTemplate={
                total == null
                  ? ({ firstIndex, lastIndex }) => (
                      <>
                        {firstIndex} - {lastIndex}
                        {hasMore ? '+' : ''}
                      </>
                    )
                  : undefined
              }
            />

            <div className={shared.actionRow} style={{ marginTop: '24px' }}>
              <Button variant="secondary" onClick={() => navigate('/')}>{t('apiSelection.btnBack')}</Button>
              <Button
                variant="primary"
                onClick={handleNext}
                isDisabled={selectedService === null}
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
