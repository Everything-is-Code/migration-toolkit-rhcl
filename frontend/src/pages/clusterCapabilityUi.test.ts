import { describe, expect, it } from 'vitest';
import en from '../locales/en.json';
import ja from '../locales/ja.json';
import {
  corsConversionHintKey,
  shouldShowClusterVersionsCard,
} from '../pages/clusterCapabilityUi';

describe('clusterCapabilityUi', () => {
  it('shows versions card only when connected (Connection WU3)', () => {
    expect(shouldShowClusterVersionsCard(false)).toBe(false);
    expect(shouldShowClusterVersionsCard(true)).toBe(true);
  });

  it('picks CORS native vs fallback hint keys for Conversion page', () => {
    expect(corsConversionHintKey(false, false)).toBeNull();
    expect(corsConversionHintKey(false, true)).toBeNull();
    expect(corsConversionHintKey(true, false)).toBe('conversion.corsFallbackHint');
    expect(corsConversionHintKey(true, true)).toBe('conversion.corsNativeHint');
  });

  it('keeps Connection version and Conversion CORS hint strings in en+ja', () => {
    const required = [
      'connection.versionsTitle',
      'connection.versionsDescription',
      'connection.capCorsNative',
      'connection.capCorsFallback',
      'conversion.corsNativeHint',
      'conversion.corsFallbackHint',
    ];
    const lookup = (locale: unknown, key: string): unknown => {
      return key.split('.').reduce<unknown>((acc, part) => {
        if (acc != null && typeof acc === 'object' && part in (acc as object)) {
          return (acc as Record<string, unknown>)[part];
        }
        return undefined;
      }, locale);
    };
    for (const key of required) {
      expect(lookup(en, key)).toBeTruthy();
      expect(lookup(ja, key)).toBeTruthy();
    }
  });
});
