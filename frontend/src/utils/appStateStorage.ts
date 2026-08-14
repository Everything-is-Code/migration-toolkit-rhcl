import type { ClusterProfile } from '../api/types';
import type { ConnectionRequest } from '../api/types';

const STORAGE_KEY = 'rhcl.toolkit.connection.v1';

export type PersistedConnectionSlice = {
  connection: ConnectionRequest & { connected: boolean };
  namespace: string;
  clusterProfile: ClusterProfile;
};

export function loadPersistedConnection(): Partial<PersistedConnectionSlice> | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<PersistedConnectionSlice>;
    if (!parsed || typeof parsed !== 'object') return null;
    return parsed;
  } catch {
    return null;
  }
}

export function savePersistedConnection(slice: PersistedConnectionSlice): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(slice));
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
