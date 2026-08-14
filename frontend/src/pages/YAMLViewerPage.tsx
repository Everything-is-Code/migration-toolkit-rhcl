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
import { UndoIcon, PencilAltIcon, TimesIcon } from '@patternfly/react-icons';
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
  const [editMode, setEditMode] = useState(false);

  // edits[serviceIndex][filename] = edited content
  const [edits, setEdits] = useState<Record<number, Record<string, string>>>(() => {
    const init: Record<number, Record<string, string>> = {};
    appState.conversionResults.forEach((r, i) => {
      if (r.yamlFiles) init[i] = { ...r.yamlFiles };
    });
    return init;
  });

  const results = appState.conversionResults.filter(r => r.yamlFiles);
  const noResults = results.length === 0;
  const current = noResults ? null : results[activeService];

  const handleEdit = (filename: string, value: string) => {
    setEdits(prev => ({
      ...prev,
      [activeService]: { ...prev[activeService], [filename]: value },
    }));
  };

  const originalFiles = current ? Object.entries(current.yamlFiles || {}) : [];
  const currentEdits = current ? (edits[activeService] ?? {}) : {};

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

  const POLICY_PATTERNS = [
    /kind:\s*AuthPolicy/,
    /kind:\s*RateLimitPolicy/,
    /^\s*authentication:/,
    /^\s*authorization:/,
    /^\s*jwt-auth:/,
    /^\s*api-key-auth:/,
    /^\s*authorizationHeader:/,
    /^\s*credentials:/,
    /^\s*timeouts:/,
    /^\s*backendRequest:/,
    /^\s*request:.*#/,
    /#\s*(connect_timeout|send_timeout|upstream)/,
    // Anonymous Access
    /^\s*anonymous:/,
    /anonymous-credentials/,
    /3scale-migration\/anonymous-access/,
    /3scale-migration\/auth-type/,
    /^\s*x-user-key:/,
    /^\s*x-app-id:/,
    /^\s*x-app-key:/,
    /^\s*valueFrom:/,
    /^\s*secretKeyRef:/,
    /^\s*user_key:/,
    /^\s*app_id:/,
    /^\s*app_key:/,
    /^\s*plain:/,
    /^\s*value:\s*"/,
    // HTTPRoute path / header modification
    /^\s*type:\s*PathPrefix/,
    /^\s*type:\s*ResponseHeaderModifier/,
    /^\s*type:\s*RequestHeaderModifier/,
    /^\s*responseHeaderModifier:/,
    /^\s*requestHeaderModifier:/,
    /^\s*set:/,
    /^\s*add:/,
    /^\s*remove:/,
    /^\s*- name:\s*\S/,
    // API Key auth selector
    /^\s*allNamespaces:/,
    /^\s*prefix:/,
    // Logging / Telemetry / EnvoyFilter
    /kind:\s*Telemetry/,
    /kind:\s*EnvoyFilter/,
    /^\s*accessLogging:/,
    /^\s*configPatches:/,
    /^\s*applyTo:/,
    /^\s*access_log:/,
    /^\s*log_format:/,
    /^\s*json_format:/,
    /^\s*typed_config:/,
    // Envoy log format fields (json_format entries containing Envoy variables)
    /%REQ\(/,
    /%RESPONSE_CODE%/,
    /%DURATION%/,
    /%DOWNSTREAM_REMOTE_ADDRESS/,
    /%BYTES_SENT%/,
    /%QUERY_STRING%/,
    /3scale-migration\/source/,
    /3scale-migration\/enable-json/,
    /3scale-migration\/enable-access/,
  ];

  const renderHighlightedYaml = (content: string) => {
    const lines = content.length === 0 ? [''] : content.split('\n');
    const gutterWidth = `${String(lines.length).length + 1}ch`;
    return (
      <table
        aria-label={t('yamlViewer.previewWithLineNumbers')}
        style={{
          borderCollapse: 'collapse',
          width: '100%',
          tableLayout: 'fixed',
        }}
      >
        <tbody>
          {lines.map((line, i) => {
            const isPolicy = POLICY_PATTERNS.some(p => p.test(line));
            return (
              <tr key={i}>
                <td
                  style={{
                    width: gutterWidth,
                    minWidth: gutterWidth,
                    padding: '0 12px 0 0',
                    textAlign: 'right',
                    color: '#6a6e73',
                    userSelect: 'none',
                    verticalAlign: 'top',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {i + 1}
                </td>
                <td
                  style={{
                    padding: 0,
                    verticalAlign: 'top',
                    whiteSpace: 'pre',
                    overflowWrap: 'normal',
                    wordBreak: 'normal',
                    color: isPolicy ? '#ffa657' : undefined,
                  }}
                >
                  {line.length === 0 ? '\u00a0' : line}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    );
  };

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
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <Title headingLevel="h1" size="2xl">{t('yamlViewer.title')}</Title>
            {!noResults && (
              <p style={{ marginTop: '8px', color: '#6a6e73', whiteSpace: 'pre-line' }}>
                {t('yamlViewer.description')}
              </p>
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
              {results.length > 1 && current && (
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
                      {editMode ? (
                        <>
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
                        </>
                      ) : (
                        <pre style={{
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
                          boxSizing: 'border-box',
                          overflowX: 'auto',
                          whiteSpace: 'pre-wrap',
                          margin: 0,
                        }}>
                          {renderHighlightedYaml(currentEdits[filename] ?? originalFiles[i][1])}
                        </pre>
                      )}
                    </div>
                  </Tab>
                ))}
              </Tabs>
            </CardBody>
          </Card>
        )}
      </PageSection>
    </>
  );
};

export default YAMLViewerPage;
