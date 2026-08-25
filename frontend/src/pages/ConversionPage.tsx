import React from 'react';
import {
  PageSection,
  PageSectionVariants,
  Title,
  Alert,
  Button,
  Stack,
  StackItem,
} from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { corsConversionHintKey } from './clusterCapabilityUi';
import { useAppState } from '../components/AppStateContext';
import ConversionForm from '../components/conversion/ConversionForm';
import ConversionResults from '../components/conversion/ConversionResults';
import styles from '../styles/shared.module.css';

const ConversionPage: React.FC = () => {
  const { appState } = useAppState();
  const { t } = useTranslation();
  const navigate = useNavigate();

  const hasCorsPolicy = appState.selectedServices.some(svc =>
    svc.policies?.some(p => p.enabled && p.name === 'cors'));
  const corsNative = appState.clusterVersions?.capabilities?.corsNative === true;
  const corsHintKey = corsConversionHintKey(hasCorsPolicy, corsNative);

  if (appState.selectedServices.length === 0) {
    return (
      <PageSection>
        <Alert variant="warning" title={t('conversion.warningTitle')}>
          <Button variant="link" onClick={() => navigate('/services')}>
            {t('conversion.goToApiList')}
          </Button>
        </Alert>
      </PageSection>
    );
  }

  return (
    <>
      <PageSection variant={PageSectionVariants.light}>
        <Title headingLevel="h1" size="2xl">{t('conversion.title')}</Title>
        <p className={styles.pageDescription}>
          {t('conversion.description', { count: appState.selectedServices.length })}
        </p>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          {corsHintKey && (
            <StackItem>
              <Alert
                variant={corsNative ? 'info' : 'warning'}
                isInline
                title={t(corsHintKey)}
              />
            </StackItem>
          )}
          <StackItem>
            <ConversionForm />
          </StackItem>
          {appState.conversionResults.length > 0 && (
            <StackItem>
              <ConversionResults
                results={appState.conversionResults}
                onNavigateYaml={() => navigate('/yaml')}
              />
            </StackItem>
          )}
        </Stack>
      </PageSection>
    </>
  );
};

export default ConversionPage;
