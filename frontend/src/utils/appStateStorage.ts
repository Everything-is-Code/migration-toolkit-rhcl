import type { ClusterProfile } from '../api/types';
import type { ConnectionRequest } from '../api/types';

const STORAGE_KEY = 'rhcl.toolkit.connection.v1';

export type PersistedConnectionSlice = {
  connection: ConnectionRequest & { connected: boolean };
  namespace: string;
  clusterProfile: ClusterProfile;
};

/**
 * Sanitize a loaded slice (I-2):
 * - Never restore accessToken from sessionStorage.
 * - Never yield connected:true without a non-empty in-memory token
 *   (token is cleared on load → connected is always false after refresh).
 */
function sanitizeLoaded(
  parsed: Partial<PersistedConnectionSlice>,
): Partial<PersistedConnectionSlice> {
  // Tokens are never restored from sessionStorage; without a token, connected must be false.
  const restoredToken = '';
  const connected =
    Boolean(restoredToken.trim()) && Boolean(parsed.connection?.connected);
  return {
    ...parsed,
    connection: {
      url: parsed.connection?.url ?? '',
      accessToken: restoredToken,
      tenant: parsed.connection?.tenant,
      connected,
    },
  };
}

export function loadPersistedConnection(): Partial<PersistedConnectionSlice> | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<PersistedConnectionSlice>;
    if (!parsed || typeof parsed !== 'object') return null;
    return sanitizeLoaded(parsed);
  } catch {
    return null;
  }
}

export function savePersistedConnection(slice: PersistedConnectionSlice): void {
  try {
    // I-2: never persist accessToken; always store connected:false.
    const toStore = {
      connection: {
        url: slice.connection.url,
        tenant: slice.connection.tenant,
        connected: false as const,
      },
      namespace: slice.namespace,
      clusterProfile: slice.clusterProfile,
    };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(toStore));
  } catch {
    // Quota / private mode — ignore; in-memory state still works for the session.
  }
}

export function clearPersistedConnection(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}
