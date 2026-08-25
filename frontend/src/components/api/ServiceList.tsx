import React from 'react';
import {
  DataList,
  DataListItem,
  DataListItemRow,
  DataListItemCells,
  DataListCell,
  Badge,
  Spinner,
  EmptyState,
  EmptyStateIcon,
  EmptyStateBody,
  Label,
  Title,
} from '@patternfly/react-core';
import { CubesIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { ApiService, Policy } from '../../api/types';
import PolicyPanel from './PolicyPanel';
import shared from '../../styles/shared.module.css';
import styles from '../../pages/APISelectionPage.module.css';

interface Props {
  loading: boolean;
  filtered: ApiService[];
  services: ApiService[];
  selectedId: string | null;
  enabledPoliciesById: Map<string, Policy[]>;
  onSelect: (id: string) => void;
  search: string;
}

const ServiceList: React.FC<Props> = ({
  loading,
  filtered,
  services: _services,
  selectedId,
  enabledPoliciesById,
  onSelect,
  search,
}) => {
  const { t } = useTranslation();

  return (
    <>
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
          <Title headingLevel="h4" size="lg">
            {t('apiSelection.emptyTitle')}
          </Title>
          <EmptyStateBody>{t('apiSelection.emptyBody')}</EmptyStateBody>
        </EmptyState>
      ) : (
        <DataList
          aria-label={t('apiSelection.ariaLabel')}
          style={{ marginTop: '16px' }}
          selectedDataListItemId={selectedId ?? undefined}
          onSelectDataListItem={(_e, id) => {
            if (id !== 'api-list-header') onSelect(id);
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
                    <span className={styles.colHeader}>{t('apiSelection.colState')}</span>
                  </DataListCell>,
                  <DataListCell key="h-auth">
                    <span className={styles.colHeader}>{t('apiSelection.colAuth')}</span>
                  </DataListCell>,
                  <DataListCell key="h-policy">
                    <span className={styles.colHeader}>{t('apiSelection.colPolicies')}</span>
                  </DataListCell>,
                  <DataListCell key="h-backends">
                    <span className={styles.colHeader}>{t('apiSelection.colBackends')}</span>
                  </DataListCell>,
                ]}
              />
            </DataListItemRow>
          </DataListItem>
          {filtered.map(service => {
            const isSelected = selectedId === service.id;
            const allPolicies = service.policies ?? [];
            const enabledPolicies = enabledPoliciesById.get(service.id) ?? [];
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
    </>
  );
};

export default ServiceList;
