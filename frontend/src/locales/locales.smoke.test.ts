import { describe, expect, it } from 'vitest';
import en from './en.json';
import ja from './ja.json';
import { ERROR_CODE_I18N } from '../utils/apiError';

const flatKeys = (obj: Record<string, unknown>, prefix = ''): string[] =>
  Object.entries(obj).flatMap(([k, v]) => {
    const key = prefix ? `${prefix}.${k}` : k;
    return typeof v === 'object' && v !== null
      ? flatKeys(v as Record<string, unknown>, key)
      : [key];
  });

describe('locales', () => {
  it('keeps ja and en translation resources available', () => {
    expect(Object.keys(ja).length).toBeGreaterThan(0);
    expect(Object.keys(en).length).toBeGreaterThan(0);
    expect(ja).toHaveProperty('nav.appTitle');
    expect(en).toHaveProperty('nav.appTitle');
  });

  it('en has every key that ja has', () => {
    const jaKeys = flatKeys(ja as Record<string, unknown>);
    const enKeys = new Set(flatKeys(en as Record<string, unknown>));
    const missing = jaKeys.filter(k => !enKeys.has(k));
    expect(missing).toEqual([]);
  });

  it('ja has every key that en has', () => {
    const enKeys = flatKeys(en as Record<string, unknown>);
    const jaKeys = new Set(flatKeys(ja as Record<string, unknown>));
    const missing = enKeys.filter(k => !jaKeys.has(k));
    expect(missing).toEqual([]);
  });

  it('ERROR_CODE_I18N keys exist in en and ja locales', () => {
    const enKeys = new Set(flatKeys(en as Record<string, unknown>));
    const jaKeys = new Set(flatKeys(ja as Record<string, unknown>));
    for (const i18nKey of Object.values(ERROR_CODE_I18N)) {
      expect(enKeys.has(i18nKey), `missing en key ${i18nKey}`).toBe(true);
      expect(jaKeys.has(i18nKey), `missing ja key ${i18nKey}`).toBe(true);
    }
  });
});
