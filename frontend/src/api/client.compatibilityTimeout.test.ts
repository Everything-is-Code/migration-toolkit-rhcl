import { beforeEach, describe, expect, it, vi } from 'vitest';

const getMock = vi.fn(() => Promise.resolve({ data: {} }));

vi.mock('axios', () => ({
  default: {
    create: () => ({
      get: getMock,
      post: vi.fn(),
      delete: vi.fn(),
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() },
      },
    }),
  },
}));

vi.mock('../i18n', () => ({
  default: { language: 'en' },
}));

vi.mock('../utils/appStateStorage', () => ({
  clearPersistedConnection: vi.fn(),
}));

describe('servicesApi.checkCompatibility timeout', () => {
  beforeEach(() => {
    getMock.mockClear();
  });

  it('uses a 60s axios timeout on compatibility requests', async () => {
    const { servicesApi, COMPATIBILITY_REQUEST_TIMEOUT_MS } = await import('./client');

    expect(COMPATIBILITY_REQUEST_TIMEOUT_MS).toBe(60_000);

    await servicesApi.checkCompatibility(
      'svc-1',
      'https://3scale.example',
      'token',
      ['cors'],
    );

    expect(getMock).toHaveBeenCalledTimes(1);
    const callArgs = getMock.mock.calls[0] as unknown as [string, Record<string, unknown>];
    expect(callArgs[1]).toMatchObject({
      timeout: 60_000,
      params: {
        url: 'https://3scale.example',
        supportedPolicies: 'cors',
      },
    });
  });
});
