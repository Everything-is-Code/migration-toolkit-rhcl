import React from 'react';
import {
  Card,
  CardBody,
  Title,
  Button,
  Alert,
  Spinner,
  FormGroup,
  FormSelect,
  FormSelectOption,
  FormHelperText,
  HelperText,
  HelperTextItem,
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
  Label,
} from '@patternfly/react-core';
import { SyncAltIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { settingsApi } from '../../api/client';
import { ClusterProfile } from '../../api/types';
import { useAppState } from '../AppStateContext';
import { clusterProfileI18nKey } from '../../utils/clusterCapabilityUi';
import { apiErrorI18nMessage } from '../../utils/apiError';
import {
  PF_FONT_SIZE_SM,
  PF_SPACER_MD,
  PF_SPACER_SM,
} from '../../styles/pfTokens';
import shared from '../../styles/shared.module.css';

const PROFILE_OPTIONS: ClusterProfile[] = ['auto', 'ocp-4.19', 'ocp-4.21'];

const displayOrDash = (value: string | null | undefined) =>
  value && value.trim() ? value : '—';

interface Props {
  versionsLoading: boolean;
  versionsError: string | null;
  profileSaving: boolean;
  onLoadVersions: (refresh?: boolean) => Promise<void>;
  onProfileSavingChange: (saving: boolean) => void;
  onVersionsErrorChange: (err: string | null) => void;
}

const ClusterVersionsPanel: React.FC<Props> = ({
  versionsLoading,
  versionsError,
  profileSaving,
  onLoadVersions,
  onProfileSavingChange,
  onVersionsErrorChange,
}) => {
  const { appState, setAppState } = useAppState();
  const { t } = useTranslation();

  const versions = appState.clusterVersions;
  const sourceLabelKey =
    versions?.source === 'profile'
      ? 'connection.sourceProfile'
      : versions?.source === 'default'
        ? 'connection.sourceDefault'
        : 'connection.sourceDetected';

  const handleProfileChange = async (_e: React.FormEvent, value: string) => {
    const profile = value as ClusterProfile;
    onProfileSavingChange(true);
    onVersionsErrorChange(null);
    try {
      await settingsApi.put('clusterProfile', profile);
      setAppState(prev => ({ ...prev, clusterProfile: profile }));
      await onLoadVersions(true);
    } catch (e: unknown) {
      onVersionsErrorChange(apiErrorI18nMessage(e, t, t('connection.profileSaveError')));
    } finally {
      onProfileSavingChange(false);
    }
  };

  return (
    <Card style={{ marginTop: PF_SPACER_MD }}>
      <CardBody>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: PF_SPACER_SM }}>
          <Title headingLevel="h3" size="lg">
            {t('connection.versionsTitle')}
          </Title>
          <Button
            variant="secondary"
            onClick={() => onLoadVersions(true)}
            isDisabled={versionsLoading || profileSaving}
            icon={<SyncAltIcon />}
          >
            {versionsLoading ? t('connection.versionsRefreshing') : t('connection.btnRefreshVersions')}
          </Button>
        </div>
        <p className={shared.mutedText} style={{ marginTop: PF_SPACER_SM, fontSize: PF_FONT_SIZE_SM }}>
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
          <div className={shared.centeredBlock} style={{ padding: PF_SPACER_MD }}>
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
                    <span className={shared.mutedText} style={{ marginLeft: PF_SPACER_SM, fontSize: PF_FONT_SIZE_SM }}>
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
  );
};

export default ClusterVersionsPanel;
