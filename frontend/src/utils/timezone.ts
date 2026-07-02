export const TZ_STORAGE_KEY = 'app_timezone';

export interface TimezoneOption {
  value: string;
  labelJa: string;
  labelEn: string;
  offset: string;
}

export const TIMEZONE_OPTIONS: TimezoneOption[] = [
  { value: 'auto',               labelJa: 'ブラウザ設定に従う',   labelEn: 'Browser default',        offset: '' },
  { value: 'Asia/Tokyo',         labelJa: '日本標準時 (JST)',     labelEn: 'Japan Standard Time',     offset: '+09:00' },
  { value: 'UTC',                labelJa: '協定世界時 (UTC)',     labelEn: 'Coordinated Universal Time', offset: '+00:00' },
  { value: 'Asia/Seoul',         labelJa: '韓国標準時 (KST)',     labelEn: 'Korea Standard Time',     offset: '+09:00' },
  { value: 'Asia/Shanghai',      labelJa: '中国標準時 (CST)',     labelEn: 'China Standard Time',     offset: '+08:00' },
  { value: 'Asia/Singapore',     labelJa: 'シンガポール標準時',   labelEn: 'Singapore Time',          offset: '+08:00' },
  { value: 'Europe/London',      labelJa: 'グリニッジ標準時',     labelEn: 'Greenwich Mean Time',     offset: '+00:00' },
  { value: 'Europe/Berlin',      labelJa: '中央欧州標準時 (CET)', labelEn: 'Central European Time',   offset: '+01:00' },
  { value: 'America/New_York',   labelJa: '米国東部時間 (EST)',   labelEn: 'US Eastern Time',         offset: '-05:00' },
  { value: 'America/Los_Angeles',labelJa: '米国西部時間 (PST)',   labelEn: 'US Pacific Time',         offset: '-08:00' },
];

export const getTimezone = (): string => {
  const stored = localStorage.getItem(TZ_STORAGE_KEY);
  if (!stored || stored === 'auto') return Intl.DateTimeFormat().resolvedOptions().timeZone;
  return stored;
};

export const setTimezone = (tz: string): void => {
  localStorage.setItem(TZ_STORAGE_KEY, tz);
};

export const getSavedTimezoneValue = (): string => {
  return localStorage.getItem(TZ_STORAGE_KEY) ?? 'auto';
};
