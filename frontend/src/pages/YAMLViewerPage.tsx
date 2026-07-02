import React, { useState, useCallback } from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  Tabs,
  Tab,
  TabTitleText,
  Button,
  Alert,
  Select,
  SelectOption,
  MenuToggle,
} from '@patternfly/react-core';
import { UndoIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { AppState } from '../App';
import { useNavigate } from 'react-router-dom';

interface Props {
  appState: AppState;
  setAppState: React.Dispatch<React.SetStateAction<AppState>>;
}

const YAMLViewerPage: React.FC<Props> = ({ appState, setAppState }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [activeService, setActiveService] = useState(0);
  const [activeTab, setActiveTab] = useState(0);
  const [selectOpen, setSelectOpen] = useState(false);

  // edits[serviceIndex][filename] = edited content
  const [edits, setEdits] = useState<Record<number, Record<string, string>>>(() => {
    const init: Record<number, Record<string, string>> = {};
    appState.conversionResults.forEach((r, i) => {
      if (r.yamlFiles) init[i] = { ...r.yamlFiles };
    });
    return init;
  });

  const results = appState.conversionResults.filter(r => r.yamlFiles);

  if (results.length === 0) {
    return (
      <PageSection>
        <Alert variant="warning" title={t('yamlViewer.warningTitle')}>
          <Button variant="link" onClick={() => navigate('/convert')}>{t('yamlViewer.goToConvert')}</Button>
        </Alert>
      </PageSection>
    );
  }

  const current = results[activeService];
  const originalFiles = Object.entries(current.yamlFiles || {});
  const currentEdits = edits[activeService] ?? {};

  const handleEdit = (filename: string, value: string) => {
    setEdits(prev => ({
      ...prev,
      [activeService]: { ...prev[activeService], [filename]: value },
    }));
  };

  const handleReset = (filename: string) => {
    const original = current.yamlFiles?.[filename] ?? '';
    setEdits(prev => ({
      ...prev,
      [activeService]: { ...prev[activeService], [filename]: original },
    }));
  };

  const isModified = (filename: string) =>
    currentEdits[filename] !== (current.yamlFiles?.[filename] ?? '');

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

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('yamlViewer.title')}</Title>
        <p style={{ marginTop: '8px', color: '#6a6e73' }}>
          {t('yamlViewer.description')}
        </p>
      </PageSection>
      <PageSection>
        <Card>
          <CardBody>
            {results.length > 1 && (
              <div style={{ marginBottom: '16px' }}>
                <Select
                  isOpen={selectOpen}
                  onOpenChange={setSelectOpen}
                  selected={current.serviceName}
                  onSelect={(_e, val) => {
                    const idx = results.findIndex(r => r.serviceName === val);
                    if (idx >= 0) setActiveService(idx);
                    setSelectOpen(false);
                    setActiveTab(0);
                  }}
                  toggle={(ref) => (
                    <MenuToggle ref={ref} onClick={() => setSelectOpen(!selectOpen)}>
                      {current.serviceName}
                    </MenuToggle>
                  )}
                >
                  {results.map((r, i) => (
                    <SelectOption key={i} value={r.serviceName}>{r.serviceName}</SelectOption>
                  ))}
                </Select>
              </div>
            )}

            <Tabs
              activeKey={activeTab}
              onSelect={(_e, key) => setActiveTab(Number(key))}
            >
              {originalFiles.map(([filename], i) => (
                <Tab
                  key={i}
                  eventKey={i}
                  title={
                    <TabTitleText>
                      {filename}
                      {isModified(filename) && (
                        <span style={{ marginLeft: 6, color: '#f0ab00', fontSize: 10 }}>●</span>
                      )}
                    </TabTitleText>
                  }
                >
                  <div style={{ marginTop: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 6 }}>
                      {isModified(filename) && (
                        <Button
                          variant="plain"
                          onClick={() => handleReset(filename)}
                          title={t('yamlViewer.btnReset')}
                          style={{ fontSize: 12, color: '#6a6e73', padding: '2px 8px' }}
                        >
                          <UndoIcon style={{ marginRight: 4 }} />
                          {t('yamlViewer.btnReset')}
                        </Button>
                      )}
                    </div>
                    <textarea
                      value={currentEdits[filename] ?? originalFiles[i][1]}
                      onChange={e => handleEdit(filename, e.target.value)}
                      spellCheck={false}
                      style={{
                        width: '100%',
                        minHeight: '500px',
                        background: '#1b1d21',
                        color: '#d4d4d4',
                        padding: '16px',
                        borderRadius: '4px',
                        border: isModified(filename) ? '1px solid #f0ab00' : '1px solid #3c3f42',
                        fontSize: '13px',
                        fontFamily: 'monospace',
                        lineHeight: 1.6,
                        resize: 'vertical',
                        boxSizing: 'border-box',
                        outline: 'none',
                      }}
                    />
                  </div>
                </Tab>
              ))}
            </Tabs>

            <div style={{ marginTop: '24px', display: 'flex', gap: '8px' }}>
              <Button variant="secondary" onClick={() => saveAndNavigate('/convert')}>
                {t('yamlViewer.btnBack')}
              </Button>
              <Button variant="primary" onClick={() => saveAndNavigate('/validate')}>
                {t('yamlViewer.btnNext')}
              </Button>
            </div>
          </CardBody>
        </Card>
      </PageSection>
    </>
  );
};

export default YAMLViewerPage;
