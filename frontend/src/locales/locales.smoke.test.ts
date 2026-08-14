import { describe, expect, it } from 'vitest';
import en from './en.json';
import ja from './ja.json';

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
});
