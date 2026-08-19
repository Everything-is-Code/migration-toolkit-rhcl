import { describe, expect, it, vi } from 'vitest';
import { runCompatibilityChecks } from './compatibilityChecks';
import type { CompatibilityResult } from '../api/types';

function resultFor(id: string, name: string): CompatibilityResult {
  return {
    serviceId: id,
    serviceName: name,
    score: 90,
    level: 'HIGH',
    items: [],
  };
}

describe('runCompatibilityChecks', () => {
  it('loads supported policies once for multiple services', async () => {
    const loadSupportedPolicies = vi.fn().mockResolvedValue(['cors', 'rate_limit']);
    const checkCompatibility = vi.fn(async (id: string) => ({
      data: resultFor(id, `Service ${id}`),
    }));

    const { results, error } = await runCompatibilityChecks(
      [
        { id: '1', name: 'One' },
        { id: '2', name: 'Two' },
        { id: '3', name: 'Three' },
      ],
      { url: 'https://3scale.example', accessToken: 'tok' },
      {
        loadSupportedPolicies,
        checkCompatibility,
        errorMessage: 'check failed',
      },
    );

    expect(error).toBeNull();
    expect(results).toHaveLength(3);
    expect(loadSupportedPolicies).toHaveBeenCalledTimes(1);
    expect(checkCompatibility).toHaveBeenCalledTimes(3);
    expect(checkCompatibility).toHaveBeenNthCalledWith(
      1,
      '1',
      'https://3scale.example',
      'tok',
      ['cors', 'rate_limit'],
    );
    expect(checkCompatibility).toHaveBeenNthCalledWith(
      2,
      '2',
      'https://3scale.example',
      'tok',
      ['cors', 'rate_limit'],
    );
  });

  it('returns soft error rows per service when checkCompatibility throws', async () => {
    const loadSupportedPolicies = vi.fn().mockResolvedValue(['cors']);
    const checkCompatibility = vi.fn()
      .mockResolvedValueOnce({ data: resultFor('1', 'One') })
      .mockRejectedValueOnce(new Error('boom'));

    const { results, error } = await runCompatibilityChecks(
      [
        { id: '1', name: 'One' },
        { id: '2', name: 'Two' },
      ],
      { url: 'https://3scale.example', accessToken: 'tok' },
      {
        loadSupportedPolicies,
        checkCompatibility,
        errorMessage: 'check failed',
      },
    );

    expect(error).toBeNull();
    expect(loadSupportedPolicies).toHaveBeenCalledTimes(1);
    expect(results[0].score).toBe(90);
    expect(results[1].score).toBe(0);
    expect(results[1].items[0].message).toBe('check failed');
  });

  it('retries loadSupportedPolicies once before failing', async () => {
    const loadSupportedPolicies = vi.fn()
      .mockRejectedValueOnce(new Error('transient'))
      .mockResolvedValueOnce(['cors']);
    const checkCompatibility = vi.fn(async (id: string) => ({
      data: resultFor(id, `Service ${id}`),
    }));

    const { results, error } = await runCompatibilityChecks(
      [{ id: '1', name: 'One' }],
      { url: 'https://3scale.example', accessToken: 'tok' },
      {
        loadSupportedPolicies,
        checkCompatibility,
        errorMessage: 'check failed',
      },
    );

    expect(error).toBeNull();
    expect(results).toHaveLength(1);
    expect(loadSupportedPolicies).toHaveBeenCalledTimes(2);
    expect(checkCompatibility).toHaveBeenCalledTimes(1);
  });

  it('surfaces loadSupportedPolicies failures as a top-level error', async () => {
    const loadSupportedPolicies = vi.fn().mockRejectedValue(new Error('policies down'));
    const checkCompatibility = vi.fn();

    const { results, error } = await runCompatibilityChecks(
      [{ id: '1', name: 'One' }],
      { url: 'https://3scale.example', accessToken: 'tok' },
      {
        loadSupportedPolicies,
        checkCompatibility,
        errorMessage: 'check failed',
      },
    );

    expect(error).toBe('policies down');
    expect(results).toEqual([]);
    expect(loadSupportedPolicies).toHaveBeenCalledTimes(2);
    expect(checkCompatibility).not.toHaveBeenCalled();
  });
});
