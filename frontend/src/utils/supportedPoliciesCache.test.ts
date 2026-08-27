import { beforeEach, describe, expect, it, vi } from 'vitest';

const getMock = vi.fn();
const putMock = vi.fn();

vi.mock('../api/client', () => ({
  settingsApi: {
    get: (...args: unknown[]) => getMock(...args),
    put: (...args: unknown[]) => putMock(...args),
  },
}));

describe('loadSupportedPolicies module cache', () => {
  beforeEach(async () => {
    getMock.mockReset();
    putMock.mockReset();
    const { invalidateSupportedPoliciesCache } = await import('./supportedPolicies');
    invalidateSupportedPoliciesCache();
  });

  it('reuses one GET for concurrent/repeated loads until invalidated', async () => {
    getMock.mockResolvedValue({
      data: { value: JSON.stringify(['Logging', 'Header Modification']) },
    });

    const { loadSupportedPolicies } = await import('./supportedPolicies');

    const [a, b] = await Promise.all([loadSupportedPolicies(), loadSupportedPolicies()]);
    const c = await loadSupportedPolicies();

    expect(getMock).toHaveBeenCalledTimes(1);
    expect(a).toEqual(b);
    expect(c).toEqual(a);
    expect(a).toContain('Logging');
  });

  it('fetches again after invalidateSupportedPoliciesCache', async () => {
    getMock
      .mockResolvedValueOnce({ data: { value: JSON.stringify(['Logging']) } })
      .mockResolvedValueOnce({ data: { value: JSON.stringify(['Retry']) } });

    const {
      loadSupportedPolicies,
      invalidateSupportedPoliciesCache,
    } = await import('./supportedPolicies');

    await loadSupportedPolicies();
    invalidateSupportedPoliciesCache();
    const second = await loadSupportedPolicies();

    expect(getMock).toHaveBeenCalledTimes(2);
    expect(second).toContain('Retry');
  });

  it('seedSupportedPoliciesCache skips GET until next invalidate', async () => {
    const {
      loadSupportedPolicies,
      seedSupportedPoliciesCache,
      invalidateSupportedPoliciesCache,
    } = await import('./supportedPolicies');

    seedSupportedPoliciesCache(['Edge Limiting']);
    const seeded = await loadSupportedPolicies();
    expect(getMock).not.toHaveBeenCalled();
    expect(seeded).toEqual(['Edge Limiting']);

    invalidateSupportedPoliciesCache();
    getMock.mockResolvedValue({ data: { value: JSON.stringify(['CORS Request Handling']) } });
    const after = await loadSupportedPolicies();
    expect(getMock).toHaveBeenCalledTimes(1);
    expect(after).toContain('CORS Request Handling');
  });
});

describe('DEFAULT_SUPPORTED_POLICIES', () => {
  it('includes Maintenance Mode for compatibility SUPPORTED scoring', async () => {
    const { DEFAULT_SUPPORTED_POLICIES, withDefaultSupportedPolicies } = await import(
      './supportedPolicies'
    );
    expect(DEFAULT_SUPPORTED_POLICIES).toContain('Maintenance Mode');
    expect(withDefaultSupportedPolicies([])).toContain('Maintenance Mode');
  });
});
