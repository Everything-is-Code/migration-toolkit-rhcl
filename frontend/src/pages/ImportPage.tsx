import React, { useState, useRef, Component, ErrorInfo, ReactNode, useCallback } from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  Button,
  Alert,
  Spinner,
  Form,
  FormGroup,
  TextInput,
  Flex,
  FlexItem,
  Stack,
  StackItem,
  Label,
} from '@patternfly/react-core';
import {
  UploadIcon,
  CheckCircleIcon,
  TimesCircleIcon,
  DownloadIcon,
  PlayIcon,
  CopyIcon,
} from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import i18next from 'i18next';
import { importApi, downloadApi, applyApi, gatewayApi } from '../api/client';
import { fixHttpRoutePort } from '../utils/fixHttpRoutePort';
import { PF_SUCCESS } from '../styles/pfTokens';
import styles from './ImportPage.module.css';

interface YamlFile { name: string; content: string; }
type EditMap = Record<string, string>;
interface ApplyResult { fileName: string; success: boolean; message: string; }

/* ── YAML parser utility ── */
interface RouteInfo { path: string; method: string; }
interface AuthInfo {
  type: 'apiKey' | 'jwt' | 'none';
  prefix?: string;
  headerName?: string;
}
interface TestInfo {
  gatewayName: string;
  routes: RouteInfo[];
  auth: AuthInfo;
  apiKey?: string;
}

/**
 * Extract test info (Gateway name, routes, auth method) from YAML files.
 * Uses regex-based simple parsing since SnakeYAML is not available.
 */
function parseTestInfo(edits: EditMap): TestInfo {
  const gatewayYaml  = edits['gateway.yaml']  ?? '';
  const routeYaml    = edits['httproute.yaml'] ?? '';
  const policyYaml   = edits['policy.yaml']   ?? '';

  // Gateway name
  const gwNameMatch = gatewayYaml.match(/^  name:\s*(.+)$/m);
  const gatewayName = gwNameMatch ? gwNameMatch[1].trim() : '';

  // Extract path and method from HTTPRoute
  const routes: RouteInfo[] = [];
  const pathMatches   = Array.from(routeYaml.matchAll(/value:\s*"([^"]+)"/g));
  const methodMatches = Array.from(routeYaml.matchAll(/method:\s*(\w+)/g));
  pathMatches.forEach((pm, i) => {
    routes.push({
      path: pm[1],
      method: methodMatches[i]?.[1] ?? 'GET',
    });
  });
  if (routes.length === 0) routes.push({ path: '/', method: 'GET' });

  // Determine auth method from AuthPolicy
  let auth: AuthInfo = { type: 'none' };
  if (policyYaml.includes('jwt:') || policyYaml.includes('jwt-auth:')) {
    auth = { type: 'jwt', headerName: 'Authorization' };
  } else if (policyYaml.includes('apiKey:') || policyYaml.includes('api-key-auth:')) {
    const prefixMatch = policyYaml.match(/prefix:\s*(\w+)/);
    auth = {
      type: 'apiKey',
      prefix: prefixMatch ? prefixMatch[1] : 'APIKEY',
      headerName: 'Authorization',
    };
  }

  // Extract api_key value from secret.yaml (stringData or data)
  const secretYaml = edits['secret.yaml'] ?? '';
  const apiKeyMatch = secretYaml.match(/api_key:\s*"?([a-zA-Z0-9+/=_-]{8,})"?/);
  const apiKey = apiKeyMatch ? apiKeyMatch[1] : undefined;

  return { gatewayName, routes, auth, apiKey };
}

