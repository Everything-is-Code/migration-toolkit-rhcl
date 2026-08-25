import React, { useState } from 'react';
import {
  Card,
  CardBody,
  Title,
  Button,
  Spinner,
  Label,
  TextInput,
} from '@patternfly/react-core';
import { CheckCircleIcon, CopyIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import styles from './import.module.css';
import type { TestInfo, RouteInfo } from './importUtils';
import { useGatewayUrl } from './useGatewayUrl';

interface Props {
  testInfo: TestInfo;
  namespace: string;
}

const TestInfoPanel: React.FC<Props> = ({ testInfo, namespace }) => {
  const { t } = useTranslation();
  const gw = useGatewayUrl(testInfo.gatewayName, namespace);
  const [copied, setCopied] = useState<string | null>(null);
  const [apiKeyValue, setApiKeyValue] = useState(testInfo.apiKey ?? 'your-api-key');
  const [jwtValue, setJwtValue] = useState('your-jwt-token');
  const [customPath, setCustomPath] = useState('');

  React.useEffect(() => {
    if (testInfo.apiKey) setApiKeyValue(testInfo.apiKey);
  }, [testInfo.apiKey]);

  const buildAuthHeader = (): string => {
    if (testInfo.auth.type === 'apiKey') {
      return `-H "${testInfo.auth.headerName ?? 'Authorization'}: ${testInfo.auth.prefix ?? 'APIKEY'} ${apiKeyValue}"`;
    }
    if (testInfo.auth.type === 'jwt') return `-H "Authorization: Bearer ${jwtValue}"`;
    return '';
  };

  const buildCurl = (route: RouteInfo): string => {
    const base = gw.url ?? 'http://<GATEWAY_URL>';
    const path = customPath.trim() || route.path;
    const auth = buildAuthHeader();
    const methodFlag = route.method !== 'GET' ? ` -X ${route.method}` : '';
    return `curl${methodFlag}${auth ? ' ' + auth : ''} "${base}${path}"`;
  };

  const copy = (text: string, key: string) => {
    navigator.clipboard.writeText(text).then(() => { setCopied(key); setTimeout(() => setCopied(null), 2000); });
  };

  const infoRow = (label: string, value: React.ReactNode) => (
    <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', marginBottom: 8 }}>
      <span className={styles.mutedLabel}>{label}</span>
      <span style={{ fontSize: 13, wordBreak: 'break-all' }}>{value}</span>
    </div>
  );

  return (
    <Card className={styles.testPanel}>
      <CardBody>
        <Title headingLevel="h3" size="md" className={styles.testPanelTitle}>{t('import.testPanel.title')}</Title>

        <div className={styles.panelBlock}>
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>{t('import.testPanel.gatewayUrl')}</div>
          {gw.loading && gw.phase === 'lb' && (
            <div className={styles.mutedText} style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, marginBottom: 8 }}>
              <Spinner size="sm" /> {t('import.testPanel.gwWaitingLb')}
            </div>
          )}
          {gw.loading && gw.phase === 'dns' && (
            <div className={styles.warningCallout}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, marginBottom: 4 }}>
                <Spinner size="sm" /> {t('import.testPanel.gwWaitingDns')}
              </div>
            </div>
          )}
          {gw.error && <div className={styles.dangerText} style={{ fontSize: 13, marginBottom: 8 }}>{gw.error}</div>}
          {!gw.loading && (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              {gw.url ? (
                <>
                  <code className={styles.codeChip}>{gw.url}</code>
                  <Button variant="plain" aria-label={t('import.testPanel.ariaCopy')} style={{ padding: 4 }}
                    onClick={() => copy(gw.url!, 'gwurl')}>
                    <CopyIcon /> {copied === 'gwurl' ? '✓' : ''}
                  </Button>
                </>
              ) : (
                <span className={styles.mutedText} style={{ fontSize: 13 }}>{gw.error ? '' : '—'}</span>
              )}
              <Button variant="link" onClick={gw.refetch} isDisabled={gw.loading} style={{ fontSize: 12 }}>
                {t('import.testPanel.refetch')}
              </Button>
            </div>
          )}
          {infoRow(t('import.testPanel.gatewayName'), <code style={{ fontSize: 12 }}>{testInfo.gatewayName}</code>)}
          {infoRow(t('import.testPanel.namespace'), <code style={{ fontSize: 12 }}>{namespace}</code>)}
        </div>

        <div className={styles.panelBlock}>
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>{t('import.testPanel.authTitle')}</div>
          {testInfo.auth.type === 'apiKey' && (
            <>
              {infoRow(t('import.testPanel.type'), <Label isCompact color="blue">API Key</Label>)}
              {infoRow(t('import.testPanel.header'), <code style={{ fontSize: 12 }}>{testInfo.auth.headerName}: {testInfo.auth.prefix} &lt;key&gt;</code>)}
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 8 }}>
                <span className={styles.mutedLabel}>{t('import.testPanel.apiKeyValue')}</span>
                <TextInput value={apiKeyValue} onChange={(_e, v) => setApiKeyValue(v)} placeholder="your-api-key" style={{ width: 280, fontSize: 13 }} />
              </div>
            </>
          )}
          {testInfo.auth.type === 'jwt' && (
            <>
              {infoRow(t('import.testPanel.type'), <Label isCompact color="purple">JWT (Bearer)</Label>)}
              {infoRow(t('import.testPanel.header'), <code style={{ fontSize: 12 }}>Authorization: Bearer &lt;token&gt;</code>)}
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 8 }}>
                <span className={styles.mutedLabel}>{t('import.testPanel.jwtToken')}</span>
                <TextInput value={jwtValue} onChange={(_e, v) => setJwtValue(v)} placeholder="your-jwt-token" style={{ width: 280, fontSize: 13 }} />
              </div>
            </>
          )}
          {testInfo.auth.type === 'none' && infoRow(t('import.testPanel.type'), <Label isCompact color="grey">{t('import.testPanel.authNone')}</Label>)}
        </div>

        <div className={styles.panelBlock}>
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 12 }}>{t('import.testPanel.curlTitle')}</div>
          {(gw.loading || !gw.url) ? (
            <div className={styles.mutedText} style={{ fontSize: 13, display: 'flex', gap: 8, alignItems: 'center' }}>
              {gw.loading
                ? <><Spinner size="sm" /> {gw.phase === 'dns' ? t('import.testPanel.gwWaitingDns') : t('import.testPanel.gwWaitingLb')}</>
                : t('import.testPanel.gwNotReady')}
            </div>
          ) : (
            <>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}>
                <span className={styles.mutedLabel}>{t('import.testPanel.customPath')}</span>
                <TextInput value={customPath} onChange={(_e, v) => setCustomPath(v)} placeholder="/api/your/path" style={{ flex: 1, fontSize: 13 }} />
              </div>
              {testInfo.routes.map((route, i) => {
                const cmd = buildCurl(route);
                const key = `curl-${i}`;
                return (
                  <div key={i} style={{ marginBottom: i < testInfo.routes.length - 1 ? 12 : 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                      <Label isCompact color="blue">{route.method}</Label>
                      <code className={styles.mutedText} style={{ fontSize: 12 }}>{route.path}</code>
                    </div>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                      <pre className={styles.curlPre}>{cmd}</pre>
                      <Button variant="plain" aria-label={t('import.testPanel.ariaCopy')} style={{ padding: 6, flexShrink: 0, marginTop: 2 }}
                        onClick={() => copy(cmd, key)}>
                        {copied === key ? <CheckCircleIcon color="var(--pf-v5-global--success-color--100)" /> : <CopyIcon />}
                      </Button>
                    </div>
                  </div>
                );
              })}
            </>
          )}
        </div>
      </CardBody>
    </Card>
  );
};

export default TestInfoPanel;
