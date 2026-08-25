import React from 'react';
import { Modal, ModalVariant, Button } from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';

interface Props {
  isOpen: boolean;
  selectedCount: number;
  deleting: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

const HistoryDeleteModal: React.FC<Props> = ({
  isOpen,
  selectedCount,
  deleting,
  onClose,
  onConfirm,
}) => {
  const { t } = useTranslation();

  return (
    <Modal
      variant={ModalVariant.small}
      title={t('history.deleteTitle')}
      isOpen={isOpen}
      onClose={onClose}
      actions={[
        <Button key="del" variant="danger" onClick={onConfirm} isLoading={deleting}>
          {t('history.btnDelete')}
        </Button>,
        <Button key="cancel" variant="link" onClick={onClose}>
          {t('history.btnCancel')}
        </Button>,
      ]}
    >
      <span dangerouslySetInnerHTML={{ __html: t('history.deleteConfirm', { count: selectedCount }) }} />
      {' '}{t('history.deleteWarn')}
    </Modal>
  );
};

export default HistoryDeleteModal;
