import React, { useState, useEffect, useCallback } from 'react';
import { apiErrorMessage } from '../utils/apiError';
import {
  PageSection,
  Card,
  CardBody,
  Spinner,
  Alert,
  Button,
  EmptyState,
  EmptyStateIcon,
  EmptyStateBody,
  Title,
} from '@patternfly/react-core';
import { CubesIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { historyApi } from '../api/client';
import { ConversionHistory } from '../api/types';
import shared from '../styles/shared.module.css';
import styles from './HistoryPage.module.css';
import { HistoryHeader } from '../components/history/HistoryToolbar';
import HistoryToolbar from '../components/history/HistoryToolbar';
import HistoryTable from '../components/history/HistoryTable';
import HistoryDeleteModal from '../components/history/HistoryDeleteModal';

const DEFAULT_PAGE_SIZE = 50;

const HistoryPage: React.FC = () => {
  const { t } = useTranslation();
  const [history, setHistory]         = useState<ConversionHistory[]>([]);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState<string | null>(null);
  const [selected, setSelected]       = useState<Set<number>>(new Set());
  const [expanded, setExpanded]       = useState<Set<number>>(new Set());
  const [downloading, setDownloading] = useState<Record<number, boolean>>({});
  const [deleteModal, setDeleteModal] = useState(false);
  const [deleting, setDeleting]       = useState(false);
  const [toast, setToast]             = useState<string | null>(null);
  const [page, setPage]               = useState(1); // PF Pagination is 1-based; API is 0-based.
  const [perPage, setPerPage]         = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal]             = useState(0);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    historyApi.list(page - 1, perPage)
      .then(r => {
        const data = r.data;
        setHistory(Array.isArray(data?.items) ? data.items : []);
        setTotal(typeof data?.total === 'number' ? data.total : 0);
      })
      .catch((e: unknown) => setError(apiErrorMessage(e, 'Failed to load history')))
      .finally(() => setLoading(false));
  }, [page, perPage]);

  useEffect(() => { load(); }, [load]);

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(null), 3000);
  };

  const toggleSelect = (id: number) => {
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(id)) { next.delete(id); } else { next.add(id); }
      return next;
    });
  };

  const toggleAll = () => {
    setSelected(selected.size === history.length ? new Set() : new Set(history.map(h => h.id)));
  };

  const toggleExpand = (id: number) => {
    setExpanded(prev => {
      const next = new Set(prev);
      if (next.has(id)) { next.delete(id); } else { next.add(id); }
      return next;
    });
  };

  const handleDownload = async (entry: ConversionHistory) => {
    setDownloading(prev => ({ ...prev, [entry.id]: true }));
    try {
      const resp = await historyApi.downloadZip(entry.id);
      const blob = new Blob([resp.data], { type: 'application/zip' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      const dateStr = new Date(entry.createdAt).toISOString().slice(0, 16).replace(/[T:]/g, '-');
      link.href = url;
      link.download = `history-${entry.id}-${dateStr}.zip`;
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (e: unknown) {
      showToast(t('history.downloadError2', { message: apiErrorMessage(e, 'Download failed') }));
    } finally {
      setDownloading(prev => ({ ...prev, [entry.id]: false }));
    }
  };

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await historyApi.deleteByIds(Array.from(selected));
      setSelected(new Set());
      setDeleteModal(false);
      showToast(t('history.deleteSuccess', { count: selected.size }));
      load();
    } catch (e: unknown) {
      showToast(t('history.deleteError', { message: apiErrorMessage(e, 'Delete failed') }));
    } finally {
      setDeleting(false);
    }
  };

  const handlePerPageSelect = (newPerPage: number) => {
    setPerPage(newPerPage);
    setPage(1);
  };

  const allChecked = history.length > 0 && selected.size === history.length;
  const someChecked = selected.size > 0 && selected.size < history.length;

  return (
    <>
      {toast && <div className={styles.toast}>{toast}</div>}

      <HistoryHeader
        selectedCount={selected.size}
        loading={loading}
        onOpenDeleteModal={() => setDeleteModal(true)}
        onReload={load}
      />

      <PageSection>
        {error && (
          <Alert variant="danger" title={error} isInline style={{ marginBottom: 16 }}
            actionClose={<Button variant="plain" onClick={() => setError(null)}>×</Button>} />
        )}

        <Card>
          <CardBody style={{ padding: 0 }}>
            {loading ? (
              <div className={shared.centeredBlock} style={{ padding: '60px' }}><Spinner size="xl" /></div>
            ) : history.length === 0 ? (
              <EmptyState style={{ padding: '60px 24px' }}>
                <EmptyStateIcon icon={CubesIcon} />
                <Title headingLevel="h3" size="lg">{t('history.emptyTitle')}</Title>
                <EmptyStateBody>{t('history.emptyBody')}</EmptyStateBody>
              </EmptyState>
            ) : (
              <>
                <HistoryToolbar
                  allChecked={allChecked}
                  someChecked={someChecked}
                  selectedCount={selected.size}
                  total={total}
                  page={page}
                  perPage={perPage}
                  loading={loading}
                  onToggleAll={toggleAll}
                  onSetPage={setPage}
                  onPerPageSelect={handlePerPageSelect}
                />
                <HistoryTable
                  history={history}
                  selected={selected}
                  expanded={expanded}
                  downloading={downloading}
                  total={total}
                  page={page}
                  perPage={perPage}
                  loading={loading}
                  onToggleSelect={toggleSelect}
                  onToggleExpand={toggleExpand}
                  onDownload={handleDownload}
                  onSetPage={setPage}
                  onPerPageSelect={handlePerPageSelect}
                />
              </>
            )}
          </CardBody>
        </Card>
      </PageSection>

      <HistoryDeleteModal
        isOpen={deleteModal}
        selectedCount={selected.size}
        deleting={deleting}
        onClose={() => setDeleteModal(false)}
        onConfirm={handleDelete}
      />
    </>
  );
};

export default HistoryPage;
