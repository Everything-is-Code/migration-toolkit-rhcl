import React, { useState, useEffect, useMemo } from 'react';
import { apiErrorMessage } from '../utils/apiError';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  Button,
  Alert,
  Pagination,
  PaginationVariant,
} from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import { servicesApi } from '../api/client';
import { ApiService, Policy } from '../api/types';
import { useAppState } from '../components/AppStateContext';
import { useNavigate } from 'react-router-dom';
import shared from '../styles/shared.module.css';
import ServiceToolbar from '../components/api/ServiceToolbar';
import ServiceList from '../components/api/ServiceList';

const DEFAULT_PER_PAGE = 20;

const PER_PAGE_OPTIONS = [
  { title: '10', value: 10 },
  { title: '20', value: 20 },
  { title: '50', value: 50 },
  { title: '100', value: 100 },
];

const APISelectionPage: React.FC = () => {
  const { appState, setAppState } = useAppState();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [services, setServices] = useState<ApiService[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedService, setSelectedService] = useState<ApiService | null>(
    appState.selectedServices.length > 0 ? appState.selectedServices[0] : null,
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
    } catch (e: unknown) {
      setError(t('apiSelection.errorFetch', { message: apiErrorMessage(e, 'Failed to load services') }));
    } finally {
      setLoading(false);
    }
  };

  const handleNext = () => {
    if (!selectedService) return;
    setAppState(prev => ({ ...prev, selectedServices: [selectedService] }));
    navigate('/compatibility');
  };

  const handleSelect = (id: string) => {
    if (id === selectedService?.id) {
      setSelectedService(null);
    } else {
      setSelectedService(services.find(s => s.id === id) ?? null);
    }
  };

  const filtered = useMemo(
    () =>
      services.filter(
        s =>
          s.name.toLowerCase().includes(search.toLowerCase()) ||
          (s.systemName || '').toLowerCase().includes(search.toLowerCase()),
      ),
    [services, search],
  );

  const enabledPoliciesById = useMemo(() => {
    const map = new Map<string, Policy[]>();
    for (const s of services) {
      map.set(s.id, (s.policies ?? []).filter(p => p.enabled));
    }
    return map;
  }, [services]);

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

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('apiSelection.title')}</Title>
        <p className={shared.pageDescription}>{t('apiSelection.description')}</p>
      </PageSection>
      <PageSection>
        <Card>
          <CardBody>
            {error && <Alert variant="danger" title={error} style={{ marginBottom: '16px' }} />}
            <ServiceToolbar
              search={search}
              loading={loading}
              page={page}
              perPage={perPage}
              itemCount={itemCount}
              hasMore={hasMore}
              total={total}
              selectedService={selectedService}
              onSearchChange={setSearch}
              onSearchClear={() => setSearch('')}
              onRefresh={() => loadServices(page, perPage)}
              onSetPage={setPage}
              onPerPageSelect={newPerPage => { setPerPage(newPerPage); setPage(1); }}
            />
            <ServiceList
              loading={loading}
              filtered={filtered}
              services={services}
              selectedId={selectedService?.id ?? null}
              enabledPoliciesById={enabledPoliciesById}
              onSelect={handleSelect}
              search={search}
            />
            <Pagination
              itemCount={itemCount}
              page={page}
              perPage={perPage}
              perPageOptions={PER_PAGE_OPTIONS}
              onSetPage={(_e, newPage) => setPage(newPage)}
              onPerPageSelect={(_e, newPerPage) => { setPerPage(newPerPage); setPage(1); }}
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
              <Button variant="primary" onClick={handleNext} isDisabled={selectedService === null}>
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
