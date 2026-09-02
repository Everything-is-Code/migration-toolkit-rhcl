/// @vitest-environment jsdom
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

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
    expect(mockSetAppState).toHaveBeenCalledTimes(1);
    const updater = mockSetAppState.mock.calls[0][0] as (prev: { clusterProfile: string }) => {
      clusterVersions: { profile: string };
      clusterProfile: string;
    };
    const next = updater({ clusterProfile: 'auto' });
    expect(next.clusterVersions.profile).toBe('openshift');
    expect(next.clusterProfile).toBe('openshift');
    expect(screen.getByText('connection.title')).toBeTruthy();
    expect(screen.getByTestId('versions-panel')).toBeTruthy();
  });

  it('refreshes cluster versions when connection succeeds', async () => {
    const user = userEvent.setup();
    render(<ConnectionPage />);

    await waitFor(() => expect(mockGetVersions).toHaveBeenCalledWith(false));
    mockGetVersions.mockClear();

    await user.click(screen.getByTestId('connect-btn'));

    await waitFor(() => expect(mockGetVersions).toHaveBeenCalledWith(true));
    expect(mockGetVersions).toHaveBeenCalledTimes(1);
  });

  it('shows versions error when clusterApi.getVersions fails', async () => {
    mockGetVersions.mockRejectedValue({
      response: { data: { error: 'cluster unreachable' } },
    });

    render(<ConnectionPage />);

    await waitFor(() => {
      expect(screen.getByTestId('versions-panel')).toHaveTextContent('cluster unreachable');
    });
    expect(mockSetAppState).not.toHaveBeenCalled();
  });
});
