import { describe, expect, it } from 'vitest';
import en from '../locales/en.json';
import ja from '../locales/ja.json';
import type { ClusterProfile, ClusterVersionsResponse } from '../api/types';
import {
  CLUSTER_PROFILE_I18N_KEYS,
  clusterProfileI18nKey,
  corsConversionHintKey,
  formatKuadrantDisplay,
  getClusterConnectionUiState,
  isFallbackVersionSource,
  shouldShowClusterVersionsCard,
} from './clusterCapabilityUi';

const baseVersions = (overrides: Partial<ClusterVersionsResponse> = {}): ClusterVersionsResponse => ({
  ocp: '4.19.0',
  gatewayApi: '1.2.1',
  kuadrant: null,
  ossm: null,
  ossmExpectedForOcp: '2.6',
  capabilities: {
    clusterReachable: false,
    corsNative: false,
    kuadrantPresent: false,
    ossmPresent: false,
    ossmMatchesOcp: false,
    timeoutsSupported: true,
    retriesSupported: true,
  },
  source: 'default',
  profile: 'auto',
  ...overrides,
});

describe('clusterCapabilityUi', () => {
  it('always shows versions card (independent of 3scale connect)', () => {
    expect(shouldShowClusterVersionsCard(false)).toBe(true);
    expect(shouldShowClusterVersionsCard(true)).toBe(true);
  });

  it('derives cluster connection UI state from versions response', () => {
    expect(getClusterConnectionUiState(null)).toBe('unreachable');
    expect(getClusterConnectionUiState(baseVersions())).toBe('unreachable');
    expect(getClusterConnectionUiState(baseVersions({
      source: 'detected',
      capabilities: { ...baseVersions().capabilities, clusterReachable: true },
    }))).toBe('reachable');
    expect(getClusterConnectionUiState(baseVersions({ source: 'profile' }))).toBe('profile');
  });

  it('formats Kuadrant display by connection state', () => {
    const t = (key: string) => key;
    expect(formatKuadrantDisplay(baseVersions(), t)).toBe('connection.kuadrantUnreachable');
    expect(formatKuadrantDisplay(baseVersions({
      source: 'detected',
      capabilities: { ...baseVersions().capabilities, clusterReachable: true },
    }), t)).toBe('connection.kuadrantAbsent');
    expect(formatKuadrantDisplay(baseVersions({
      source: 'detected',
      kuadrant: '1.4.2',
      capabilities: { ...baseVersions().capabilities, clusterReachable: true, kuadrantPresent: true },
    }), t)).toBe('1.4.2');
    expect(formatKuadrantDisplay(baseVersions({
      source: 'profile',
      capabilities: { ...baseVersions().capabilities, clusterReachable: true, kuadrantPresent: true },
    }), t)).toBe('connection.kuadrantProfileAssumed');
  });

  it('flags default source as fallback version labels', () => {
    expect(isFallbackVersionSource(baseVersions())).toBe(true);
    expect(isFallbackVersionSource(baseVersions({ source: 'detected' }))).toBe(false);
  });

  it('picks CORS native vs fallback hint keys for Conversion page', () => {
    expect(corsConversionHintKey(false, false)).toBeNull();
    expect(corsConversionHintKey(false, true)).toBeNull();
    expect(corsConversionHintKey(true, false)).toBe('conversion.corsFallbackHint');
    expect(corsConversionHintKey(true, true)).toBe('conversion.corsNativeHint');
  });

  it('maps every ClusterProfile to a connection.profile.* i18n key (I10)', () => {
    const profiles: ClusterProfile[] = ['auto', 'ocp-4.19', 'ocp-4.21'];
    expect(Object.keys(CLUSTER_PROFILE_I18N_KEYS).sort()).toEqual([...profiles].sort());
    expect(clusterProfileI18nKey('auto')).toBe('connection.profile.auto');
    expect(clusterProfileI18nKey('ocp-4.19')).toBe('connection.profile.ocp419');
    expect(clusterProfileI18nKey('ocp-4.21')).toBe('connection.profile.ocp421');
  });

  it('keeps Connection version and Conversion CORS hint strings in en+ja', () => {
    const required = [
      'connection.versionsTitle',
      'connection.versionsDescription',
      'connection.capCorsNative',
      'connection.capCorsFallback',
      'connection.profile.auto',
      'connection.profile.ocp419',
      'connection.profile.ocp421',
      'connection.clusterUnreachableTitle',
      'connection.clusterUnreachableBody',
      'connection.clusterProfileTitle',
      'connection.kuadrantUnreachable',
      'connection.kuadrantAbsent',
      'connection.kuadrantProfileAssumed',
      'connection.versionFallbackDefault',
      'connection.clusterReachableLabel',
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
