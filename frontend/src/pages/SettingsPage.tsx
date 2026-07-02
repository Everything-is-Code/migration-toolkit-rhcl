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

const SettingsPage: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [tzValue, setTzValue] = useState<string>(getSavedTimezoneValue);
  const [saved, setSaved] = useState(false);

  const handleTzChange = (_e: React.FormEvent, value: string) => {
    setTzValue(value);
    setTimezone(value);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const lang = i18n.language?.startsWith('ja') ? 'ja' : 'en';

  return (
    <div style={{ padding: '24px 32px', maxWidth: 720 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
        <CogIcon style={{ fontSize: '1.8rem', color: '#6a6e73' }} />
        <Title headingLevel="h1" size="xl">{t('settings.title')}</Title>
      </div>
      <p style={{ color: '#6a6e73', marginBottom: 24 }}>{t('settings.subtitle')}</p>

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
                const label = lang === 'ja' ? opt.labelJa : opt.labelEn;
                const display = opt.offset ? `${label} (${opt.offset})` : label;
                return (
                  <FormSelectOption key={opt.value} value={opt.value} label={display} />
                );
              })}
            </FormSelect>
          </FormGroup>
          <p style={{ fontSize: 12, color: '#6a6e73', marginTop: 8 }}>
            {t('settings.timezoneHint')}
          </p>
        </CardBody>
      </Card>
    </div>
  );
};

export default SettingsPage;
