import React, { useState, useEffect } from 'react';
import {
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
  Title,
} from '@patternfly/react-core';
import { CheckCircleIcon, EyeIcon, EyeSlashIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { connectionApi, clusterApi, defaultsApi } from '../../api/client';
import { useAppState } from '../AppStateContext';
import { useNavigate } from 'react-router-dom';
import { clearPersistedConnection } from '../../utils/appStateStorage';
import { apiErrorMessage } from '../../utils/apiError';
import { PF_SPACER_MD } from '../../styles/pfTokens';

interface Props {
  onConnected: () => void;
}

const ConnectionForm: React.FC<Props> = ({ onConnected }) => {
  const { appState, setAppState } = useAppState();
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
      const detectedNs = (res.data as Record<string, unknown>)?.namespace as string | undefined;
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
      onConnected();
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

  return (
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
          <FormGroup label={t('connection.labelToken')} isRequired fieldId="token">
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
                <HelperTextItem>{t('connection.tokenHelper')}</HelperTextItem>
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
      </CardBody>
    </Card>
  );
};

export default ConnectionForm;
