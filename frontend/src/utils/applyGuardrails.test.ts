import { describe, expect, it } from 'vitest';
import type { AppState } from '../components/AppStateContext';
import type { ConversionResultItem } from '../api/types';
import { getApplyGuardrails } from './applyGuardrails';

const baseResult = (): ConversionResultItem => ({
  serviceId: 'svc1',
  serviceName: 'API',
  packageName: 'api-pkg',
  historyId: 1,
  compatibilityScore: 90,
  files: ['gateway.yaml'],
  yamlFiles: { 'gateway.yaml': 'kind: Gateway\n' },
});

const baseState = (overrides: Partial<AppState> = {}): AppState => ({
  connection: { url: '', accessToken: '', tenant: '', connected: false },
  selectedServices: [],
  conversionResults: [baseResult()],
  namespace: 'default',
  clusterVersions: {
    profile: 'auto',
    source: 'detected',
    ocp: '4.16',
    gatewayApi: '1.2',
    kuadrant: '1.0',
    ossm: null,
    ossmExpectedForOcp: null,
    capabilities: {
      clusterReachable: true,
      corsNative: false,
      kuadrantPresent: true,
      ossmPresent: false,
      ossmMatchesOcp: true,
      timeoutsSupported: false,
      retriesSupported: false,
    },
  },
  clusterProfile: 'auto',
  validationSnapshot: {
    fingerprint: 'svc1:1',
    results: { svc1: { valid: true, items: [{ check: 'ok', status: 'OK', message: 'ok' }] } },
  },
  ...overrides,
});

describe('getApplyGuardrails', () => {
  it('disables when cluster is not reachable', () => {
    const g = getApplyGuardrails(baseResult(), baseState({
      clusterVersions: {
        profile: 'auto',
        source: 'default',
        ocp: '4.16',
        gatewayApi: '1.2',
        kuadrant: null,
        ossm: null,
        ossmExpectedForOcp: null,
        capabilities: {
          clusterReachable: false,
          corsNative: false,
          kuadrantPresent: false,
          ossmPresent: false,
          ossmMatchesOcp: false,
          timeoutsSupported: false,
          retriesSupported: false,
        },
      },
    }));
    expect(g).toEqual({ enabled: false, reasonKey: 'download.applyDisabledCluster' });
  });

  it('disables when validation snapshot is missing', () => {
    const g = getApplyGuardrails(baseResult(), baseState({ validationSnapshot: null }));
    expect(g.reasonKey).toBe('download.applyDisabledValidationMissing');
  });

  it('disables when validation has ERROR', () => {
    const g = getApplyGuardrails(baseResult(), baseState({
      validationSnapshot: {
        fingerprint: 'svc1:1',
        results: { svc1: { valid: false, items: [{ check: 'x', status: 'ERROR', message: 'bad' }] } },
      },
    }));
    expect(g.reasonKey).toBe('download.applyDisabledValidationError');
  });

  it('disables when REPLACE_ME is present', () => {
    const g = getApplyGuardrails({
      ...baseResult(),
      yamlFiles: { 'secret.yaml': 'REPLACE_ME' },
    }, baseState());
    expect(g.reasonKey).toBe('download.applyDisabledPlaceholder');
  });

  it('enables when all guardrails pass', () => {
    const g = getApplyGuardrails(baseResult(), baseState());
    expect(g).toEqual({ enabled: true, reasonKey: null });
  });
});
