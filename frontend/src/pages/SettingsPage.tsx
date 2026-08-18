import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Card,
  CardBody,
  Title,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Alert,
} from '@patternfly/react-core';
import { CogIcon } from '@patternfly/react-icons';
import { TIMEZONE_OPTIONS, getSavedTimezoneValue, setTimezone } from '../utils/timezone';
import { PF_COLOR_MUTED, PF_FONT_SIZE_XS, PF_SPACER_MD } from '../styles/pfTokens';
import styles from '../styles/shared.module.css';

const SettingsPage: React.FC = () => {
  const { t } = useTranslation();
  const [tzValue, setTzValue] = useState<string>(getSavedTimezoneValue);
  const [saved, setSaved] = useState(false);

  const handleTzChange = (_e: React.FormEvent, value: string) => {
    setTzValue(value);
    setTimezone(value);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  return (
    <div style={{ padding: '24px 32px', maxWidth: 720 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
        <CogIcon style={{ fontSize: '1.8rem', color: PF_COLOR_MUTED }} />
        <Title headingLevel="h1" size="xl">{t('settings.title')}</Title>
      </div>
      <p className={styles.mutedText} style={{ marginBottom: PF_SPACER_MD }}>{t('settings.subtitle')}</p>

      {saved && (
        <Alert variant="success" isInline title={t('settings.saved')} style={{ marginBottom: 16 }} />
      )}

      <Card>
        <CardBody>
          <Title headingLevel="h2" size="md" style={{ marginBottom: 16 }}>
            {t('settings.sectionDatetime')}
          </Title>
          <FormGroup label={t('settings.labelTimezone')} fieldId="tz-select">
            <FormSelect
              id="tz-select"
              value={tzValue}
              onChange={handleTzChange}
              style={{ maxWidth: 400 }}
            >
              {TIMEZONE_OPTIONS.map(opt => {
                const label = t(`settings.tz.${opt.value}`);
                const display = opt.offset ? `${label} (${opt.offset})` : label;
                return (
                  <FormSelectOption key={opt.value} value={opt.value} label={display} />
                );
              })}
            </FormSelect>
          </FormGroup>
          <p className={styles.mutedText} style={{ fontSize: PF_FONT_SIZE_XS, marginTop: 8 }}>
            {t('settings.timezoneHint')}
          </p>
        </CardBody>
      </Card>
    </div>
  );
};

export default SettingsPage;
