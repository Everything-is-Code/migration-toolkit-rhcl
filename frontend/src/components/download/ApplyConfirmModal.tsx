import React from 'react';
import { Modal, ModalVariant, Button, Form, FormGroup, TextInput } from '@patternfly/react-core';
import { useTranslation, Trans } from 'react-i18next';

interface Props {
  isOpen: boolean;
  namespace: string;
  packageName: string;
  fileCount: number;
  applying: boolean;
  onNamespaceChange: (value: string) => void;
  onClose: () => void;
  onConfirm: () => void;
}

const ApplyConfirmModal: React.FC<Props> = ({
  isOpen,
  namespace,
  packageName,
  fileCount,
  applying,
  onNamespaceChange,
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
        <Button key="apply" variant="primary" onClick={onConfirm} isLoading={applying} isDisabled={!namespace.trim()}>
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
          values={{ packageName, count: fileCount }}
          components={{ strong: <strong /> }}
        />
      </p>
      <Form>
        <FormGroup label={t('download.applyConfirmLabelNamespace')} fieldId="apply-confirm-ns" isRequired>
          <TextInput
            id="apply-confirm-ns"
            value={namespace}
            onChange={(_e, value) => onNamespaceChange(value)}
            placeholder="default"
          />
        </FormGroup>
      </Form>
      <p>{t('download.applyConfirmRbac')}</p>
    </Modal>
  );
};

export default ApplyConfirmModal;
