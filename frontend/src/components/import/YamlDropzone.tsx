import React, { useRef, useState } from 'react';
import { Card, CardBody, Button, Spinner } from '@patternfly/react-core';
import { UploadIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import styles from '../../pages/ImportPage.module.css';

interface Props {
  loading: boolean;
  onFileSelected: (file: File) => void;
}

const YamlDropzone: React.FC<Props> = ({ loading, onFileSelected }) => {
  const { t } = useTranslation();
  const fileRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const f = e.dataTransfer.files[0];
    if (f) onFileSelected(f);
  };

  return (
    <Card>
      <CardBody>
        <div
          onDragOver={e => { e.preventDefault(); setDragOver(true); }}
          onDragLeave={() => setDragOver(false)}
          onDrop={handleDrop}
          onClick={() => !loading && fileRef.current?.click()}
          className={`${styles.dropZone}${dragOver ? ` ${styles.isDragOver}` : ''}`}
          style={{ cursor: loading ? 'default' : 'pointer' }}
        >
          {loading ? (
            <><Spinner size="lg" /><p style={{ marginTop: 16 }}>{t('import.analyzing')}</p></>
          ) : (
            <>
              <UploadIcon className={styles.mutedText} style={{ fontSize: '3rem' }} />
              <p style={{ marginTop: 16, fontSize: '1.1rem', fontWeight: 500 }}>
                {t('import.dropZone')}
              </p>
              <p className={styles.mutedText} style={{ marginTop: 8 }}>{t('import.orClick')}</p>
              <Button variant="primary" style={{ marginTop: 16 }}
                onClick={e => { e.stopPropagation(); fileRef.current?.click(); }}>
                {t('import.btnSelectFile')}
              </Button>
            </>
          )}
          <input ref={fileRef} type="file" accept=".zip" style={{ display: 'none' }}
            onChange={e => { const f = e.target.files?.[0]; if (f) onFileSelected(f); e.target.value = ''; }} />
        </div>
      </CardBody>
    </Card>
  );
};

export default YamlDropzone;
