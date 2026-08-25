import React from 'react';
import { Label } from '@patternfly/react-core';

export const sourceLabel = (
  source: string | undefined,
  t: (key: string) => string,
): React.ReactElement => {
  if (source === 'IMPORT') return <Label isCompact color="purple">{t('history.sourceZipImport')}</Label>;
  if (source === 'CONVERT') return <Label isCompact color="blue">{t('history.sourceConvert')}</Label>;
  return <Label isCompact color="grey">{source ?? '—'}</Label>;
};
