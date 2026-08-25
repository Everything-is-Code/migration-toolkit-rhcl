import React from 'react';
import { useTranslation } from 'react-i18next';
import { Policy } from '../../api/types';
import styles from '../../pages/APISelectionPage.module.css';
import shared from '../../styles/shared.module.css';

const renderConfigValue = (v: unknown): string => {
  if (v === null || v === undefined) return '';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
};

interface Props {
  policies: Policy[];
}

const PolicyPanel: React.FC<Props> = ({ policies }) => {
  const { t } = useTranslation();
  if (policies.length === 0) return null;
  return (
    <div className={styles.policyPanel}>
      <div className={styles.policyPanelTitle}>
        {t('apiSelection.policyDefinitions', 'Policy Definitions')}
      </div>
      {policies.map((p, i) => (
        <div
          key={i}
          style={{ marginBottom: i < policies.length - 1 ? 8 : 0, opacity: p.enabled ? 1 : 0.45 }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 3 }}>
            <code className={styles.policyCode}>{p.name}</code>
            {p.version && <span className={styles.policyMeta}>{p.version}</span>}
            {!p.enabled && <span className={styles.policyMeta}>(disabled)</span>}
          </div>
          {p.configuration && Object.keys(p.configuration).length > 0 && (
            <div style={{ paddingLeft: 12 }}>
              {Object.entries(p.configuration).map(([k, v]) => (
                <div key={k} className={styles.policyConfigLine}>
                  <span className={shared.mutedText}>{k}:</span>{' '}
                  <span style={{ fontFamily: 'monospace' }}>{renderConfigValue(v)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  );
};

export default PolicyPanel;
