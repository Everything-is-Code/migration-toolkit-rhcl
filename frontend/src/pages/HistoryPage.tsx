import React, { useState, useEffect, useCallback } from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  Spinner,
  Alert,
  Label,
  Button,
  EmptyState,
  EmptyStateIcon,
  EmptyStateBody,
  Badge,
  Checkbox,
  Modal,
  ModalVariant,
  Pagination,
  PaginationVariant,
} from '@patternfly/react-core';
import {
  HistoryIcon,
  DownloadIcon,
  CubesIcon,
  TrashIcon,
  AngleRightIcon,
  AngleDownIcon,
  ExclamationCircleIcon,
  CheckCircleIcon,
} from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { historyApi } from '../api/client';
import { ConversionHistory, FailureDetail } from '../api/types';
import { getTimezone } from '../utils/timezone';
import { PF_COLOR_MUTED } from '../styles/pfTokens';
import shared from '../styles/shared.module.css';
import styles from './HistoryPage.module.css';

const DEFAULT_PAGE_SIZE = 50;

const formatDate = (iso: string): string => {
  try {
    const timeZone = getTimezone();
    return new Date(iso).toLocaleString(undefined, {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
      timeZone,
    });
  } catch { return iso; }
};

const statusColor = (status: string): 'green' | 'red' | 'orange' | 'blue' => {
  switch (status?.toUpperCase()) {
    case 'COMPLETED': return 'green';
    case 'FAILED':    return 'red';
    case 'PARTIAL':   return 'orange';
    default:          return 'blue';
  }
};

