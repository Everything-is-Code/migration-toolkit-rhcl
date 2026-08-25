import React, { useState, Component, ErrorInfo, ReactNode } from 'react';
import { apiErrorMessage } from '../utils/apiError';
import { PageSection, PageSectionVariants, Title, Card, CardBody, Button, Alert, Stack, StackItem } from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import i18next from 'i18next';
import { importApi, downloadApi, applyApi } from '../api/client';
import { fixHttpRoutePort } from '../utils/fixHttpRoutePort';
import styles from './ImportPage.module.css';
import type { YamlFile, EditMap, ApplyResult, TestInfo } from '../components/import/importUtils';
import { parseTestInfo, detectExternalBackend, normalizeApiVersions, deriveEdits } from '../components/import/importUtils';
import TestInfoPanel from '../components/import/TestInfoPanel';
import YamlDropzone from '../components/import/YamlDropzone';
import YamlDiffViewer from '../components/import/YamlDiffViewer';
import ImportResultTable from '../components/import/ImportResultTable';
import NamespaceFormCard from '../components/import/NamespaceFormCard';
import FileInfoBar from '../components/import/FileInfoBar';
import ManualSteps from '../components/import/ManualSteps';

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

const ImportPageInner: React.FC = () => {
  const { t } = useTranslation();
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
    setTestInfo(null); setPortFixNotice(null); setPackageName(''); setPkgNameError(false);
  };

  const handleFile = async (file: File) => {
    if (!file.name.toLowerCase().endsWith('.zip')) { setError(t('import.errorZipOnly')); return; }
    setLoading(true); setError(null); reset();
    setUploadedName(file.name);
    const defaultPkg = file.name.replace(/\.zip$/i, '');
    setPackageName(defaultPkg); setPkgNameError(false);
    try {
      const res = await importApi.uploadZip(file);
      const yamlMap: Record<string, string> = res.data?.files ?? {};
      if (Object.keys(yamlMap).length === 0) { setError(t('import.errorNoYaml')); setLoading(false); return; }
      const loaded = Object.entries(yamlMap)
        .map(([name, content]) => ({ name, content: String(content) }))
        .sort((a, b) => a.name.localeCompare(b.name));
      const init: EditMap = {};
      loaded.forEach(f => { init[f.name] = f.content; });
      setBaseEdits(init);
      const derived = deriveEdits(init, defaultPkg, namespace);
      setFiles(loaded); setEdits(derived);
      if (detectExternalBackend(derived)) setPortFixNotice('portFixExternal');
    } catch (e: unknown) {
      setError(t('import.errorUpload', { message: apiErrorMessage(e, 'Upload failed') }));
    } finally { setLoading(false); }
  };

  const applyNamespace = () => {
    const isExternal = detectExternalBackend(edits);
    const updated: EditMap = {};
    let portConverted = false;
    files.forEach(f => {
      let yaml = normalizeApiVersions(edits[f.name] ?? f.content)
        .replace(/^(\s*namespace:\s*).+$/gm, `$1${namespace}`);
      if (isExternal && f.name === 'httproute.yaml') {
        const fixed = fixHttpRoutePort(yaml);
        if (fixed !== yaml) { portConverted = true; yaml = fixed; }
      }
      updated[f.name] = yaml;
    });
    setEdits(updated);
    if (portConverted) setPortFixNotice('portFixed443');
    else if (isExternal) setPortFixNotice('portAlready443');
    else setPortFixNotice(null);
  };

  const handlePackageNameChange = (newPkg: string) => {
    setPackageName(newPkg); setPkgNameError(newPkg.trim() === '');
    if (Object.keys(baseEdits).length > 0) setEdits(deriveEdits(baseEdits, newPkg, namespace));
  };

  const handleDownload = async () => {
    if (!packageName.trim()) { setPkgNameError(true); return; }
    const yamlFiles: Record<string, string> = {};
    files.forEach(f => { yamlFiles[f.name] = edits[f.name] ?? f.content; });
    try {
      const resp = await downloadApi.downloadZip(packageName, yamlFiles);
      const url = URL.createObjectURL(new Blob([resp.data], { type: 'application/zip' }));
      const a = document.createElement('a');
      a.href = url; a.download = `${packageName}.zip`; a.click(); URL.revokeObjectURL(url);
      setDownloaded(true);
    } catch (e: unknown) { setError(t('import.errorDownload', { message: apiErrorMessage(e, 'Download failed') })); }
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
      if (results.some(r => r.success)) setTestInfo(parseTestInfo(edits));
    } catch (e: unknown) {
      const data = (e && typeof e === 'object' && 'response' in e)
        ? (e as { response?: { data?: { results?: ApplyResult[]; error?: string } } }).response?.data
        : undefined;
      if (Array.isArray(data?.results)) {
        setApplyResults(data.results);
        if (data.results.some((r: ApplyResult) => r.success)) setTestInfo(parseTestInfo(edits));
      } else {
        setError(t('import.errorApply', { message: apiErrorMessage(e, 'Apply failed') }));
      }
    } finally { setApplying(false); }
  };

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
          {files.length === 0 && (
            <StackItem><YamlDropzone loading={loading} onFileSelected={handleFile} /></StackItem>
          )}
          {files.length > 0 && (
            <>
              <StackItem><FileInfoBar uploadedName={uploadedName} fileCount={files.length} onReset={reset} /></StackItem>
              <StackItem>
                <NamespaceFormCard
                  namespace={namespace} packageName={packageName}
                  pkgNameError={pkgNameError} applying={applying} portFixNotice={portFixNotice}
                  onNamespaceChange={setNamespace} onPackageNameChange={handlePackageNameChange}
                  onApplyNamespace={applyNamespace} onApply={handleApply} onDownload={handleDownload}
                />
              </StackItem>
              {applyResults && (
                <StackItem><ImportResultTable results={applyResults} namespace={namespace} /></StackItem>
              )}
              {testInfo && (
                <StackItem><TestInfoPanel testInfo={testInfo} namespace={namespace} /></StackItem>
              )}
              <StackItem>
                <Card><CardBody>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexWrap: 'wrap', gap: 8 }}>
                    <Title headingLevel="h3" size="md">{t('import.yamlEditorTitle')}</Title>
                    <span />
                  </div>
                  {downloaded && <Alert variant="success" isInline title={t('import.downloadSuccess')} style={{ marginBottom: 12 }} />}
                  <YamlDiffViewer files={files} edits={edits}
                    onEdit={(name, val) => setEdits(prev => ({ ...prev, [name]: val }))} />
                </CardBody></Card>
              </StackItem>
              <StackItem><ManualSteps namespace={namespace} /></StackItem>
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
