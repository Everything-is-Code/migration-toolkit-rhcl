import React, { useState, useEffect, useCallback } from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
} from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import { clusterApi } from '../api/client';
import { ClusterVersionsResponse } from '../api/types';
import { useAppState } from '../components/AppStateContext';
import { shouldShowClusterVersionsCard } from '../utils/clusterCapabilityUi';
import { apiErrorI18nMessage } from '../utils/apiError';
import ConnectionForm from '../components/connection/ConnectionForm';
import ClusterVersionsPanel from '../components/connection/ClusterVersionsPanel';
import shared from '../styles/shared.module.css';

const ConnectionPage: React.FC = () => {
  const { setAppState } = useAppState();
  const { t } = useTranslation();
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [versionsError, setVersionsError] = useState<string | null>(null);
  const [profileSaving, setProfileSaving] = useState(false);

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
      setVersionsError(apiErrorI18nMessage(e, t, t('connection.versionsError')));
    } finally {
      setVersionsLoading(false);
    }
  }, [applyVersions]);

  // Load cluster versions on mount (independent of 3scale connection).
  useEffect(() => {
    loadVersions(false);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('connection.title')}</Title>
        <p className={shared.pageDescription}>
          {t('connection.description')}
        </p>
      </PageSection>
      <PageSection>
        <ConnectionForm onConnected={() => loadVersions(true)} />

        {shouldShowClusterVersionsCard() && (
          <ClusterVersionsPanel
            versionsLoading={versionsLoading}
            versionsError={versionsError}
            profileSaving={profileSaving}
            onLoadVersions={loadVersions}
            onProfileSavingChange={setProfileSaving}
            onVersionsErrorChange={setVersionsError}
          />
        )}
      </PageSection>
    </>
  );
};

export default ConnectionPage;
