import React, { useState, useCallback, useEffect, useRef } from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  Button,
  Alert,
} from '@patternfly/react-core';
import { PencilAltIcon, TimesIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { useAppState } from '../components/AppStateContext';
import { useNavigate } from 'react-router-dom';
import YamlFileTabs from '../components/yaml/YamlFileTabs';
import YamlEditorPanel from '../components/yaml/YamlEditorPanel';
import {
  buildEditsFromResults,
  conversionResultsFingerprint,
} from '../components/conversion/conversionWorkflowState';
import styles from '../components/yaml/yamlViewer.module.css';

const YAMLViewerPage: React.FC = () => {
  const { appState, setAppState } = useAppState();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [activeService, setActiveService] = useState(0);
  const [activeTab, setActiveTab] = useState(0);
  const [selectOpen, setSelectOpen] = useState(false);
  const [editMode, setEditMode] = useState(false);

  const [edits, setEdits] = useState<Record<number, Record<string, string>>>(() =>
    buildEditsFromResults(appState.conversionResults),
  );

  const fingerprint = conversionResultsFingerprint(appState.conversionResults);
  const prevFingerprint = useRef(fingerprint);

  useEffect(() => {
    if (prevFingerprint.current === fingerprint) {
      return;
    }
    prevFingerprint.current = fingerprint;
    setEdits(buildEditsFromResults(appState.conversionResults));
    setActiveService(0);
    setActiveTab(0);
  }, [fingerprint, appState.conversionResults]);

  const results = appState.conversionResults.filter(r => r.yamlFiles);
  const noResults = results.length === 0;
  const current = noResults ? null : results[activeService];
  const currentEdits = current ? (edits[activeService] ?? {}) : {};

  const handleEdit = (filename: string, value: string) => {
    setEdits(prev => ({
      ...prev,
      [activeService]: { ...prev[activeService], [filename]: value },
    }));
  };

  const handleReset = (filename: string) => {
    if (!current) return;
    const original = current.yamlFiles?.[filename] ?? '';
    setEdits(prev => ({
      ...prev,
      [activeService]: { ...prev[activeService], [filename]: original },
    }));
  };

  const isModified = (filename: string) =>
    current ? currentEdits[filename] !== (current.yamlFiles?.[filename] ?? '') : false;

  const saveAndNavigate = useCallback((path: string) => {
    setAppState(prev => {
      const updated = prev.conversionResults.map((r, i) => {
        const svcEdits = edits[i];
        if (!svcEdits || !r.yamlFiles) return r;
        return { ...r, yamlFiles: { ...r.yamlFiles, ...svcEdits } };
      });
      return { ...prev, conversionResults: updated };
    });
    navigate(path);
  }, [edits, setAppState, navigate]);

  const renderTabContent = (filename: string, originalContent: string) => (
    <YamlEditorPanel
      filename={filename}
      originalContent={originalContent}
      editedContent={currentEdits[filename] ?? originalContent}
      editMode={editMode}
      isModified={isModified(filename)}
      onEdit={handleEdit}
      onReset={handleReset}
    />
  );

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <Title headingLevel="h1" size="2xl">{t('yamlViewer.title')}</Title>
            {!noResults && (
              <p className={styles.pageDescription}>{t('yamlViewer.description')}</p>
            )}
          </div>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            {!noResults && (
              editMode ? (
                <Button variant="secondary" onClick={() => setEditMode(false)}>
                  <TimesIcon style={{ marginRight: 6 }} />
                  {t('yamlViewer.btnViewMode')}
                </Button>
              ) : (
                <Button variant="secondary" onClick={() => setEditMode(true)}>
                  <PencilAltIcon style={{ marginRight: 6 }} />
                  {t('yamlViewer.btnEdit')}
                </Button>
              )
            )}
            <Button variant="secondary" onClick={() => saveAndNavigate('/convert')}>
              {t('yamlViewer.btnBack')}
            </Button>
            <Button variant="primary" onClick={() => saveAndNavigate('/validate')} isDisabled={noResults}>
              {t('yamlViewer.btnNext')}
            </Button>
          </div>
        </div>
      </PageSection>
      <PageSection>
        {noResults ? (
          <Alert variant="warning" title={t('yamlViewer.warningTitle')} />
        ) : (
          <Card>
            <CardBody>
              <YamlFileTabs
                results={results}
                activeService={activeService}
                activeTab={activeTab}
                selectOpen={selectOpen}
                onServiceChange={(idx) => { setActiveService(idx); setActiveTab(0); }}
                onTabChange={setActiveTab}
                onSelectOpenChange={setSelectOpen}
                isModified={isModified}
                renderTabContent={renderTabContent}
              />
            </CardBody>
          </Card>
        )}
      </PageSection>
    </>
  );
};

export default YAMLViewerPage;
