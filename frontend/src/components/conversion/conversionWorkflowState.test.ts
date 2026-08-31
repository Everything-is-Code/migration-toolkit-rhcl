import { describe, expect, it } from 'vitest';
import type { ApiService, ConversionResultItem } from '../../api/types';
import {
  buildEditsFromResults,
  conversionResultsFingerprint,
  nextStateAfterServiceSelect,
  resultsMatchSelection,
  shouldClearConversionResults,
} from './conversionWorkflowState';

const svc = (id: string, name = id): ApiService => ({ id, name, systemName: name });

const result = (
  serviceId: string,
  opts: Partial<ConversionResultItem> = {},
): ConversionResultItem => ({
  serviceId,
  serviceName: opts.serviceName ?? serviceId,
  packageName: opts.packageName ?? `${serviceId}-pkg`,
  historyId: opts.historyId ?? 1,
  compatibilityScore: opts.compatibilityScore ?? 90,
  files: opts.files ?? ['gateway.yaml'],
  yamlFiles: opts.yamlFiles ?? { 'gateway.yaml': `name: ${serviceId}-gateway` },
  status: opts.status,
  error: opts.error,
});

describe('shouldClearConversionResults', () => {
  it('clears when selected id changes', () => {
    expect(shouldClearConversionResults(['a'], 'b')).toBe(true);
  });

  it('keeps when same id is re-confirmed', () => {
    expect(shouldClearConversionResults(['a'], 'a')).toBe(false);
  });

  it('does not clear when there was no prior selection', () => {
    expect(shouldClearConversionResults([], 'a')).toBe(false);
  });
});

describe('nextStateAfterServiceSelect', () => {
  it('clears conversionResults when id changes', () => {
    const prev = {
      selectedServices: [svc('a')],
      conversionResults: [result('a')],
    };
    const next = nextStateAfterServiceSelect(prev, svc('b'));
    expect(next.selectedServices).toEqual([svc('b')]);
    expect(next.conversionResults).toEqual([]);
  });

  it('keeps conversionResults when same id is re-confirmed', () => {
    const results = [result('a')];
    const prev = {
      selectedServices: [svc('a')],
      conversionResults: results,
    };
    const next = nextStateAfterServiceSelect(prev, svc('a', 'A renamed'));
    expect(next.selectedServices[0].id).toBe('a');
    expect(next.conversionResults).toBe(results);
  });
});

describe('conversionResultsFingerprint', () => {
  it('is stable across array clones with same content', () => {
    const a = [result('svc1', { historyId: 10, packageName: 'pkg-a' })];
    const b = [...a];
    expect(conversionResultsFingerprint(a)).toBe(conversionResultsFingerprint(b));
  });

  it('changes when historyId changes', () => {
    const before = conversionResultsFingerprint([result('svc1', { historyId: 1 })]);
    const after = conversionResultsFingerprint([result('svc1', { historyId: 2 })]);
    expect(before).not.toBe(after);
  });

  it('falls back to packageName when historyId is missing', () => {
    const r = result('svc1', { packageName: 'fallback-pkg' });
    // Simulate missing historyId for fingerprint fallback (API may omit on failure paths).
    const withoutHistory = { ...r, historyId: undefined as unknown as number };
    expect(conversionResultsFingerprint([withoutHistory])).toBe('svc1:fallback-pkg');
  });
});

describe('buildEditsFromResults', () => {
  it('copies yamlFiles by service index', () => {
    const results = [
      result('a', { yamlFiles: { 'httproute.yaml': 'kind: HTTPRoute\n' } }),
      result('b', { yamlFiles: { 'gateway.yaml': 'kind: Gateway\n' } }),
    ];
    const edits = buildEditsFromResults(results);
    expect(edits[0]['httproute.yaml']).toBe('kind: HTTPRoute\n');
    expect(edits[1]['gateway.yaml']).toBe('kind: Gateway\n');
    // Shallow copy — mutating edits must not mutate source
    edits[0]['httproute.yaml'] = 'edited';
    expect(results[0].yamlFiles['httproute.yaml']).toBe('kind: HTTPRoute\n');
  });

  it('skips results without yamlFiles', () => {
    const failed = result('x');
    delete (failed as { yamlFiles?: unknown }).yamlFiles;
    expect(buildEditsFromResults([failed])).toEqual({});
  });
});

describe('resultsMatchSelection', () => {
  it('matches when selected id is present in results', () => {
    expect(resultsMatchSelection([result('a'), result('b')], [svc('b')])).toBe(true);
  });

  it('mismatches when selected id is absent', () => {
    expect(resultsMatchSelection([result('a')], [svc('b')])).toBe(false);
  });

  it('matches when results are empty', () => {
    expect(resultsMatchSelection([], [svc('a')])).toBe(true);
  });
});
