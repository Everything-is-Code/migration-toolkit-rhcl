import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { HistoryListPage } from './types';

const getMock = vi.fn(() =>
  Promise.resolve({
    data: {
      items: [{ id: 1, status: 'COMPLETED', createdAt: '2026-01-01T00:00:00Z' }],
      total: 1,
      page: 0,
      size: 50,
      hasMore: false,
    } satisfies HistoryListPage,
  }),
);

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

describe('historyApi.list envelope', () => {
  beforeEach(() => {
    getMock.mockClear();
  });

  it('requests 0-based page/size and types the envelope response', async () => {
    const { historyApi } = await import('./client');

    const resp = await historyApi.list(1, 20);
    const data: HistoryListPage = resp.data;

    expect(getMock).toHaveBeenCalledWith('/api/history', {
      params: { page: 1, size: 20 },
    });
    expect(Array.isArray(data.items)).toBe(true);
    expect(data.total).toBe(1);
    expect(data.page).toBe(0);
    expect(data.size).toBe(50);
    expect(data.hasMore).toBe(false);
    // Bare-array assumption must not type-check; envelope has items.
    expect('items' in data).toBe(true);
  });
});
