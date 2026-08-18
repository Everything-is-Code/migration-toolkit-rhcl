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

export const ALL_POLICIES = [
  '3scale APIcast',
  '3scale Auth Caching',
  '3scale Batcher',
  '3scale Referrer',
  'Anonymous Access',
  'Camel Service',
  'Conditional Policy',
  'Content Caching',
  'CORS Request Handling',
  'Custom metrics',
  'Echo',
  'Edge Limiting',
  'Header Modification',
  'IP Check',
  'JWT Claim Check',
  'Liquid Context Debug',
  'Logging',
  'Maintenance Mode',
  'OAuth 2.0 Mutual TLS Client Authentication',
  'OAuth 2.0 Token Introspection',
  'Proxy Service',
  'Rate Limit Headers',
  'Response/Request Content Limits',
  'Retry',
  'RH-SSO/Keycloak Role Check',
  'Routing',
  'SOAP',
  'TLS Client Certificate Validation',
  'TLS Termination',
  'Upstream',
  'Upstream Connection',
  'Upstream Mutual TLS',
  'URL Rewriting',
  'URL Rewriting with Captures',
];

export const DEFAULT_SUPPORTED_POLICIES = ['3scale APIcast', 'Header Modification', 'Upstream Connection', 'Logging', 'Anonymous Access', 'URL Rewriting', '3scale Auth Caching', 'CORS Request Handling', 'IP Check', 'Edge Limiting', 'OAuth 2.0 Token Introspection', 'JWT Claim Check', 'Response/Request Content Limits', 'Retry', 'RH-SSO/Keycloak Role Check'];
const SETTINGS_KEY = 'supportedPolicies';

export async function loadSupportedPolicies(): Promise<string[]> {
  try {
    const resp = await settingsApi.get(SETTINGS_KEY);
    return JSON.parse(resp.data.value);
  } catch {
    return DEFAULT_SUPPORTED_POLICIES;
  }
}

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
        <p style={{ marginTop: '8px', color: '#6a6e73' }}>
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
              <div style={{ textAlign: 'center', padding: '40px' }}>
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
                <div style={{ display: 'flex', gap: '8px' }}>
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
