import React from 'react';
import { Modal, ModalVariant, Button } from '@patternfly/react-core';
import { useTranslation, Trans } from 'react-i18next';

interface Props {
  isOpen: boolean;
  namespace: string;
  packageName: string;
  fileCount: number;
  applying: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

const ApplyConfirmModal: React.FC<Props> = ({
  isOpen,
  namespace,
  packageName,
  fileCount,
  applying,
  onClose,
  onConfirm,
}) => {
  const { t } = useTranslation();

  return (
    <Modal
      variant={ModalVariant.small}
      title={t('download.applyConfirmTitle')}
      isOpen={isOpen}
      onClose={onClose}
      actions={[
        <Button key="apply" variant="primary" onClick={onConfirm} isLoading={applying}>
          {applying ? t('download.btnApplying') : t('download.applyConfirmAction')}
        </Button>,
        <Button key="cancel" variant="link" onClick={onClose}>
          {t('download.applyConfirmCancel')}
        </Button>,
      ]}
    >
      <p>
        <Trans
          i18nKey="download.applyConfirmBody"
          values={{ namespace, packageName, count: fileCount }}
          components={{ strong: <strong /> }}
        />
      </p>
      <p>{t('download.applyConfirmRbac')}</p>
    </Modal>
  );
};

export default ApplyConfirmModal;