/* ── Test info panel ── */
interface TestInfoPanelProps {
  testInfo: TestInfo;
  namespace: string;
}
const TestInfoPanel: React.FC<TestInfoPanelProps> = ({ testInfo, namespace }) => {
  const { t } = useTranslation();
  const [gatewayUrl, setGatewayUrl] = useState<string | null>(null);
  const [gwLoading, setGwLoading]   = useState(false);
  const [gwError, setGwError]       = useState<string | null>(null);
  const [gwPhase, setGwPhase]       = useState<'lb' | 'dns' | 'done'>('lb');
  const [copied, setCopied]         = useState<string | null>(null);
  const [apiKeyValue, setApiKeyValue] = useState(testInfo.apiKey ?? 'your-api-key');
  const [jwtValue, setJwtValue]       = useState('your-jwt-token');

  // Follow secret.yaml api_key changes (but don't overwrite if manually entered)
  React.useEffect(() => {
    if (testInfo.apiKey) setApiKeyValue(testInfo.apiKey);
  }, [testInfo.apiKey]);
  const [customPath, setCustomPath]   = useState('');

  const fetchGatewayUrl = useCallback(async () => {
    if (!testInfo.gatewayName || !namespace) return;
    setGwLoading(true); setGwError(null); setGwPhase('lb');

    // Phase 1: Wait for LB address assignment (max 60 seconds)
    let hostname = '';
    for (let i = 0; i < 12; i++) {
      try {
        const res = await gatewayApi.getInfo(namespace, testInfo.gatewayName);
        if (res.data.ready) { hostname = res.data.hostname; break; }
      } catch (_e) { /* Gateway not yet created — retry */ }
      if (i < 11) await new Promise(r => setTimeout(r, 5000));
    }
    if (!hostname) {
      setGwError(t('import.testPanel.gwNotReady'));
      setGwLoading(false);
      return;
    }

    // Phase 2: Wait for DNS propagation (max 5 min, 10s interval)
    setGwPhase('dns');
    for (let i = 0; i < 30; i++) {
      try {
        const res = await gatewayApi.getInfo(namespace, testInfo.gatewayName);
        if (res.data.dnsReady) {
          setGatewayUrl(res.data.httpUrl);
          setGwPhase('done');
          setGwLoading(false);
          return;
        }
      } catch (_e) { /* retry */ }
      if (i < 29) await new Promise(r => setTimeout(r, 10000));
    }
    // Timeout — DNS not resolved yet but display URL anyway
    setGatewayUrl(`http://${hostname}`);
    setGwPhase('done');
    setGwLoading(false);
  }, [testInfo.gatewayName, namespace]);

  // Auto-fetch on mount
  React.useEffect(() => { fetchGatewayUrl(); }, [fetchGatewayUrl]);

  const buildAuthHeader = (): string => {
    if (testInfo.auth.type === 'apiKey') {
      return `-H "${testInfo.auth.headerName ?? 'Authorization'}: ${testInfo.auth.prefix ?? 'APIKEY'} ${apiKeyValue}"`;
    }
    if (testInfo.auth.type === 'jwt') {
      return `-H "Authorization: Bearer ${jwtValue}"`;
    }
    return '';
  };

  const buildCurl = (route: RouteInfo): string => {
    const base = gatewayUrl ?? 'http://<GATEWAY_URL>';
    const path = customPath.trim() || route.path;
    const auth = buildAuthHeader();
    const methodFlag = route.method !== 'GET' ? ` -X ${route.method}` : '';
    return `curl${methodFlag}${auth ? ' ' + auth : ''} "${base}${path}"`;
  };

  const copyToClipboard = (text: string, key: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(key);
      setTimeout(() => setCopied(null), 2000);
    });
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
        <Title headingLevel="h3" size="md" className={styles.testPanelTitle}>
          {t('import.testPanel.title')}
        </Title>

        {/* Gateway URL */}
        <div className={styles.panelBlock}>
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>{t('import.testPanel.gatewayUrl')}</div>

          {/* Phase-based status display */}
          {gwLoading && gwPhase === 'lb' && (
            <div className={styles.mutedText} style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, marginBottom: 8 }}>
              <Spinner size="sm" /> {t('import.testPanel.gwWaitingLb')}
            </div>
          )}
          {gwLoading && gwPhase === 'dns' && (
            <div className={styles.warningCallout}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, marginBottom: 4 }}>
                <Spinner size="sm" /> {t('import.testPanel.gwWaitingDns')}
              </div>
            </div>
          )}

          {gwError && <div className={styles.dangerText} style={{ fontSize: 13, marginBottom: 8 }}>{gwError}</div>}

          {!gwLoading && (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              {gatewayUrl ? (
                <>
                  <code className={styles.codeChip}>{gatewayUrl}</code>
                  <Button variant="plain" aria-label={t('import.testPanel.ariaCopy')} style={{ padding: 4 }}
                    onClick={() => copyToClipboard(gatewayUrl, 'gwurl')}>
                    <CopyIcon /> {copied === 'gwurl' ? '✓' : ''}
                  </Button>
                </>
              ) : (
                <span className={styles.mutedText} style={{ fontSize: 13 }}>{gwError ? '' : '—'}</span>
              )}
              <Button variant="link" onClick={fetchGatewayUrl} isDisabled={gwLoading} style={{ fontSize: 12 }}>
                {t('import.testPanel.refetch')}
              </Button>
            </div>
          )}
          {infoRow(t('import.testPanel.gatewayName'), <code style={{ fontSize: 12 }}>{testInfo.gatewayName}</code>)}
          {infoRow(t('import.testPanel.namespace'), <code style={{ fontSize: 12 }}>{namespace}</code>)}
        </div>

        {/* Authentication info */}
        <div className={styles.panelBlock}>
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>{t('import.testPanel.authTitle')}</div>
          {testInfo.auth.type === 'apiKey' && (
            <>
              {infoRow(t('import.testPanel.type'), <Label isCompact color="blue">API Key</Label>)}
              {infoRow(t('import.testPanel.header'), <code style={{ fontSize: 12 }}>{testInfo.auth.headerName}: {testInfo.auth.prefix} &lt;key&gt;</code>)}
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 8 }}>
                <span className={styles.mutedLabel}>{t('import.testPanel.apiKeyValue')}</span>
                <TextInput
                  value={apiKeyValue}
                  onChange={(_e, v) => setApiKeyValue(v)}
                  placeholder="your-api-key"
                  style={{ width: 280, fontSize: 13 }}
                />
              </div>
            </>
          )}
          {testInfo.auth.type === 'jwt' && (
            <>
              {infoRow(t('import.testPanel.type'), <Label isCompact color="purple">JWT (Bearer)</Label>)}
              {infoRow(t('import.testPanel.header'), <code style={{ fontSize: 12 }}>Authorization: Bearer &lt;token&gt;</code>)}
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 8 }}>
                <span className={styles.mutedLabel}>{t('import.testPanel.jwtToken')}</span>
                <TextInput
                  value={jwtValue}
                  onChange={(_e, v) => setJwtValue(v)}
                  placeholder="your-jwt-token"
                  style={{ width: 280, fontSize: 13 }}
                />
              </div>
            </>
          )}
          {testInfo.auth.type === 'none' && infoRow(t('import.testPanel.type'), <Label isCompact color="grey">{t('import.testPanel.authNone')}</Label>)}
        </div>

        {/* curl command — shown only after DNS resolution */}
        <div className={styles.panelBlock}>
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 12 }}>{t('import.testPanel.curlTitle')}</div>
          {(gwLoading || !gatewayUrl) ? (
            <div className={styles.mutedText} style={{ fontSize: 13, display: 'flex', gap: 8, alignItems: 'center' }}>
              {gwLoading
                ? <><Spinner size="sm" /> {gwPhase === 'dns' ? t('import.testPanel.gwWaitingDns') : t('import.testPanel.gwWaitingLb')}</>
                : t('import.testPanel.gwNotReady')}
            </div>
          ) : (
            <>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}>
                <span className={styles.mutedLabel}>{t('import.testPanel.customPath')}</span>
                <TextInput
                  value={customPath}
                  onChange={(_e, v) => setCustomPath(v)}
                  placeholder="/api/your/path"
                  style={{ flex: 1, fontSize: 13 }}
                />
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
                      <pre className={styles.curlPre}>
                        {cmd}
                      </pre>
                      <Button variant="plain" aria-label={t('import.testPanel.ariaCopy')} style={{ padding: 6, flexShrink: 0, marginTop: 2 }}
                        onClick={() => copyToClipboard(cmd, key)}>
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

/* ── Error Boundary ── */
class ErrorBoundary extends Component<{ children: ReactNode }, { hasError: boolean; msg: string }> {
  state = { hasError: false, msg: '' };
  static getDerivedStateFromError(e: Error) { return { hasError: true, msg: e.message }; }
  componentDidCatch(e: Error, i: ErrorInfo) { console.error('[ImportPage]', e, i); }
  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 32 }}>
          <div className={styles.errorBanner}>
            <p className={styles.errorBannerTitle}>{i18next.t('import.errorTitle')}</p>
            <pre style={{ marginTop: 8, fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{this.state.msg}</pre>
            <button onClick={() => this.setState({ hasError: false, msg: '' })} className={styles.errorRetry}>
              {i18next.t('import.btnRetry')}
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

/* ── Simple tabs (without Tabs component) ── */
// ── Simple line diff ────────────────────────────────────────────────────
type DiffLine = { type: 'same' | 'add' | 'remove'; text: string };

function computeDiff(original: string, current: string): DiffLine[] {
  const oldLines = original.split('\n');
  const newLines = current.split('\n');

  // LCS DP table
  const m = oldLines.length;
  const n = newLines.length;
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      if (oldLines[i] === newLines[j]) {
        dp[i][j] = dp[i + 1][j + 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }
  }

  const result: DiffLine[] = [];
  let i = 0, j = 0;
  while (i < m || j < n) {
    if (i < m && j < n && oldLines[i] === newLines[j]) {
      result.push({ type: 'same', text: oldLines[i] });
      i++; j++;
    } else if (j < n && (i >= m || dp[i][j + 1] >= dp[i + 1][j])) {
      result.push({ type: 'add', text: newLines[j] });
      j++;
    } else {
      result.push({ type: 'remove', text: oldLines[i] });
      i++;
    }
  }
  return result;
}

interface SimpleTabs {
  files: YamlFile[];
  edits: EditMap;
  onEdit: (name: string, val: string) => void;
}
const SimpleYamlTabs: React.FC<SimpleTabs> = ({ files, edits, onEdit }) => {
  const { t } = useTranslation();
  const [active, setActive] = useState(0);
  const [mode, setMode] = useState<'view' | 'edit' | 'diff'>('view');

  if (files.length === 0) return null;
  const current = files[active] ?? files[0];
  const content = edits[current.name] ?? current.content;
  const isEdited = edits[current.name] !== undefined && edits[current.name] !== current.content;

  const diffLines = mode === 'diff' ? computeDiff(current.content, content) : [];

  const panelId = 'yaml-panel';

  return (
    <div>
      {/* Tab header */}
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

      {/* Mode toggle buttons */}
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

      {/* Content */}
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

/* ── Main component ── */
const ImportPageInner: React.FC = () => {
  const { t } = useTranslation();
  const fileRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver]         = useState(false);
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState<string | null>(null);
  const [files, setFiles]               = useState<YamlFile[]>([]);
  const [edits, setEdits]               = useState<EditMap>({});
  const [baseEdits, setBaseEdits]       = useState<EditMap>({});
  const [namespace, setNamespace]       = useState('default');
  const [packageName, setPackageName]   = useState('');
  const [pkgNameError, setPkgNameError] = useState(false);
  const [uploadedName, setUploadedName] = useState('');
  const [downloaded, setDownloaded]     = useState(false);
  const [applying, setApplying]         = useState(false);
  const [applyResults, setApplyResults] = useState<ApplyResult[] | null>(null);
  const [testInfo, setTestInfo]         = useState<TestInfo | null>(null);
  const [portFixNotice, setPortFixNotice] = useState<string | null>(null);

  const reset = () => {
    setFiles([]); setEdits({}); setBaseEdits({}); setUploadedName('');
    setApplyResults(null); setError(null); setDownloaded(false);
    setTestInfo(null); setPortFixNotice(null);
    setPackageName(''); setPkgNameError(false);
  };

  /**
   * External backend detection:
   *   Returns true if serviceentry.yaml is included, or
   *   any YAML contains type: ExternalName / kind: ServiceEntry
   */
  const detectExternalBackend = (editMap: EditMap): boolean => {
    if ('serviceentry.yaml' in editMap) return true;
    return Object.values(editMap).some(
      yaml => /type:\s*ExternalName/i.test(yaml) || /kind:\s*ServiceEntry/i.test(yaml)
    );
  };

  const handleFile = async (file: File) => {
    if (!file.name.toLowerCase().endsWith('.zip')) {
      setError(t('import.errorZipOnly')); return;
    }
    setLoading(true); setError(null); reset();
    setUploadedName(file.name);
    const defaultPkg = file.name.replace(/\.zip$/i, '');
    setPackageName(defaultPkg);
    setPkgNameError(false);
    try {
      const res = await importApi.uploadZip(file);
      const yamlMap: Record<string, string> = res.data?.files ?? {};
      if (Object.keys(yamlMap).length === 0) {
        setError(t('import.errorNoYaml')); setLoading(false); return;
      }
      const loaded = Object.entries(yamlMap)
        .map(([name, content]) => ({ name, content: String(content) }))
        .sort((a, b) => a.name.localeCompare(b.name));
      const init: EditMap = {};
      loaded.forEach(f => { init[f.name] = f.content; });
      setBaseEdits(init);
      const derived = deriveEdits(init, defaultPkg, namespace);
      setFiles(loaded); setEdits(derived);
      if (detectExternalBackend(derived)) {
        setPortFixNotice('portFixExternal');
      }
    } catch (e: any) {
      setError(t('import.errorUpload', { message: e.response?.data?.error ?? e.message }));
    } finally { setLoading(false); }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault(); setDragOver(false);
    const f = e.dataTransfer.files[0]; if (f) handleFile(f);
  };

  const normalizeApiVersions = (yaml: string) => yaml
    .replace(/apiVersion: kuadrant\.io\/v1beta2/g, 'apiVersion: kuadrant.io/v1')
    .replace(/apiVersion: kuadrant\.io\/v1beta1/g, 'apiVersion: kuadrant.io/v1')
    .replace(/apiVersion: gateway\.networking\.k8s\.io\/v1beta1/g, 'apiVersion: gateway.networking.k8s.io/v1');

  // Replace "api" prefix in YAML with package name
  const applyPkgToYaml = (yaml: string, pkg: string): string => {
    if (!pkg) return yaml;
    return yaml
      // name: api-xxx → name: {pkg}-xxx  /  name: api → name: {pkg}
      .replace(/^(\s+name:\s+)api(-|(?=\s*$))/gm, `$1${pkg}$2`)
      // app: api-xxx → app: {pkg}-xxx  /  app: api → app: {pkg}
      .replace(/^(\s+app:\s+)api(-|(?=\s*$))/gm, `$1${pkg}$2`)
      // service-name: "API" or service-name: API
      .replace(/^(\s+service-name:\s*)["']?API["']?/gm, `$1"${pkg.toUpperCase()}"`);
  };

  // Regenerate edits by applying namespace + package name to baseEdits
  const deriveEdits = useCallback((base: EditMap, pkg: string, ns: string): EditMap => {
    const updated: EditMap = {};
    for (const [name, content] of Object.entries(base)) {
      let yaml = normalizeApiVersions(content);
      yaml = yaml.replace(/^(\s*namespace:\s*).+$/gm, `$1${ns}`);
      yaml = applyPkgToYaml(yaml, pkg);
      updated[name] = yaml;
    }
    return updated;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const applyNamespace = () => {
    const isExternal = detectExternalBackend(edits);
    const updated: EditMap = {};
    let portConverted = false;

    files.forEach(f => {
      let yaml = normalizeApiVersions(edits[f.name] ?? f.content)
        .replace(/^(\s*namespace:\s*).+$/gm, `$1${namespace}`);

      // If external backend detected, convert HTTPRoute backendRefs.port to 443
      if (isExternal && f.name === 'httproute.yaml') {
        const fixed = fixHttpRoutePort(yaml);
        if (fixed !== yaml) { portConverted = true; yaml = fixed; }
      }

      updated[f.name] = yaml;
    });

    setEdits(updated);
    if (portConverted) {
      setPortFixNotice('portFixed443');
    } else if (isExternal) {
      setPortFixNotice('portAlready443');
    } else {
      setPortFixNotice(null);
    }
  };

  const handlePackageNameChange = (newPkg: string) => {
    setPackageName(newPkg);
    setPkgNameError(newPkg.trim() === '');
    if (Object.keys(baseEdits).length > 0) {
      setEdits(deriveEdits(baseEdits, newPkg, namespace));
    }
  };

  const handleDownload = async () => {
    if (!packageName.trim()) { setPkgNameError(true); return; }
    const yamlFiles: Record<string, string> = {};
    files.forEach(f => { yamlFiles[f.name] = edits[f.name] ?? f.content; });
    try {
      const resp = await downloadApi.downloadZip(packageName, yamlFiles);
      const url = URL.createObjectURL(new Blob([resp.data], { type: 'application/zip' }));
      const a = document.createElement('a');
      a.href = url; a.download = `${packageName}.zip`;
      a.click(); URL.revokeObjectURL(url);
      setDownloaded(true);
    } catch (e: any) { setError(t('import.errorDownload', { message: e.message })); }
  };

  const handleApply = async () => {
    if (!packageName.trim()) { setPkgNameError(true); return; }
    setApplying(true); setApplyResults(null); setError(null); setTestInfo(null);
    const yamlFiles: Record<string, string> = {};
    files.forEach(f => { yamlFiles[f.name] = edits[f.name] ?? f.content; });
    try {
      const res = await applyApi.apply(namespace, yamlFiles, 'IMPORT', packageName || undefined);
      const results: ApplyResult[] = res.data?.results ?? [];
      setApplyResults(results);
      // Show test info panel if at least one apply succeeded
      if (results.some(r => r.success)) {
        setTestInfo(parseTestInfo(edits));
      }
    } catch (e: any) {
      const data = e.response?.data;
      if (Array.isArray(data?.results)) {
        setApplyResults(data.results);
        if (data.results.some((r: ApplyResult) => r.success)) {
          setTestInfo(parseTestInfo(edits));
        }
      } else {
        setError(t('import.errorApply', { message: data?.error ?? e.message }));
      }
    } finally { setApplying(false); }
  };

  const successCount = applyResults?.filter(r => r.success).length ?? 0;
  const errorCount   = applyResults?.filter(r => !r.success).length ?? 0;

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('import.title')}</Title>
        <p className={styles.pageDescription}>{t('import.description')}</p>
      </PageSection>

      <PageSection>
        <Stack hasGutter>

          {error && (
            <StackItem>
              <Alert variant="danger" title={error} isInline
                actionClose={<Button variant="plain" onClick={() => setError(null)}>×</Button>} />
            </StackItem>
          )}

          {/* ── Upload zone ── */}
          {files.length === 0 && (
            <StackItem>
              <Card>
                <CardBody>
                  <div
                    onDragOver={e => { e.preventDefault(); setDragOver(true); }}
                    onDragLeave={() => setDragOver(false)}
                    onDrop={handleDrop}
                    onClick={() => !loading && fileRef.current?.click()}
                    className={`${styles.dropZone}${dragOver ? ` ${styles.isDragOver}` : ''}`}
                    style={{ cursor: loading ? 'default' : 'pointer' }}
                  >
                    {loading ? (
                      <><Spinner size="lg" /><p style={{ marginTop: 16 }}>{t('import.analyzing')}</p></>
                    ) : (
                      <>
                        <UploadIcon className={styles.mutedText} style={{ fontSize: '3rem' }} />
                        <p style={{ marginTop: 16, fontSize: '1.1rem', fontWeight: 500 }}>
                          {t('import.dropZone')}
                        </p>
                        <p className={styles.mutedText} style={{ marginTop: 8 }}>{t('import.orClick')}</p>
                        <Button variant="primary" style={{ marginTop: 16 }}
                          onClick={e => { e.stopPropagation(); fileRef.current?.click(); }}>
                          {t('import.btnSelectFile')}
                        </Button>
                      </>
                    )}
                    <input ref={fileRef} type="file" accept=".zip" style={{ display: 'none' }}
                      onChange={e => { const f = e.target.files?.[0]; if (f) handleFile(f); e.target.value = ''; }} />
                  </div>
                </CardBody>
              </Card>
            </StackItem>
          )}

          {/* ── After file load ── */}
          {files.length > 0 && (
            <>
              {/* File info bar */}
              <StackItem>
                <Card>
                  <CardBody>
                    <Flex alignItems={{ default: 'alignItemsCenter' }}>
                      <FlexItem>
                        <CheckCircleIcon color="var(--pf-v5-global--success-color--100)" />
                        {' '}<strong>{uploadedName}</strong>{' — '}
                        <Label isCompact color="blue">{t('import.fileCount', { count: files.length })}</Label>
                      </FlexItem>
                      <FlexItem align={{ default: 'alignRight' }}>
                        <Button variant="link" onClick={reset}>{t('import.btnUploadAnother')}</Button>
                      </FlexItem>
                    </Flex>
                  </CardBody>
                </Card>
              </StackItem>

              {/* Namespace & Apply */}
              <StackItem>
                <Card>
                  <CardBody>
                    <Title headingLevel="h3" size="md" style={{ marginBottom: 12 }}>
                      {t('import.namespaceSection')}
                    </Title>
                    <Form isHorizontal>
                      <FormGroup label={<>{t('import.labelPackageName')}<span className={styles.requiredMark}>*</span></>} fieldId="imp-pkg">
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                          <TextInput
                            id="imp-pkg"
                            value={packageName}
                            onChange={(_e, v) => handlePackageNameChange(v)}
                            placeholder={t('import.pkgNamePlaceholder')}
                            className={pkgNameError ? styles.inputError : undefined}
                            style={{ width: 260 }}
                            aria-invalid={pkgNameError}
                          />
                          {pkgNameError ? (
                            <span className={styles.fieldError}>{t('import.pkgNameRequired')}</span>
                          ) : (
                            <span className={styles.fieldHint}>{t('import.pkgNameHint')}</span>
                          )}
                        </div>
                      </FormGroup>
                      <FormGroup label={t('import.labelNamespace')} fieldId="imp-ns">
                        <Flex>
                          <FlexItem>
                            <TextInput id="imp-ns" value={namespace}
                              onChange={(_e, v) => setNamespace(v)}
                              placeholder="default" style={{ width: 260 }} />
                          </FlexItem>
                          <FlexItem>
                            <Button variant="secondary" onClick={applyNamespace}>
                              {t('import.btnApplyNamespace')}
                            </Button>
                          </FlexItem>
                        </Flex>
                      </FormGroup>
                    </Form>
                    {portFixNotice && (
                      <div className={`${styles.portNotice}${portFixNotice === 'portFixed443' ? ` ${styles.isSuccess}` : ''}`}>
                        {portFixNotice === 'portFixed443'
                          ? <CheckCircleIcon color={PF_SUCCESS} />
                          : <span style={{ fontWeight: 700 }}>ℹ</span>}
                        {t(`import.${portFixNotice}`)}
                      </div>
                    )}
                    <div style={{ marginTop: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <Button variant="primary"
                        icon={applying ? <Spinner size="sm" /> : <PlayIcon />}
                        onClick={handleApply} isDisabled={applying}>
                        {applying ? t('import.btnApplying') : t('import.btnApplyOc', { namespace })}
                      </Button>
                      <Button variant="secondary" icon={<DownloadIcon />} onClick={handleDownload}>
                        {t('import.btnDownloadZip')}
                      </Button>
                    </div>
                  </CardBody>
                </Card>
              </StackItem>

              {/* Apply results */}
              {applyResults && (
                <StackItem>
                  <Card>
                    <CardBody>
                      <Title headingLevel="h3" size="md" style={{ marginBottom: 12 }}>
                        {t('import.resultTitle')}
                        {' '}<Label isCompact color="green">{t('import.successCount', { count: successCount })}</Label>
                        {errorCount > 0 && <>{' '}<Label isCompact color="red">{t('import.failCount', { count: errorCount })}</Label></>}
                      </Title>
                      {errorCount === 0 && <Alert variant="success" isInline style={{ marginBottom: 12 }}
                        title={t('import.allSuccessAlert', { count: successCount, namespace })} />}
                      {errorCount > 0 && successCount === 0 && <Alert variant="danger" isInline style={{ marginBottom: 12 }}
                        title={t('import.allFailAlert')} />}
                      {errorCount > 0 && successCount > 0 && <Alert variant="warning" isInline style={{ marginBottom: 12 }}
                        title={t('import.partialAlert', { success: successCount, error: errorCount })} />}
                      <div style={{ overflowX: 'auto' }}>
                        <table className={styles.resultsTable}>
                          <caption style={{ textAlign: 'left', padding: '0 0 8px', fontWeight: 600 }}>
                            {t('import.resultTitle')}
                          </caption>
                          <thead>
                            <tr className={styles.resultsHeader}>
                              <th scope="col" className={styles.resultsTh}>{t('import.colFileName')}</th>
                              <th scope="col" className={styles.resultsTh} style={{ width: 90 }}>{t('import.colResult')}</th>
                              <th scope="col" className={styles.resultsTh}>{t('import.colMessage')}</th>
                            </tr>
                          </thead>
                          <tbody>
                            {applyResults.map(r => (
                              <tr key={r.fileName} className={styles.resultsRow}>
                                <td style={{ padding: '8px 12px', fontFamily: 'monospace' }}>{r.fileName}</td>
                                <td style={{ padding: '8px 12px', whiteSpace: 'nowrap' }}>
                                  {r.success
                                    ? <CheckCircleIcon color="var(--pf-v5-global--success-color--100)" />
                                    : <TimesCircleIcon color="var(--pf-v5-global--danger-color--100)" />}
                                  {' '}{r.success ? t('import.resultSuccess') : t('import.resultFail')}
                                </td>
                                <td className={r.success ? styles.resultsOk : styles.resultsFail} style={{ padding: '8px 12px', fontSize: 12, wordBreak: 'break-word', maxWidth: 500 }}>
                                  {r.message}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </CardBody>
                  </Card>
                </StackItem>
              )}

              {/* Test info panel (shown after successful apply) */}
              {testInfo && (
                <StackItem>
                  <TestInfoPanel testInfo={testInfo} namespace={namespace} />
                </StackItem>
              )}

              {/* YAML viewer (custom tabs) */}
              <StackItem>
                <Card>
                  <CardBody>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexWrap: 'wrap', gap: 8 }}>
                      <Title headingLevel="h3" size="md">{t('import.yamlEditorTitle')}</Title>
                      <span />
                    </div>
                    {downloaded && <Alert variant="success" isInline title={t('import.downloadSuccess')} style={{ marginBottom: 12 }} />}
                    <SimpleYamlTabs files={files} edits={edits}
                      onEdit={(name, val) => setEdits(prev => ({ ...prev, [name]: val }))} />
                  </CardBody>
                </Card>
              </StackItem>

              {/* Manual apply steps */}
              <StackItem>
                <Card>
                  <CardBody>
                    <Title headingLevel="h3" size="md" style={{ marginBottom: 12 }}>
                      {t('import.manualStepsTitle')}
                    </Title>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 14 }}>
                      <div><strong>{t('import.step1Term')}</strong> — {t('import.step1Desc')}</div>
                      <div><strong>{t('import.step2Term')}</strong> — <span dangerouslySetInnerHTML={{ __html: t('import.step2Desc') }} /></div>
                      <div><strong>{t('import.step3Term')}</strong> — <code>oc apply -n {namespace} -f ./</code></div>
                    </div>
                  </CardBody>
                </Card>
              </StackItem>
            </>
          )}
        </Stack>
      </PageSection>
    </>
  );
};

const ImportPage: React.FC = () => (
  <ErrorBoundary><ImportPageInner /></ErrorBoundary>
);

export default ImportPage;
