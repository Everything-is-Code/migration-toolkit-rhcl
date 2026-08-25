import React, { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import styles from './import.module.css';
import { computeDiff } from './importUtils';
import type { YamlFile, EditMap } from './importUtils';

interface Props {
  files: YamlFile[];
  edits: EditMap;
  onEdit: (name: string, val: string) => void;
}

const YamlDiffViewer: React.FC<Props> = ({ files, edits, onEdit }) => {
  const { t } = useTranslation();
  const [active, setActive] = useState(0);
  const [mode, setMode] = useState<'view' | 'edit' | 'diff'>('view');

  const current = files[active] ?? files[0];
  const content = current ? (edits[current.name] ?? current.content) : '';
  const isEdited = Boolean(
    current && edits[current.name] !== undefined && edits[current.name] !== current.content,
  );
  const originalContent = current?.content ?? '';

  const diffLines = useMemo(
    () => (mode === 'diff' && current ? computeDiff(originalContent, content) : []),
    [mode, originalContent, content, current],
  );

  if (files.length === 0 || !current) return null;

  const panelId = 'yaml-panel';

  return (
    <div>
      <div
        role="tablist"
        aria-label={t('import.yamlEditorTitle')}
        className={styles.tabList}
      >
        {files.map((f, i) => {
          const changed = edits[f.name] !== undefined && edits[f.name] !== f.content;
          return (
            <button
              key={f.name}
              type="button"
              role="tab"
              id={`yaml-tab-${i}`}
              aria-selected={i === active}
              aria-controls={panelId}
              tabIndex={i === active ? 0 : -1}
              onClick={() => { setActive(i); setMode('view'); }}
              className={`${styles.tab}${i === active ? ` ${styles.isActive}` : ''}`}
            >
              {f.name}
              {changed && (
                <span className={styles.modifiedBadge}>M</span>
              )}
            </button>
          );
        })}
      </div>

      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', marginBottom: 8 }}>
        <button
          type="button"
          onClick={() => setMode('view')}
          className={`${styles.modeButton}${mode === 'view' ? ` ${styles.isActive}` : ''}`}
        >
          {t('import.viewMode')}
        </button>
        <button
          type="button"
          onClick={() => setMode('edit')}
          className={`${styles.modeButton}${mode === 'edit' ? ` ${styles.isActive}` : ''}`}
        >
          {t('import.editMode')}
        </button>
        <button
          type="button"
          onClick={() => setMode('diff')}
          disabled={!isEdited}
          className={`${styles.modeButton}${mode === 'diff' ? ` ${styles.diffActive}` : ''}`}
        >
          {t('import.diffMode')}
        </button>
      </div>

      <div
        role="tabpanel"
        id={panelId}
        aria-labelledby={`yaml-tab-${active}`}
      >
        {mode === 'edit' ? (
          <textarea
            value={content}
            onChange={e => onEdit(current.name, e.target.value)}
            aria-label={t('import.editMode') + ': ' + current.name}
            className={styles.editorTextarea}
          />
        ) : mode === 'diff' ? (
          <div className={styles.diffPane}>
            {diffLines.map((line, idx) => (
              <div
                key={idx}
                className={`${styles.diffLine}${line.type === 'add' ? ` ${styles.diffAdd}` : line.type === 'remove' ? ` ${styles.diffRemove}` : ''}`}
              >
                <span className={styles.diffMarker}>
                  {line.type === 'add' ? '+' : line.type === 'remove' ? '-' : ' '}
                </span>
                <span className={styles.diffText}>
                  {line.text}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <pre className={styles.editorPre}>
            {content}
          </pre>
        )}
      </div>
    </div>
  );
};

export default YamlDiffViewer;
