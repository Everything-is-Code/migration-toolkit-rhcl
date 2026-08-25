import React from 'react';
import { Card, CardBody, Button, Flex, FlexItem, Label } from '@patternfly/react-core';
import { CheckCircleIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';

interface Props {
  uploadedName: string;
  fileCount: number;
  onReset: () => void;
}

const FileInfoBar: React.FC<Props> = ({ uploadedName, fileCount, onReset }) => {
  const { t } = useTranslation();
  return (
    <Card><CardBody>
      <Flex alignItems={{ default: 'alignItemsCenter' }}>
        <FlexItem>
          <CheckCircleIcon color="var(--pf-v5-global--success-color--100)" />
          {' '}<strong>{uploadedName}</strong>{' — '}
          <Label isCompact color="blue">{t('import.fileCount', { count: fileCount })}</Label>
        </FlexItem>
        <FlexItem align={{ default: 'alignRight' }}>
          <Button variant="link" onClick={onReset}>{t('import.btnUploadAnother')}</Button>
        </FlexItem>
      </Flex>
    </CardBody></Card>
  );
};

export default FileInfoBar;
