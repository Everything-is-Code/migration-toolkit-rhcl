import React, { useState, useEffect, useCallback } from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Card,
  CardBody,
  Form,
  FormGroup,
  TextInput,
  Button,
  Alert,
  AlertVariant,
  Spinner,
  ActionGroup,
  InputGroup,
  InputGroupItem,
  FormHelperText,
  HelperText,
  HelperTextItem,
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
  FormSelect,
  FormSelectOption,
  Label,
} from '@patternfly/react-core';
import { CheckCircleIcon, EyeIcon, EyeSlashIcon, SyncAltIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { connectionApi, clusterApi, defaultsApi, settingsApi } from '../api/client';
import { ClusterProfile, ClusterVersionsResponse } from '../api/types';
import { AppState } from '../App';
import { useNavigate } from 'react-router-dom';
import { clusterProfileI18nKey, shouldShowClusterVersionsCard } from './clusterCapabilityUi';
import { clearPersistedConnection } from '../utils/appStateStorage';
import {
  PF_COLOR_MUTED,
  PF_FONT_SIZE_SM,
  PF_SPACER_MD,
  PF_SPACER_SM,
} from '../styles/pfTokens';

interface Props {
  appState: AppState;
  setAppState: React.Dispatch<React.SetStateAction<AppState>>;
}

const PROFILE_OPTIONS: ClusterProfile[] = ['auto', 'ocp-4.19', 'ocp-4.21'];

const displayOrDash = (value: string | null | undefined) =>
  value && value.trim() ? value : '—';

/** Narrow unknown catch values from axios / Error (I9). */
function apiErrorMessage(e: unknown, fallback: string): string {
  if (e && typeof e === 'object') {
    const err = e as {
      message?: string;
      response?: { data?: { error?: string; message?: string } };
    };
    return err.response?.data?.error || err.response?.data?.message || err.message || fallback;
  }
  if (typeof e === 'string' && e.trim()) {
    return e;
  }
  return fallback;
}

const ConnectionPage: React.FC<Props> = ({ appState, setAppState }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [url, setUrl] = useState(appState.connection.url);
  const [accessToken, setAccessToken] = useState(appState.connection.accessToken);
  const [tenant, setTenant] = useState(appState.connection.tenant || '');
  const [namespace, setNamespace] = useState(appState.namespace);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(appState.connection.connected);
  const [showToken, setShowToken] = useState(false);
  const [fetchingDomain, setFetchingDomain] = useState(false);
  const [domainError, setDomainError] = useState<string | null>(null);
  const [clusterDomain, setClusterDomain] = useState<string | null>(null);
  const [defaultsLoaded, setDefaultsLoaded] = useState(false);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [versionsError, setVersionsError] = useState<string | null>(null);
  const [profileSaving, setProfileSaving] = useState(false);

  // Re-sync local form when returning to this page with a saved connection.
  useEffect(() => {
    if (!appState.connection.connected) return;
    setUrl(appState.connection.url);
    setAccessToken(appState.connection.accessToken);
    setTenant(appState.connection.tenant || '');
    setNamespace(appState.namespace);
    setSuccess(true);
  }, [
    appState.connection.connected,
    appState.connection.url,
    appState.connection.accessToken,
    appState.connection.tenant,
    appState.namespace,
  ]);

  const applyVersions = useCallback((versions: ClusterVersionsResponse) => {
    setAppState(prev => ({
      ...prev,
      clusterVersions: versions,
      clusterProfile: versions.profile || prev.clusterProfile,
    }));
  }, [setAppState]);

  const loadVersions = useCallback(async (refresh = false) => {
    setVersionsLoading(true);
    setVersionsError(null);
    try {
      const res = await clusterApi.getVersions(refresh);
      applyVersions(res.data);
    } catch (e: unknown) {
      setVersionsError(apiErrorMessage(e, 'Failed to load cluster versions'));
    } finally {
      setVersionsLoading(false);
    }
  }, [applyVersions]);

  useEffect(() => {
    if (defaultsLoaded || appState.connection.connected) return;
    defaultsApi.get().then((res) => {
      const cfg = res.data?.threescale;
      if (cfg?.configured) {
        if (cfg.url && !url) setUrl(cfg.url);
        if (cfg.token && !accessToken) setAccessToken(cfg.token);
      }
    }).catch(() => {
      // Defaults endpoint unavailable — fields stay empty
    }).finally(() => setDefaultsLoaded(true));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Only load versions on mount when already connected from a previous session
  useEffect(() => {
    if (appState.connection.connected) {
      loadVersions(false);
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const buildUrlFromNamespace = (ns: string, domain: string) =>
    `https://${ns}-admin.${domain}`;

  const handleNamespaceChange = (val: string) => {
    setNamespace(val);
    if (clusterDomain && val.trim()) {
      setUrl(buildUrlFromNamespace(val.trim(), clusterDomain));
    }
  };

  const handleFetchDomain = async () => {
    setFetchingDomain(true);
    setDomainError(null);
    try {
      const res = await clusterApi.getDomain();
      const domain = res.data?.domain;
      const detectedNs = (res.data as any)?.namespace;
      if (domain) {
        setClusterDomain(domain);
        const ns = detectedNs || namespace || '3scale';
        if (detectedNs) {
          setNamespace(detectedNs);
        }
        setUrl(buildUrlFromNamespace(ns, domain));
      } else {
        setDomainError(t('connection.domainNotFound'));
      }
    } catch (e: unknown) {
      const detail = apiErrorMessage(e, '');
      setDomainError(`${t('connection.domainError')}: ${detail}`);
    } finally {
      setFetchingDomain(false);
    }
  };

  const handleTest = async () => {
    setLoading(true);
    setError(null);
    setSuccess(false);
    try {
      await connectionApi.test({ url, accessToken, tenant });
      setSuccess(true);
      setAppState(prev => ({
        ...prev,
        connection: { url, accessToken, tenant, connected: true },
        namespace,
      }));
      // Refresh cluster versions on reconnect
      await loadVersions(true);
    } catch (e: unknown) {
      setError(apiErrorMessage(e, t('connection.errorDefault')));
      // I-3: clear persisted connection on connect failure / disconnect.
      clearPersistedConnection();
      setAppState(prev => ({
        ...prev,
        connection: { url, accessToken, tenant, connected: false },
      }));
    } finally {
      setLoading(false);
    }
  };

  const handleProfileChange = async (_e: React.FormEvent, value: string) => {
    const profile = value as ClusterProfile;
    setProfileSaving(true);
    setVersionsError(null);
    try {
      await settingsApi.put('clusterProfile', profile);
      setAppState(prev => ({ ...prev, clusterProfile: profile }));
      await loadVersions(true);
    } catch (e: unknown) {
      setVersionsError(apiErrorMessage(e, t('connection.profileSaveError')));
    } finally {
      setProfileSaving(false);
    }
  };

  const versions = appState.clusterVersions;
  const sourceLabelKey =
    versions?.source === 'profile'
      ? 'connection.sourceProfile'
      : versions?.source === 'default'
        ? 'connection.sourceDefault'
        : 'connection.sourceDetected';

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('connection.title')}</Title>
        <p style={{ marginTop: PF_SPACER_SM, color: PF_COLOR_MUTED }}>
          {t('connection.description')}
        </p>
      </PageSection>
      <PageSection>
        <Card>
          <CardBody>
            {error && (
              <Alert variant={AlertVariant.danger} title={error} style={{ marginBottom: '16px' }} />
            )}
            {success && (
              <Alert
                variant={AlertVariant.success}
                title={t('connection.successAlert')}
                style={{ marginBottom: '16px' }}
                actionLinks={
                  <Button variant="link" onClick={() => navigate('/services')}>
                    {t('connection.goToApiList')}
                  </Button>
                }
              />
            )}
            <Form>
              <FormGroup label={t('connection.labelUrlShort')} isRequired fieldId="url">
                <InputGroup>
                  <InputGroupItem isFill>
                    <TextInput
                      id="url"
                      type="url"
                      value={url}
                      onChange={(_e, val) => setUrl(val)}
                      placeholder="https://your-admin.3scale.net"
                      isRequired
                    />
                  </InputGroupItem>
                  <InputGroupItem>
                    <Button
                      variant="control"
                      onClick={handleFetchDomain}
                      isDisabled={fetchingDomain}
                      title={t('connection.btnFetchDomain')}
                    >
                      {fetchingDomain ? <Spinner size="sm" /> : t('connection.btnFetchDomain')}
                    </Button>
                  </InputGroupItem>
                </InputGroup>
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem>{domainError || t('connection.urlHelper')}</HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>
              <FormGroup
                label={t('connection.labelToken')}
                isRequired
                fieldId="token"
              >
                <InputGroup>
                  <InputGroupItem isFill>
                    <TextInput
                      id="token"
                      type={showToken ? 'text' : 'password'}
                      value={accessToken}
                      onChange={(_e, val) => setAccessToken(val)}
                      placeholder={t('connection.tokenPlaceholder')}
                      isRequired
                      autoComplete="off"
                    />
                  </InputGroupItem>
                  <InputGroupItem>
                    <Button
                      variant="control"
                      onClick={() => setShowToken(!showToken)}
                      aria-label={showToken ? t('connection.hideToken') : t('connection.showToken')}
                    >
                      {showToken ? <EyeSlashIcon /> : <EyeIcon />}
                    </Button>
                  </InputGroupItem>
                </InputGroup>
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem>
                      {t('connection.tokenHelper')}
                    </HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>
              <FormGroup label={t('connection.labelTenant')} fieldId="tenant">
                <TextInput
                  id="tenant"
                  value={tenant}
                  onChange={(_e, val) => setTenant(val)}
                  placeholder={t('connection.tenantPlaceholder')}
                />
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem>{t('connection.tenantHelper')}</HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>
              <FormGroup label={t('connection.labelNamespace')} isRequired fieldId="namespace">
                <TextInput
                  id="namespace"
                  value={namespace}
                  onChange={(_e, val) => handleNamespaceChange(val)}
                  placeholder="default"
                  isRequired
                />
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem>{t('connection.namespaceHelper')}</HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>
              <ActionGroup>
                <Button
                  variant="primary"
                  onClick={handleTest}
                  isDisabled={loading || !url || !accessToken}
                >
                  {loading ? <><Spinner size="sm" /> {t('connection.btnTesting')}</> : t('connection.btnTest')}
                </Button>
                {success && (
                  <Button variant="secondary" onClick={() => navigate('/services')}>
                    {t('connection.btnNext')}
                  </Button>
                )}
              </ActionGroup>
            </Form>
          </CardBody>
        </Card>

        {shouldShowClusterVersionsCard(appState.connection.connected) && (
          <Card style={{ marginTop: PF_SPACER_MD }}>
            <CardBody>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: PF_SPACER_SM }}>
                <Title headingLevel="h3" size="lg">
                  {t('connection.versionsTitle')}
                </Title>
                <Button
                  variant="secondary"
                  onClick={() => loadVersions(true)}
                  isDisabled={versionsLoading || profileSaving}
                  icon={<SyncAltIcon />}
                >
                  {versionsLoading ? t('connection.versionsRefreshing') : t('connection.btnRefreshVersions')}
                </Button>
              </div>
              <p style={{ marginTop: PF_SPACER_SM, color: PF_COLOR_MUTED, fontSize: PF_FONT_SIZE_SM }}>
                {t('connection.versionsDescription')}
              </p>

              {versionsError && (
                <Alert variant="warning" isInline title={versionsError} style={{ marginTop: PF_SPACER_SM }} />
              )}

              <FormGroup
                label={t('connection.labelProfile')}
                fieldId="cluster-profile"
                style={{ marginTop: PF_SPACER_MD, maxWidth: 360 }}
              >
                <FormSelect
                  id="cluster-profile"
                  value={appState.clusterProfile}
                  onChange={handleProfileChange}
                  isDisabled={profileSaving || versionsLoading}
                  aria-label={t('connection.labelProfile')}
                >
                  {PROFILE_OPTIONS.map(opt => (
                    <FormSelectOption
                      key={opt}
                      value={opt}
                      label={t(clusterProfileI18nKey(opt))}
                    />
                  ))}
                </FormSelect>
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem>{t('connection.profileHelper')}</HelperTextItem>
                  </HelperText>
                </FormHelperText>
              </FormGroup>

              {versionsLoading && !versions ? (
                <div style={{ textAlign: 'center', padding: PF_SPACER_MD }}>
                  <Spinner size="md" /> {t('connection.versionsLoading')}
                </div>
              ) : versions ? (
                <>
                  <div style={{ marginTop: PF_SPACER_MD, display: 'flex', alignItems: 'center', gap: PF_SPACER_SM, flexWrap: 'wrap' }}>
                    <Label color={versions.source === 'detected' ? 'green' : versions.source === 'profile' ? 'blue' : 'orange'}>
                      {t(sourceLabelKey)}
                    </Label>
                    {versions.capabilities?.corsNative ? (
                      <Label color="green">{t('connection.capCorsNative')}</Label>
                    ) : (
                      <Label color="orange">{t('connection.capCorsFallback')}</Label>
                    )}
                  </div>
                  <DescriptionList style={{ marginTop: PF_SPACER_MD }} isHorizontal>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t('connection.labelOcp')}</DescriptionListTerm>
                      <DescriptionListDescription>{displayOrDash(versions.ocp)}</DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t('connection.labelGatewayApi')}</DescriptionListTerm>
                      <DescriptionListDescription>{displayOrDash(versions.gatewayApi)}</DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t('connection.labelKuadrant')}</DescriptionListTerm>
                      <DescriptionListDescription>{displayOrDash(versions.kuadrant)}</DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t('connection.labelOssm')}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {displayOrDash(versions.ossm)}
                        {versions.ossmExpectedForOcp && (
                          <span style={{ marginLeft: PF_SPACER_SM, color: PF_COLOR_MUTED, fontSize: PF_FONT_SIZE_SM }}>
                            ({t('connection.ossmExpected', { version: versions.ossmExpectedForOcp })})
                          </span>
                        )}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                  </DescriptionList>
                  {versions.errors && versions.errors.length > 0 && (
                    <Alert
                      variant="info"
                      isInline
                      title={t('connection.versionsSoftFail')}
                      style={{ marginTop: PF_SPACER_MD }}
                    >
                      <ul style={{ margin: 0, paddingLeft: 18 }}>
                        {versions.errors.map((err, i) => (
                          <li key={i}>{err}</li>
                        ))}
                      </ul>
                    </Alert>
                  )}
                </>
              ) : null}
            </CardBody>
          </Card>
        )}

        {appState.connection.connected && (
          <Card style={{ marginTop: PF_SPACER_MD }}>
            <CardBody>
              <Title headingLevel="h3" size="lg">
                <CheckCircleIcon color="green" /> {t('connection.infoTitle')}
              </Title>
              <DescriptionList style={{ marginTop: PF_SPACER_MD }}>
                <DescriptionListGroup>
                  <DescriptionListTerm>{t('connection.labelUrlShort')}</DescriptionListTerm>
                  <DescriptionListDescription>{appState.connection.url}</DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Tenant</DescriptionListTerm>
                  <DescriptionListDescription>{appState.connection.tenant || '-'}</DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>{t('connection.labelNamespace')}</DescriptionListTerm>
                  <DescriptionListDescription>{appState.namespace}</DescriptionListDescription>
                </DescriptionListGroup>
              </DescriptionList>
            </CardBody>
          </Card>
        )}
      </PageSection>
    </>
  );
};

export default ConnectionPage;
