import React, { createContext, useContext, useState, useEffect } from 'react';
import {
  ConnectionRequest,
  ApiService,
  ConversionResultItem,
  ClusterVersionsResponse,
  ClusterProfile,
} from '../api/types';
import { loadPersistedConnection, savePersistedConnection } from '../utils/appStateStorage';

export interface AppState {
  connection: ConnectionRequest & { connected: boolean };
  selectedServices: ApiService[];
  conversionResults: ConversionResultItem[];
  namespace: string;
  /** Resolved cluster versions + capabilities from GET /api/cluster/versions. */
  clusterVersions: ClusterVersionsResponse | null;
  /** Selected profile override (auto | ocp-4.19 | ocp-4.21). */
  clusterProfile: ClusterProfile;
}

// TODO(#172): consider narrower setters (e.g. setConnection, setConversionResults) to
// reduce coupling — components currently receive the full setAppState updater.
interface AppStateContextValue {
  appState: AppState;
  setAppState: React.Dispatch<React.SetStateAction<AppState>>;
}

const AppStateContext = createContext<AppStateContextValue | undefined>(undefined);

export const AppStateProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [appState, setAppState] = useState<AppState>(() => {
    const persisted = loadPersistedConnection();
    // I-2: never restore connected:true without a non-empty token (load already clears token).
    const token = persisted?.connection?.accessToken ?? '';
    const connected =
      Boolean(token.trim()) && Boolean(persisted?.connection?.connected);
    return {
      connection: {
        url: persisted?.connection?.url ?? '',
        accessToken: token,
        tenant: persisted?.connection?.tenant ?? '',
        connected,
      },
      selectedServices: [],
      conversionResults: [],
      namespace: persisted?.namespace || 'default',
      clusterVersions: null,
      clusterProfile: persisted?.clusterProfile || 'auto',
    };
  });

  // Keep 3scale connection across route changes / HMR remounts for this browser tab.
  useEffect(() => {
    savePersistedConnection({
      connection: appState.connection,
      namespace: appState.namespace,
      clusterProfile: appState.clusterProfile,
    });
  }, [appState.connection, appState.namespace, appState.clusterProfile]);

  return (
    <AppStateContext.Provider value={{ appState, setAppState }}>
      {children}
    </AppStateContext.Provider>
  );
};

export const useAppState = (): AppStateContextValue => {
  const ctx = useContext(AppStateContext);
  if (!ctx) {
    throw new Error('useAppState must be used within AppStateProvider');
  }
  return ctx;
};
