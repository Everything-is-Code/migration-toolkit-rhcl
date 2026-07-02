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
} from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';

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

export const DEFAULT_SUPPORTED_POLICIES = ['3scale APIcast', 'Upstream Connection'];
export const STORAGE_KEY = 'supportedPolicies';

export function loadSupportedPolicies(): string[] {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) return JSON.parse(stored);
  } catch {}
  return DEFAULT_SUPPORTED_POLICIES;
}

const SupportedPoliciesPage: React.FC = () => {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<string[]>(loadSupportedPolicies);
  const [saved, setSaved] = useState(false);

  useEffect(() => { setSaved(false); }, [selected]);

  const toggle = (name: string) => {
    setSelected(prev =>
      prev.includes(name) ? prev.filter(p => p !== name) : [...prev, name]
    );
  };

  const handleSave = () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(selected));
    setSaved(true);
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
        <Card>
          <CardBody>
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
          </CardBody>
        </Card>
      </PageSection>
    </>
  );
};

export default SupportedPoliciesPage;
