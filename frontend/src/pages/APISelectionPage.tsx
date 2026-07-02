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
  if (policies.length === 0) return null;
  return (
    <div style={{ marginTop: 10, padding: '10px 14px', background: '#f9f9f9', borderRadius: 4, border: '1px solid #e8e8e8' }}>
      <div style={{ fontSize: 12, fontWeight: 600, color: '#6a6e73', marginBottom: 6 }}>ポリシー定義</div>
      {policies.map((p, i) => (
        <div key={i} style={{ marginBottom: i < policies.length - 1 ? 8 : 0, opacity: p.enabled ? 1 : 0.45 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 3 }}>
            <code style={{ fontSize: 12, background: '#fff', border: '1px solid #d2d2d2', borderRadius: 3, padding: '1px 5px' }}>
              {p.name}
            </code>
            {p.version && <span style={{ fontSize: 11, color: '#8a8d90' }}>{p.version}</span>}
            {!p.enabled && <span style={{ fontSize: 11, color: '#8a8d90' }}>(無効)</span>}
          </div>
          {p.configuration && Object.keys(p.configuration).length > 0 && (
            <div style={{ paddingLeft: 12 }}>
              {Object.entries(p.configuration).map(([k, v]) => (
                <div key={k} style={{ fontSize: 12, color: '#3c3f42', lineHeight: 1.6 }}>
                  <span style={{ color: '#6a6e73' }}>{k}:</span>{' '}
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
        <p style={{ marginTop: '8px', color: '#6a6e73' }}>
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
              <div style={{ textAlign: 'center', padding: '40px' }}>
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
                onSelectDataListItem={(_e, id) => setSelectedId(id === selectedId ? null : id)}
              >
                {filtered.map(service => {
                  const isSelected = selectedId === service.id;
                  const allPolicies = service.policies ?? [];
                  const enabledPolicies = allPolicies.filter(p => p.enabled);
                  return (
                    <DataListItem
                      key={service.id}
                      id={service.id}
                      aria-labelledby={`service-${service.id}`}
                      style={{
                        cursor: 'pointer',
                        background: isSelected ? '#e7f3ff' : undefined,
                        borderLeft: isSelected ? '3px solid #0066cc' : '3px solid transparent',
                      }}
                    >
                      <DataListItemRow>
                        {/* ラジオボタン */}
                        <div style={{ display: 'flex', alignItems: 'flex-start', padding: '12px 12px 12px 16px' }}>
                          <input
                            type="radio"
                            name="service-select"
                            id={`radio-${service.id}`}
                            checked={isSelected}
                            onChange={() => setSelectedId(service.id)}
                            style={{ marginTop: 3, accentColor: '#0066cc', width: 16, height: 16 }}
                          />
                        </div>
                        <DataListItemCells
                          dataListCells={[
                            <DataListCell key="name" width={2}>
                              <label htmlFor={`radio-${service.id}`} style={{ cursor: 'pointer' }}>
                                <span id={`service-${service.id}`} style={{ fontWeight: 'bold' }}>
                                  {service.name}
                                </span>
                                <br />
                                <small style={{ color: '#6a6e73' }}>{service.systemName}</small>
                              </label>
                              {isSelected && allPolicies.length > 0 && (
                                <PolicyPanel policies={allPolicies} />
                              )}
                            </DataListCell>,
                            <DataListCell key="state">
                              <Badge
                                isRead={service.state !== 'published'}
                                style={{ backgroundColor: service.state === 'published' ? '#3e8635' : undefined }}
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
                              {t('apiSelection.backendCount', { count: service.backends?.length || 0 })}
                            </DataListCell>,
                          ]}
                        />
                      </DataListItemRow>
                    </DataListItem>
                  );
                })}
              </DataList>
            )}

            <div style={{ marginTop: '24px', display: 'flex', gap: '8px' }}>
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
