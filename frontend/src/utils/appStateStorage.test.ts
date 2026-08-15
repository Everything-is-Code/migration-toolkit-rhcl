import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  clearPersistedConnection,
  loadPersistedConnection,
  savePersistedConnection,
  type PersistedConnectionSlice,
} from './appStateStorage';

const STORAGE_KEY = 'rhcl.toolkit.connection.v1';

function memorySessionStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear: () => map.clear(),
    getItem: (key: string) => (map.has(key) ? map.get(key)! : null),
    key: (index: number) => Array.from(map.keys())[index] ?? null,
    removeItem: (key: string) => {
      map.delete(key);
    },
    setItem: (key: string, value: string) => {
      map.set(key, value);
    },
  };
}

describe('appStateStorage', () => {
  beforeEach(() => {
    Object.defineProperty(globalThis, 'sessionStorage', {
      configurable: true,
      value: memorySessionStorage(),
    });
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('savePersistedConnection omits accessToken and never persists connected:true (I-2)', () => {
    const slice: PersistedConnectionSlice = {
      connection: {
        url: 'https://admin.example.com',
        accessToken: 'secret-token',
        tenant: 'tenant-a',
        connected: true,
      },
      namespace: '3scale',
      clusterProfile: 'auto',
    };

    savePersistedConnection(slice);

    const raw = sessionStorage.getItem(STORAGE_KEY);
    expect(raw).toBeTruthy();
    const stored = JSON.parse(raw!);
    expect(stored.connection.accessToken).toBeUndefined();
    expect(JSON.stringify(stored)).not.toContain('secret-token');
    expect(stored.connection.connected).toBe(false);
    expect(stored.connection.url).toBe('https://admin.example.com');
    expect(stored.connection.tenant).toBe('tenant-a');
    expect(stored.namespace).toBe('3scale');
    expect(stored.clusterProfile).toBe('auto');
  });

  it('loadPersistedConnection never restores connected:true without a non-empty token', () => {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        connection: {
          url: 'https://admin.example.com',
          accessToken: 'legacy-token',
          tenant: 't1',
          connected: true,
        },
        namespace: 'default',
        clusterProfile: 'ocp-4.19',
      }),
    );

    const loaded = loadPersistedConnection();
    expect(loaded).not.toBeNull();
    expect(loaded!.connection?.accessToken).toBe('');
    expect(loaded!.connection?.connected).toBe(false);
    expect(loaded!.connection?.url).toBe('https://admin.example.com');
  });

  it('loadPersistedConnection forces connected false when token is empty', () => {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        connection: {
          url: 'https://admin.example.com',
          accessToken: '   ',
          connected: true,
        },
        namespace: 'ns',
        clusterProfile: 'auto',
      }),
    );

    const loaded = loadPersistedConnection();
    expect(loaded!.connection?.accessToken).toBe('');
    expect(loaded!.connection?.connected).toBe(false);
  });

  it('clearPersistedConnection removes the storage key (I-3)', () => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ connection: { url: 'x' } }));
    clearPersistedConnection();
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});
