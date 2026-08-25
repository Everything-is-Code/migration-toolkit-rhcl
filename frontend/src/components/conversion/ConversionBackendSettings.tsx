import React from 'react';
import {
  Checkbox,
  TextInput,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Form,
} from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import styles from '../../styles/shared.module.css';

interface Props {
  isExternal: boolean;
  externalBackendUrl: string;
  onExternalChange: (checked: boolean) => void;
  onExternalUrlChange: (url: string) => void;
}

const ConversionBackendSettings: React.FC<Props> = ({
  isExternal,
  externalBackendUrl,
  onExternalChange,
  onExternalUrlChange,
}) => {
  const { t } = useTranslation();

  return (
    <div className={styles.bluePanel}>
      <div className={styles.bluePanelTitle}>
        {t('conversion.backendType', 'Backend Settings')}
      </div>
      <Form>
        <FormGroup>
          <Checkbox
            id="external-backend"
            label={t('conversion.externalBackend', 'Backend is an external service (AWS ECS / external HTTPS endpoint)')}
            isChecked={isExternal}
            onChange={(_e, checked) => onExternalChange(checked)}
          />
        </FormGroup>
        {isExternal && (
          <>
            <FormGroup
              label={t('conversion.externalBackendUrl', 'External Backend URL')}
              fieldId="external-backend-url"
              isRequired
            >
              <TextInput
                id="external-backend-url"
                type="url"
                value={externalBackendUrl}
                onChange={(_e, val) => onExternalUrlChange(val)}
                placeholder={t('conversion.externalBackendUrlPlaceholder')}
              />
              <FormHelperText>
                <HelperText>
                  <HelperTextItem>
                    {t('conversion.externalBackendUrlHelp', 'e.g.: https://foo.ecs.us-east-2.on.aws')}
                  </HelperTextItem>
                </HelperText>
              </FormHelperText>
            </FormGroup>
            <div className={styles.warningCallout}>
              <div style={{ fontWeight: 600, marginBottom: '6px' }}>
                {t('conversion.externalNote', 'The following resources will be additionally generated for external services:')}
              </div>
              <ul style={{ margin: 0, paddingLeft: '18px', lineHeight: '1.8' }}>
                <li dangerouslySetInnerHTML={{ __html: t('conversion.externalNoteEnvoy') }} />
                <li dangerouslySetInnerHTML={{ __html: t('conversion.externalNoteRoute') }} />
              </ul>
            </div>
          </>
        )}
      </Form>
    </div>
  );
};

export default ConversionBackendSettings;
