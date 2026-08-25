import React from 'react';
import {
  Button,
  Label,
  Checkbox,
  Pagination,
  PaginationVariant,
} from '@patternfly/react-core';
import {
  DownloadIcon,
  AngleRightIcon,
  AngleDownIcon,
  ExclamationCircleIcon,
  CheckCircleIcon,
} from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { ConversionHistory, FailureDetail } from '../../api/types';
import { formatDate, statusColor } from './historyUtils';
import { sourceLabel } from './historyLabels';
import styles from './history.module.css';

const PER_PAGE_OPTIONS = [
  { title: '20', value: 20 },
  { title: '50', value: 50 },
  { title: '100', value: 100 },
];

const parseFailures = (json?: string): FailureDetail[] => {
  if (!json) return [];
  try { return JSON.parse(json); } catch { return []; }
};

interface Props {
  history: ConversionHistory[];
  selected: Set<number>;
  expanded: Set<number>;
  downloading: Record<number, boolean>;
  total: number;
  page: number;
  perPage: number;
  loading: boolean;
  onToggleSelect: (id: number) => void;
  onToggleExpand: (id: number) => void;
  onDownload: (entry: ConversionHistory) => void;
  onSetPage: (page: number) => void;
  onPerPageSelect: (perPage: number) => void;
}

const HistoryTable: React.FC<Props> = ({
  history,
  selected,
  expanded,
  downloading,
  total,
  page,
  perPage,
  loading,
  onToggleSelect,
  onToggleExpand,
  onDownload,
  onSetPage,
  onPerPageSelect,
}) => {
  const { t } = useTranslation();

  return (
    <>
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
                    <td className={styles.td} style={{ textAlign: 'center' }}>
                      <Checkbox
                        id={`chk-${entry.id}`}
                        isChecked={isSelected}
                        onChange={() => onToggleSelect(entry.id)}
                        aria-label={t('history.ariaSelectEntry', { id: entry.id })}
                      />
                    </td>

                    <td className={styles.td} style={{ textAlign: 'center' }}>
                      {hasFailures && (
                        <Button variant="plain" size="sm"
                          onClick={() => onToggleExpand(entry.id)}
                          aria-label={t('history.ariaDetails')}>
                          {isExpanded ? <AngleDownIcon /> : <AngleRightIcon />}
                        </Button>
                      )}
                    </td>

                    <td className={styles.td}>
                      <span style={{ fontSize: 13, whiteSpace: 'nowrap' }}>
                        {formatDate(entry.createdAt)}
                      </span>
                    </td>

                    <td className={styles.td}>{sourceLabel(entry.source, t)}</td>

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

                    <td className={styles.td}>
                      <code style={{ fontSize: 12 }}>{entry.namespace ?? '—'}</code>
                    </td>

                    <td className={styles.td} style={{ textAlign: 'center' }}>
                      <Label color={statusColor(entry.status)}>
                        {entry.status === 'COMPLETED' ? t('history.statusSuccess')
                          : entry.status === 'FAILED' ? t('history.statusFailed')
                          : entry.status === 'PARTIAL' ? t('history.statusPartial')
                          : entry.status}
                      </Label>
                    </td>

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

                    <td className={styles.td} style={{ textAlign: 'center' }}>
                      <Button
                        variant="secondary"
                        icon={<DownloadIcon />}
                        size="sm"
                        onClick={() => onDownload(entry)}
                        isDisabled={downloading[entry.id] || entry.status === 'FAILED'}
                      >
                        {downloading[entry.id] ? '...' : 'YAML'}
                      </Button>
                    </td>
                  </tr>

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
        perPageOptions={PER_PAGE_OPTIONS}
        onSetPage={(_e, newPage) => onSetPage(newPage)}
        onPerPageSelect={(_e, newPerPage) => onPerPageSelect(newPerPage)}
        variant={PaginationVariant.bottom}
        isDisabled={loading}
        style={{ padding: '8px 16px' }}
      />
    </>
  );
};

export default HistoryTable;
