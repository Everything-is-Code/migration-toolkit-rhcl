import React from 'react';
import { Button } from '@patternfly/react-core';
import { UndoIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import styles from '../../pages/YAMLViewerPage.module.css';

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
  /^\s*type:\s*PathPrefix/,
  /^\s*type:\s*ResponseHeaderModifier/,
  /^\s*type:\s*RequestHeaderModifier/,
  /^\s*responseHeaderModifier:/,
  /^\s*requestHeaderModifier:/,
  /^\s*set:/,
  /^\s*add:/,
  /^\s*remove:/,
  /^\s*- name:\s*\S/,
  /^\s*allNamespaces:/,
  /^\s*prefix:/,
  /kind:\s*Telemetry/,
  /kind:\s*EnvoyFilter/,
  /^\s*accessLogging:/,
  /^\s*configPatches:/,
  /^\s*applyTo:/,
  /^\s*access_log:/,
  /^\s*log_format:/,
  /^\s*json_format:/,
  /^\s*typed_config:/,
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

interface Props {
  filename: string;
  originalContent: string;
  editedContent: string;
  editMode: boolean;
  isModified: boolean;
  onEdit: (filename: string, value: string) => void;
  onReset: (filename: string) => void;
}

const YamlEditorPanel: React.FC<Props> = ({
  filename,
  editedContent,
  editMode,
  isModified,
  onEdit,
  onReset,
}) => {
  const { t } = useTranslation();

  const renderHighlightedYaml = (content: string) => {
    const lines = content.length === 0 ? [''] : content.split('\n');
    const gutterWidth = `${String(lines.length).length + 1}ch`;
    return (
      <table
        aria-label={t('yamlViewer.previewWithLineNumbers')}
        style={{ borderCollapse: 'collapse', width: '100%', tableLayout: 'fixed' }}
      >
        <tbody>
          {lines.map((line, i) => {
            const isPolicy = POLICY_PATTERNS.some(p => p.test(line));
            return (
              <tr key={i}>
                <td
                  className={styles.lineMeta}
                  style={{
                    width: gutterWidth,
                    minWidth: gutterWidth,
                    padding: '0 12px 0 0',
                    textAlign: 'right',
                    userSelect: 'none',
                    verticalAlign: 'top',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {i + 1}
                </td>
                <td
                  className={isPolicy ? styles.policyHighlight : undefined}
                  style={{ padding: 0, verticalAlign: 'top', whiteSpace: 'pre', overflowWrap: 'normal', wordBreak: 'normal' }}
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

  const editorClass = `${styles.editor}${isModified ? ` ${styles.isModified}` : ''}`;

  if (editMode) {
    return (
      <>
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 6 }}>
          {isModified && (
            <Button
              variant="plain"
              onClick={() => onReset(filename)}
              title={t('yamlViewer.btnReset')}
              className={styles.resetButton}
            >
              <UndoIcon style={{ marginRight: 4 }} />
              {t('yamlViewer.btnReset')}
            </Button>
          )}
        </div>
        <textarea
          value={editedContent}
          onChange={e => onEdit(filename, e.target.value)}
          spellCheck={false}
          className={editorClass}
          style={{ minHeight: '500px', lineHeight: 1.6, resize: 'vertical', boxSizing: 'border-box', outline: 'none' }}
        />
      </>
    );
  }

  return (
    <pre
      className={editorClass}
      style={{ minHeight: '500px', lineHeight: 1.6, boxSizing: 'border-box', overflowX: 'auto', whiteSpace: 'pre-wrap', margin: 0 }}
    >
      {renderHighlightedYaml(editedContent)}
    </pre>
  );
};

export default YamlEditorPanel;
