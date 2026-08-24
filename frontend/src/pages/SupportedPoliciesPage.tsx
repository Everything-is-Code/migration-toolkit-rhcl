import React, { useState, useEffect } from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  Checkbox,
  Button,
  Alert,
  Spinner,
} from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import { settingsApi } from '../api/client';
import styles from '../styles/shared.module.css';
import {
  ALL_POLICIES,
  DEFAULT_SUPPORTED_POLICIES,
  SETTINGS_KEY,
  invalidateSupportedPoliciesCache,
  loadSupportedPolicies,
  seedSupportedPoliciesCache,
  withDefaultSupportedPolicies,
} from './supportedPolicies';

const SupportedPoliciesPage: React.FC = () => {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<string[]>(DEFAULT_SUPPORTED_POLICIES);
  const [loading, setLoading] = useState(true);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadSupportedPolicies().then(policies => {
      setSelected(policies);
      setLoading(false);
    });
  }, []);

  useEffect(() => { setSaved(false); }, [selected]);

  const toggle = (name: string) => {
    setSelected(prev =>
      prev.includes(name) ? prev.filter(p => p !== name) : [...prev, name]
    );
  };

  const handleSave = async () => {
    try {
      await settingsApi.put(SETTINGS_KEY, JSON.stringify(selected));
      // Drop any in-flight/stale GET, then seed cache with the saved list.
      invalidateSupportedPoliciesCache();
      seedSupportedPoliciesCache(withDefaultSupportedPolicies(selected));
      setSaved(true);
      setError(null);
    } catch {
      setError(t('supportedPolicies.saveError'));
    }
  };

  const handleReset = () => {
    setSelected(DEFAULT_SUPPORTED_POLICIES);
  };

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('supportedPolicies.title')}</Title>
        <p className={styles.pageDescription}>
          {t('supportedPolicies.description')}
        </p>
      </PageSection>
      <PageSection>
        {saved && (
          <Alert variant="success" title={t('supportedPolicies.saved')} style={{ marginBottom: '16px' }} />
        )}
        {error && (
          <Alert variant="danger" title={error} style={{ marginBottom: '16px' }} />
        )}
        <Card>
          <CardBody>
            {loading ? (
              <div className={styles.centeredBlock}>
                <Spinner size="xl" />
              </div>
            ) : (
              <>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '8px 24px', marginBottom: '24px' }}>
                  {ALL_POLICIES.map(name => (
                    <Checkbox
                      key={name}
                      id={`policy-${name}`}
                      label={name}
                      isChecked={selected.includes(name)}
                      onChange={() => toggle(name)}
                    />
                  ))}
                </div>
                <div className={styles.actionRow}>
                  <Button variant="primary" onClick={handleSave}>{t('supportedPolicies.btnSave')}</Button>
                  <Button variant="secondary" onClick={handleReset}>{t('supportedPolicies.btnReset')}</Button>
                </div>
              </>
            )}
          </CardBody>
        </Card>
      </PageSection>
    </>
  );
};

export default SupportedPoliciesPage;
