export const TZ_STORAGE_KEY = 'app_timezone';

export interface TimezoneOption {
  value: string;
  offset: string;
}

export const TIMEZONE_OPTIONS: TimezoneOption[] = [
  { value: 'auto',                offset: '' },
  { value: 'Asia/Tokyo',          offset: '+09:00' },
  { value: 'UTC',                 offset: '+00:00' },
  { value: 'Asia/Seoul',          offset: '+09:00' },
  { value: 'Asia/Shanghai',       offset: '+08:00' },
  { value: 'Asia/Singapore',      offset: '+08:00' },
  { value: 'Europe/London',       offset: '+00:00' },
  { value: 'Europe/Berlin',       offset: '+01:00' },
  { value: 'America/New_York',    offset: '-05:00' },
  { value: 'America/Los_Angeles', offset: '-08:00' },
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
