import React, { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../i18n';
import styles from '../App.module.css';

const LangSwitcher: React.FC = () => {
  const { i18n: i18nInst } = useTranslation();
  const current = (i18nInst.language || 'en').startsWith('ja') ? 'ja' : 'en';

  useEffect(() => {
    document.documentElement.lang = current;
  }, [current]);

  const setLanguage = (lang: string) => {
    void i18n.changeLanguage(lang).then(() => {
      document.documentElement.lang = lang;
    });
  };

  const btn = (lang: string, label: string, ariaLabel: string) => (
    <button
      key={lang}
      type="button"
      onClick={() => setLanguage(lang)}
      aria-pressed={current === lang}
      aria-label={ariaLabel}
      className={[
        styles.langButton,
        current === lang ? styles.isActive : '',
        lang === 'ja' ? styles.langButtonJa : styles.langButtonEn,
      ].filter(Boolean).join(' ')}
    >
      {label}
    </button>
  );

  return (
    <div style={{ display: 'flex' }} role="group" aria-label="Language">
      {btn('ja', 'JA', 'Japanese')}
      {btn('en', 'EN', 'English')}
    </div>
  );
};

export default LangSwitcher;