const sourceLabel = (source: string | undefined, t: (key: string) => string) => {
  if (source === 'IMPORT') return <Label isCompact color="purple">{t('history.sourceZipImport')}</Label>;
  if (source === 'CONVERT') return <Label isCompact color="blue">{t('history.sourceConvert')}</Label>;
  return <Label isCompact color="grey">{source ?? '—'}</Label>;
};

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
  // PF Pagination is 1-based; history API page is 0-based.
  const [page, setPage]               = useState(1);
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
      .catch(e => setError(e.message))
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
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const toggleAll = () => {
    if (selected.size === history.length) {
      setSelected(new Set());
    } else {
      setSelected(new Set(history.map(h => h.id)));
    }
  };

  const toggleExpand = (id: number) => {
    setExpanded(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
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
    } catch (e: any) {
      showToast(t('history.downloadError2', { message: e.message }));
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
    } catch (e: any) {
      showToast(t('history.deleteError', { message: e.message }));
    } finally {
      setDeleting(false);
    }
  };

  const parseFailures = (json?: string): FailureDetail[] => {
    if (!json) return [];
    try { return JSON.parse(json); } catch { return []; }
  };

  const allChecked = history.length > 0 && selected.size === history.length;
  const someChecked = selected.size > 0 && selected.size < history.length;

  return (
    <>
      {/* Toast notification */}
      {toast && (
        <div className={styles.toast}>
          {toast}
        </div>
      )}

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
            {selected.size > 0 && (
              <Button
                variant="danger"
                icon={<TrashIcon />}
                onClick={() => setDeleteModal(true)}
              >
                {t('history.btnDeleteSelected', { count: selected.size })}
              </Button>
            )}
            <Button variant="secondary" onClick={load} isDisabled={loading}>
              {t('history.btnReload')}
            </Button>
          </div>
        </div>
      </PageSection>

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
                {/* Toolbar */}
                <div className={styles.toolbar}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <Checkbox
                      id="select-all"
                      isChecked={allChecked}
                      onChange={toggleAll}
                      aria-label={t('history.ariaSelectAll')}
                      style={{ marginRight: 4 }}
                    />
                    <span className={styles.toolbarHint}>
                      {someChecked || allChecked
                        ? t('history.selectedCount2', { count: selected.size })
                        : t('history.selectAll')}
                    </span>
                  </div>
                  <Badge isRead>{t('history.countBadge', { count: total })}</Badge>
                </div>

                <Pagination
                  itemCount={total}
                  page={page}
                  perPage={perPage}
                  perPageOptions={[
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
                  style={{ padding: '8px 16px' }}
                />

                {/* Table */}
                <div style={{ overflowX: 'auto' }}>
                  <table className={styles.table}>
                    <caption style={{ textAlign: 'left', padding: '0 0 8px', fontWeight: 600 }}>
                      {t('history.titlePage')}
                    </caption>
                    <thead>
                      <tr className={styles.headerRow}>
                        <th scope="col" className={styles.th} style={{ width: 40 }}></th>
                        <th scope="col" className={styles.th} style={{ width: 32 }}></th>
                        <th scope="col" className={styles.th}>{t('history.colDateTime')}</th>
                        <th scope="col" className={styles.th}>{t('history.colType')}</th>
                        <th scope="col" className={styles.th}>{t('history.colPackageName')}</th>
                        <th scope="col" className={styles.th}>{t('history.colNamespace')}</th>
                        <th scope="col" className={styles.th} style={{ textAlign: 'center' }}>{t('history.colStatus')}</th>
                        <th scope="col" className={styles.th} style={{ textAlign: 'center' }}>{t('history.colSuccessFail')}</th>
                        <th scope="col" className={styles.th} style={{ textAlign: 'center', width: 120 }}>{t('history.colOps')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {history.map((entry, idx) => {
                        const failures = parseFailures(entry.failureDetails);
                        const hasFailures = failures.length > 0;
                        const isExpanded = expanded.has(entry.id);
                        const isSelected = selected.has(entry.id);

                        return (
                          <React.Fragment key={entry.id}>
                            <tr
                              className={[
                                styles.row,
                                idx % 2 === 1 ? styles.odd : '',
                                isSelected ? styles.isSelected : '',
                                isExpanded ? styles.isExpanded : '',
                              ].filter(Boolean).join(' ')}
                            >
                              {/* Checkbox */}
                              <td className={styles.td} style={{ textAlign: 'center' }}>
                                <Checkbox
                                  id={`chk-${entry.id}`}
                                  isChecked={isSelected}
                                  onChange={() => toggleSelect(entry.id)}
                                  aria-label={t('history.ariaSelectEntry', { id: entry.id })}
                                />
                              </td>

                              {/* Expand button (only when there are failures) */}
                              <td className={styles.td} style={{ textAlign: 'center' }}>
                                {hasFailures && (
                                  <Button variant="plain" size="sm"
                                    onClick={() => toggleExpand(entry.id)}
                                    aria-label={t('history.ariaDetails')}>
                                    {isExpanded ? <AngleDownIcon /> : <AngleRightIcon />}
                                  </Button>
                                )}
                              </td>

                              {/* Execution date/time */}
                              <td className={styles.td}>
                                <span style={{ fontSize: 13, whiteSpace: 'nowrap' }}>
                                  {formatDate(entry.createdAt)}
                                </span>
                              </td>

                              {/* Type */}
                              <td className={styles.td}>{sourceLabel(entry.source, t)}</td>

                              {/* Service/Package name */}
                              <td className={styles.td}>
                                {(() => {
                                  const name = entry.source === 'IMPORT'
                                    ? entry.packageName
                                    : entry.serviceName;
                                  return name
                                    ? <code style={{ fontSize: 13 }}>{name}</code>
                                    : <span className={styles.dash}>—</span>;
                                })()}
                              </td>

                              {/* Namespace */}
                              <td className={styles.td}>
                                <code style={{ fontSize: 12 }}>{entry.namespace ?? '—'}</code>
                              </td>

                              {/* Status */}
                              <td className={styles.td} style={{ textAlign: 'center' }}>
                                <Label color={statusColor(entry.status)}>
                                  {entry.status === 'COMPLETED' ? t('history.statusSuccess')
                                    : entry.status === 'FAILED' ? t('history.statusFailed')
                                    : entry.status === 'PARTIAL' ? t('history.statusPartial')
                                    : entry.status}
                                </Label>
                              </td>

                              {/* Success/Failure count */}
                              <td className={styles.td} style={{ textAlign: 'center' }}>
                                {entry.totalCount != null ? (
                                  <span style={{ fontSize: 13 }}>
                                    <span className={styles.successCount}>
                                      <CheckCircleIcon style={{ marginRight: 3, fontSize: 12 }} />
                                      {entry.successCount ?? 0}
                                    </span>
                                    <span className={styles.mutedSep}>/</span>
                                    <span className={`${styles.failCount}${(entry.failureCount ?? 0) > 0 ? ` ${styles.hasFailures}` : ''}`}>
                                      <ExclamationCircleIcon style={{ marginRight: 3, fontSize: 12 }} />
                                      {entry.failureCount ?? 0}
                                    </span>
                                    <span className={styles.totalHint}>
                                      {t('history.totalOf', { count: entry.totalCount })}
                                    </span>
                                  </span>
                                ) : '—'}
                              </td>

                              {/* Download */}
                              <td className={styles.td} style={{ textAlign: 'center' }}>
                                <Button
                                  variant="secondary"
                                  icon={<DownloadIcon />}
                                  size="sm"
                                  onClick={() => handleDownload(entry)}
                                  isDisabled={downloading[entry.id] || entry.status === 'FAILED'}
                                >
                                  {downloading[entry.id] ? '...' : 'YAML'}
                                </Button>
                              </td>
                            </tr>

                            {/* Expanded: failure details */}
                            {isExpanded && hasFailures && (
                              <tr className={styles.failureDetailRow}>
                                <td colSpan={9} className={styles.failureDetailCell}>
                                  <table className={styles.table} style={{ fontSize: 12 }}>
                                    <caption className={styles.failureCaption}>
                                      {t('history.failedResources', { count: failures.length })}
                                    </caption>
                                    <thead>
                                      <tr className={styles.failureHeader}>
                                        <th scope="col" className={styles.th} style={{ padding: '4px 12px' }}>{t('history.colFile')}</th>
                                        <th scope="col" className={styles.th} style={{ padding: '4px 12px' }}>Kind</th>
                                        <th scope="col" className={styles.th} style={{ padding: '4px 12px' }}>{t('history.colName')}</th>
                                        <th scope="col" className={styles.th} style={{ padding: '4px 12px' }}>{t('history.colError')}</th>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      {failures.map((f, i) => (
                                        <tr key={i} className={styles.failureItemRow}>
                                          <td style={{ padding: '4px 12px', fontFamily: 'monospace' }}>{f.fileName}</td>
                                          <td style={{ padding: '4px 12px' }}>
                                            <Label isCompact color="red">{f.kind}</Label>
                                          </td>
                                          <td style={{ padding: '4px 12px', fontFamily: 'monospace' }}>{f.name}</td>
                                          <td className={styles.failureError}>
                                            {f.error}
                                          </td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                </td>
                              </tr>
                            )}
                          </React.Fragment>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                <Pagination
                  itemCount={total}
                  page={page}
                  perPage={perPage}
                  perPageOptions={[
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
                  style={{ padding: '8px 16px' }}
                />
              </>
            )}
          </CardBody>
        </Card>
      </PageSection>

      {/* Delete confirmation modal */}
      <Modal
        variant={ModalVariant.small}
        title={t('history.deleteTitle')}
        isOpen={deleteModal}
        onClose={() => setDeleteModal(false)}
        actions={[
          <Button key="del" variant="danger" onClick={handleDelete} isLoading={deleting}>
            {t('history.btnDelete')}
          </Button>,
          <Button key="cancel" variant="link" onClick={() => setDeleteModal(false)}>
            {t('history.btnCancel')}
          </Button>,
        ]}
      >
        <span dangerouslySetInnerHTML={{ __html: t('history.deleteConfirm', { count: selected.size }) }} />
        {' '}{t('history.deleteWarn')}
      </Modal>
    </>
  );
};


export default HistoryPage;
