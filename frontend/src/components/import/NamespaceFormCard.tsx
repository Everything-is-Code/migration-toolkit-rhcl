import React from 'react';
import {
  Card,
  CardBody,
  Title,
  Button,
  Spinner,
  Form,
  FormGroup,
  TextInput,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import { CheckCircleIcon, DownloadIcon, PlayIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { PF_SUCCESS } from '../../styles/pfTokens';
import styles from '../../pages/ImportPage.module.css';

interface Props {
  namespace: string;
  packageName: string;
  pkgNameError: boolean;
  applying: boolean;
  portFixNotice: string | null;
  onNamespaceChange: (v: string) => void;
  onPackageNameChange: (v: string) => void;
  onApplyNamespace: () => void;
  onApply: () => void;
  onDownload: () => void;
}

const NamespaceFormCard: React.FC<Props> = ({
  namespace, packageName, pkgNameError, applying, portFixNotice,
  onNamespaceChange, onPackageNameChange, onApplyNamespace, onApply, onDownload,
}) => {
  const { t } = useTranslation();
  return (
    <Card>
      <CardBody>
        <Title headingLevel="h3" size="md" style={{ marginBottom: 12 }}>
          {t('import.namespaceSection')}
        </Title>
        <Form isHorizontal>
          <FormGroup label={<>{t('import.labelPackageName')}<span className={styles.requiredMark}>*</span></>} fieldId="imp-pkg">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <TextInput id="imp-pkg" value={packageName}
                onChange={(_e, v) => onPackageNameChange(v)}
                placeholder={t('import.pkgNamePlaceholder')}
                className={pkgNameError ? styles.inputError : undefined}
                style={{ width: 260 }} aria-invalid={pkgNameError} />
              {pkgNameError
                ? <span className={styles.fieldError}>{t('import.pkgNameRequired')}</span>
                : <span className={styles.fieldHint}>{t('import.pkgNameHint')}</span>}
            </div>
          </FormGroup>
          <FormGroup label={t('import.labelNamespace')} fieldId="imp-ns">
            <Flex>
              <FlexItem>
                <TextInput id="imp-ns" value={namespace}
                  onChange={(_e, v) => onNamespaceChange(v)}
                  placeholder="default" style={{ width: 260 }} />
              </FlexItem>
              <FlexItem>
                <Button variant="secondary" onClick={onApplyNamespace}>
                  {t('import.btnApplyNamespace')}
                </Button>
              </FlexItem>
            </Flex>
          </FormGroup>
        </Form>
        {portFixNotice && (
          <div className={`${styles.portNotice}${portFixNotice === 'portFixed443' ? ` ${styles.isSuccess}` : ''}`}>
            {portFixNotice === 'portFixed443'
              ? <CheckCircleIcon color={PF_SUCCESS} />
              : <span style={{ fontWeight: 700 }}>ℹ</span>}
            {t(`import.${portFixNotice}`)}
          </div>
        )}
        <div style={{ marginTop: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Button variant="primary"
            icon={applying ? <Spinner size="sm" /> : <PlayIcon />}
            onClick={onApply} isDisabled={applying}>
            {applying ? t('import.btnApplying') : t('import.btnApplyOc', { namespace })}
          </Button>
          <Button variant="secondary" icon={<DownloadIcon />} onClick={onDownload}>
            {t('import.btnDownloadZip')}
          </Button>
        </div>
      </CardBody>
    </Card>
  );
};

export default NamespaceFormCard;
