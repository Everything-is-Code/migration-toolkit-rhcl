import React from 'react';
import { Card, CardBody, Title, Alert, Label } from '@patternfly/react-core';
import { CheckCircleIcon, TimesCircleIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import styles from '../../pages/ImportPage.module.css';
import type { ApplyResult } from './importUtils';

interface Props {
  results: ApplyResult[];
  namespace: string;
}

const ImportResultTable: React.FC<Props> = ({ results, namespace }) => {
  const { t } = useTranslation();
  const successCount = results.filter(r => r.success).length;
  const errorCount   = results.filter(r => !r.success).length;

  return (
    <Card>
      <CardBody>
        <Title headingLevel="h3" size="md" style={{ marginBottom: 12 }}>
          {t('import.resultTitle')}
          {' '}<Label isCompact color="green">{t('import.successCount', { count: successCount })}</Label>
          {errorCount > 0 && <>{' '}<Label isCompact color="red">{t('import.failCount', { count: errorCount })}</Label></>}
        </Title>
        {errorCount === 0 && <Alert variant="success" isInline style={{ marginBottom: 12 }}
          title={t('import.allSuccessAlert', { count: successCount, namespace })} />}
        {errorCount > 0 && successCount === 0 && <Alert variant="danger" isInline style={{ marginBottom: 12 }}
          title={t('import.allFailAlert')} />}
        {errorCount > 0 && successCount > 0 && <Alert variant="warning" isInline style={{ marginBottom: 12 }}
          title={t('import.partialAlert', { success: successCount, error: errorCount })} />}
        <div style={{ overflowX: 'auto' }}>
          <table className={styles.resultsTable}>
            <caption style={{ textAlign: 'left', padding: '0 0 8px', fontWeight: 600 }}>
              {t('import.resultTitle')}
            </caption>
            <thead>
              <tr className={styles.resultsHeader}>
                <th scope="col" className={styles.resultsTh}>{t('import.colFileName')}</th>
                <th scope="col" className={styles.resultsTh} style={{ width: 90 }}>{t('import.colResult')}</th>
                <th scope="col" className={styles.resultsTh}>{t('import.colMessage')}</th>
              </tr>
            </thead>
            <tbody>
              {results.map(r => (
                <tr key={r.fileName} className={styles.resultsRow}>
                  <td style={{ padding: '8px 12px', fontFamily: 'monospace' }}>{r.fileName}</td>
                  <td style={{ padding: '8px 12px', whiteSpace: 'nowrap' }}>
                    {r.success
                      ? <CheckCircleIcon color="var(--pf-v5-global--success-color--100)" />
                      : <TimesCircleIcon color="var(--pf-v5-global--danger-color--100)" />}
                    {' '}{r.success ? t('import.resultSuccess') : t('import.resultFail')}
                  </td>
                  <td className={r.success ? styles.resultsOk : styles.resultsFail} style={{ padding: '8px 12px', fontSize: 12, wordBreak: 'break-word', maxWidth: 500 }}>
                    {r.message}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardBody>
    </Card>
  );
};

export default ImportResultTable;
