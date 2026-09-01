/// @vitest-environment jsdom
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';

const mockGetVersions = vi.fn();
const mockSetAppState = vi.fn();

vi.mock('../api/client', () => ({
  clusterApi: {
    getVersions: (...args: unknown[]) => mockGetVersions(...args),
  },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('../utils/clusterCapabilityUi', () => ({
  shouldShowClusterVersionsCard: () => true,
}));

vi.mock('../components/AppStateContext', () => ({
  useAppState: () => ({ setAppState: mockSetAppState }),
}));

vi.mock('../components/connection/ConnectionForm', () => ({
  default: ({ onConnected }: { onConnected: () => void }) => (
    <button data-testid="connect-btn" onClick={onConnected}>
      Connect
    </button>
  ),
}));

vi.mock('../components/connection/ClusterVersionsPanel', () => ({
  default: ({ versionsError }: { versionsError: string | null }) => (
    <div data-testid="versions-panel">{versionsError ?? 'ok'}</div>
  ),
}));

import ConnectionPage from './ConnectionPage';

describe('ConnectionPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetVersions.mockResolvedValue({
      data: { profile: 'openshift', capabilities: { clusterReachable: true } },
    });
  });

  afterEach(() => cleanup());

  it('loads cluster versions on mount and updates app state', async () => {
    render(<ConnectionPage />);

    await waitFor(() => expect(mockGetVersions).toHaveBeenCalledWith(false));
    expect(mockSetAppState).toHaveBeenCalled();
    expect(screen.getByText('connection.title')).toBeTruthy();
    expect(screen.getByTestId('versions-panel')).toBeTruthy();
  });

  it('shows versions error when clusterApi.getVersions fails', async () => {
    mockGetVersions.mockRejectedValue({
      response: { data: { error: 'cluster unreachable' } },
    });

    render(<ConnectionPage />);

    await waitFor(() => {
      expect(screen.getByTestId('versions-panel').textContent).not.toBe('ok');
    });
  });
});
