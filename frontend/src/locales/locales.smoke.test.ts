import { describe, expect, it } from 'vitest';
import en from './en.json';
import ja from './ja.json';

describe('locales', () => {
  it('keeps ja and en translation resources available', () => {
    expect(Object.keys(ja).length).toBeGreaterThan(0);
    expect(Object.keys(en).length).toBeGreaterThan(0);
    expect(ja).toHaveProperty('nav.appTitle');
    expect(en).toHaveProperty('nav.appTitle');
  });
});
