import React from 'react';
import { Label } from '@patternfly/react-core';
import { getTimezone } from '../../utils/timezone';

export const formatDate = (iso: string): string => {
  try {
    const timeZone = getTimezone();
    return new Date(iso).toLocaleString(undefined, {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
      timeZone,
    });
  } catch { return iso; }
};

export const statusColor = (status: string): 'green' | 'red' | 'orange' | 'blue' => {
  switch (status?.toUpperCase()) {
    case 'COMPLETED': return 'green';
    case 'FAILED':    return 'red';
    case 'PARTIAL':   return 'orange';
    default:          return 'blue';
  }
};

export const sourceLabel = (
  source: string | undefined,
  t: (key: string) => string,
): React.ReactElement => {
  if (source === 'IMPORT') return <Label isCompact color="purple">{t('history.sourceZipImport')}</Label>;
  if (source === 'CONVERT') return <Label isCompact color="blue">{t('history.sourceConvert')}</Label>;
  return <Label isCompact color="grey">{source ?? '—'}</Label>;
};
