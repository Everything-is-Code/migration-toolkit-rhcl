import { describe, expect, it } from 'vitest';
import type { AppState } from '../components/AppStateContext';
import type { ConversionResultItem, ValidationResult } from '../api/types';
import { conversionResultsFingerprint } from '../components/conversion/conversionWorkflowState';
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

const snapshotFor = (
  results: ConversionResultItem[],
  resultsOverride?: Record<string, ValidationResult>,
): NonNullable<AppState['validationSnapshot']> => ({
  fingerprint: conversionResultsFingerprint(results),
  results: resultsOverride ?? {
    svc1: { valid: true, items: [{ check: 'ok', status: 'OK' as const, message: 'ok' }] },
  },
});

const baseState = (overrides: Partial<AppState> = {}): AppState => {
  const conversionResults = overrides.conversionResults ?? [baseResult()];
  return {
    connection: { url: '', accessToken: '', tenant: '', connected: false },
    selectedServices: [],
    conversionResults,
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
    validationSnapshot: snapshotFor(conversionResults),
    ...overrides,
  };
};

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

  it('disables when yamlFiles is empty', () => {
    const g = getApplyGuardrails({ ...baseResult(), yamlFiles: {} }, baseState());
    expect(g.reasonKey).toBe('download.applyDisabledNoYaml');
  });

  it('disables when validation snapshot is missing', () => {
    const g = getApplyGuardrails(baseResult(), baseState({ validationSnapshot: null }));
    expect(g.reasonKey).toBe('download.applyDisabledValidationMissing');
  });

  it('disables when validation snapshot fingerprint is stale', () => {
    const g = getApplyGuardrails(baseResult(), baseState({
      validationSnapshot: {
        fingerprint: 'stale-fingerprint',
        results: { svc1: { valid: true, items: [{ check: 'ok', status: 'OK', message: 'ok' }] } },
      },
    }));
    expect(g.reasonKey).toBe('download.applyDisabledValidationMissing');
  });

  it('disables when service validation is missing from snapshot', () => {
    const results = [baseResult()];
    const g = getApplyGuardrails(baseResult(), baseState({
      conversionResults: results,
      validationSnapshot: {
        ...snapshotFor(results),
        results: {},
      },
    }));
    expect(g.reasonKey).toBe('download.applyDisabledValidationError');
  });

  it('disables when YAML was edited after validation', () => {
    const validatedYaml = { 'gateway.yaml': 'kind: Gateway\n' };
    const editedYaml = { 'gateway.yaml': 'kind: Gateway\nbroken: true\n' };
    const validatedResult = { ...baseResult(), yamlFiles: validatedYaml };
    const editedResult = { ...baseResult(), yamlFiles: editedYaml };
    const g = getApplyGuardrails(editedResult, baseState({
      conversionResults: [editedResult],
      validationSnapshot: snapshotFor([validatedResult]),
    }));
    expect(g.reasonKey).toBe('download.applyDisabledValidationMissing');
  });

  it('disables when validation has ERROR', () => {
    const results = [baseResult()];
    const g = getApplyGuardrails(baseResult(), baseState({
      conversionResults: results,
      validationSnapshot: snapshotFor(results, {
        svc1: { valid: false, items: [{ check: 'x', status: 'ERROR' as const, message: 'bad' }] },
      }),
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
    const results = [baseResult()];
    const g = getApplyGuardrails(baseResult(), baseState({
      conversionResults: results,
      validationSnapshot: snapshotFor(results),
    }));
    expect(g).toEqual({ enabled: true, reasonKey: null });
  });
});
