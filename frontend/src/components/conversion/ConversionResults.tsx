import React from 'react';
import {
  Card,
  CardBody,
  Title,
  DataList,
  DataListItem,
  DataListItemRow,
  DataListItemCells,
  DataListCell,
  Label,
  Button,
} from '@patternfly/react-core';
import { CheckCircleIcon, TimesCircleIcon } from '@patternfly/react-icons';
import { useTranslation } from 'react-i18next';
import { ConversionResultItem } from '../../api/types';
import { PF_DANGER, PF_SUCCESS } from '../../styles/pfTokens';
import styles from '../../styles/shared.module.css';

interface Props {
  results: ConversionResultItem[];
  onNavigateYaml: () => void;
}

const ConversionResults: React.FC<Props> = ({ results, onNavigateYaml }) => {
  const { t } = useTranslation();

  return (
    <Card>
      <CardBody>
        <Title headingLevel="h3" size="lg">{t('conversion.resultTitle')}</Title>
        <DataList aria-label={t('conversion.ariaResult')} style={{ marginTop: '16px' }}>
          {results.map(result => (
            <DataListItem key={result.serviceId}>
              <DataListItemRow>
                <DataListItemCells
                  dataListCells={[
                    <DataListCell key="icon">
                      {result.status === 'FAILED'
                        ? <TimesCircleIcon color={PF_DANGER} />
                        : <CheckCircleIcon color={PF_SUCCESS} />}
                    </DataListCell>,
                    <DataListCell key="name" width={2}>
                      <strong>{result.serviceName}</strong>
                    </DataListCell>,
                    <DataListCell key="score">
                      Score: {result.compatibilityScore}%
                    </DataListCell>,
                    <DataListCell key="files">
                      {result.files ? (
                        <div>
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                            {result.files.map(f => {
                              const isExternalFile = f === 'envoyfilter.yaml';
                              return (
                                <Label
                                  key={f}
                                  isCompact
                                  color={isExternalFile ? 'orange' : 'blue'}
                                  title={isExternalFile ? t('conversion.externalFileTitle') : undefined}
                                >
                                  {f}
                                </Label>
                              );
                            })}
                          </div>
                          {result.files.includes('envoyfilter.yaml') && (
                            <div
                              className={styles.warningLabel}
                              style={{ marginTop: '6px', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '4px' }}
                            >
                              <span className={styles.warningDot}>●</span>
                              {t('conversion.externalFilesNote', 'Includes resources for external services (EnvoyFilter + Host rewrite)')}
                            </div>
                          )}
                        </div>
                      ) : result.error}
                    </DataListCell>,
                  ]}
                />
              </DataListItemRow>
            </DataListItem>
          ))}
        </DataList>
        <div style={{ marginTop: '16px' }}>
          <Button variant="primary" onClick={onNavigateYaml}>
            {t('conversion.btnNext')}
          </Button>
        </div>
      </CardBody>
    </Card>
  );
};

export default ConversionResults;
