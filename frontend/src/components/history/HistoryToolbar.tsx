import React from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Button,
  Badge,
  Checkbox,
  Pagination,
} from '@patternfly/react-core';
import { HistoryIcon, TrashIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { PF_COLOR_MUTED } from '../../styles/pfTokens';
import styles from './history.module.css';

const PER_PAGE_OPTIONS = [
  { title: '20', value: 20 },
  { title: '50', value: 50 },
  { title: '100', value: 100 },
];

interface HeaderProps {
  selectedCount: number;
  loading: boolean;
  onOpenDeleteModal: () => void;
  onReload: () => void;
}

export const HistoryHeader: React.FC<HeaderProps> = ({
  selectedCount,
  loading,
  onOpenDeleteModal,
  onReload,
}) => {
  const { t } = useTranslation();

  return (
    <PageSection variant={PageSectionVariants.light}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <HistoryIcon style={{ fontSize: '1.8rem', color: PF_COLOR_MUTED }} />
          <div>
            <Title headingLevel="h1" size="2xl">{t('history.titlePage')}</Title>
            <p className={styles.pageSubtitle}>
              {t('history.descriptionPage')}
            </p>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {selectedCount > 0 && (
            <Button
              variant="danger"
              icon={<TrashIcon />}
              onClick={onOpenDeleteModal}
            >
              {t('history.btnDeleteSelected', { count: selectedCount })}
            </Button>
          )}
          <Button variant="secondary" onClick={onReload} isDisabled={loading}>
            {t('history.btnReload')}
          </Button>
        </div>
      </div>
    </PageSection>
  );
};

interface Props {
  allChecked: boolean;
  someChecked: boolean;
  selectedCount: number;
  total: number;
  page: number;
  perPage: number;
  loading: boolean;
  onToggleAll: () => void;
  onSetPage: (page: number) => void;
  onPerPageSelect: (perPage: number) => void;
}

const HistoryToolbar: React.FC<Props> = ({
  allChecked,
  someChecked,
  selectedCount,
  total,
  page,
  perPage,
  loading,
  onToggleAll,
  onSetPage,
  onPerPageSelect,
}) => {
  const { t } = useTranslation();

  return (
    <>
      <div className={styles.toolbar}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Checkbox
            id="select-all"
            isChecked={allChecked}
            onChange={onToggleAll}
            aria-label={t('history.ariaSelectAll')}
            style={{ marginRight: 4 }}
          />
          <span className={styles.toolbarHint}>
            {someChecked || allChecked
              ? t('history.selectedCount2', { count: selectedCount })
              : t('history.selectAll')}
          </span>
        </div>
        <Badge isRead>{t('history.countBadge', { count: total })}</Badge>
      </div>

      <Pagination
        itemCount={total}
        page={page}
        perPage={perPage}
        perPageOptions={PER_PAGE_OPTIONS}
        onSetPage={(_e, newPage) => onSetPage(newPage)}
        onPerPageSelect={(_e, newPerPage) => onPerPageSelect(newPerPage)}
        isCompact
        isDisabled={loading}
        style={{ padding: '8px 16px' }}
      />
    </>
  );
};

export default HistoryToolbar;
