/// @vitest-environment jsdom
import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../i18n';
import type { ClusterProfile, ClusterVersionsResponse } from '../../api/types';

vi.mock('../../api/client', () => ({
  settingsApi: { put: vi.fn() },
}));

const baseCapabilities = {
  corsNative: false,
  kuadrantPresent: false,
  ossmPresent: false,
  ossmMatchesOcp: false,
  timeoutsSupported: true,
  retriesSupported: true,
};

const unreachableVersions: ClusterVersionsResponse = {
  ocp: '4.19.0',
  gatewayApi: '1.2.1',
  kuadrant: null,
  ossm: null,
  ossmExpectedForOcp: '2.6',
  capabilities: { ...baseCapabilities, clusterReachable: false },
  source: 'default',
  profile: 'auto',
};

const reachableVersions: ClusterVersionsResponse = {
  ocp: '4.21.0',
  gatewayApi: '1.3.0',
  kuadrant: '1.4.2',
  ossm: '3.0.0',
  ossmExpectedForOcp: '3.0',
  capabilities: {
    ...baseCapabilities,
    clusterReachable: true,
    corsNative: true,
    kuadrantPresent: true,
    ossmPresent: true,
    ossmMatchesOcp: true,
  },
  source: 'detected',
  profile: 'auto',
};

const profileVersions: ClusterVersionsResponse = {
  ocp: '4.21.0',
  gatewayApi: '1.3.0',
  kuadrant: null,
  ossm: '3.0',
  ossmExpectedForOcp: '3.0',
  capabilities: {
    ...baseCapabilities,
    clusterReachable: true,
    corsNative: true,
    kuadrantPresent: true,
    ossmPresent: true,
    ossmMatchesOcp: true,
  },
  source: 'profile',
  profile: 'ocp-4.21',
};

const { mockAppState } = vi.hoisted(() => ({
  mockAppState: {
    clusterVersions: null as ClusterVersionsResponse | null,
    clusterProfile: 'auto' as ClusterProfile,
  },
}));

vi.mock('../AppStateContext', () => ({
  useAppState: () => ({
    appState: mockAppState,
    setAppState: vi.fn(),
  }),
  AppStateProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

import ClusterVersionsPanel from './ClusterVersionsPanel';

const renderPanel = () =>
  render(
    <I18nextProvider i18n={i18n}>
      <ClusterVersionsPanel
        versionsLoading={false}
        versionsError={null}
        profileSaving={false}
        onLoadVersions={async () => {}}
        onProfileSavingChange={() => {}}
        onVersionsErrorChange={() => {}}
      />
    </I18nextProvider>,
  );

describe('ClusterVersionsPanel', () => {
  beforeEach(() => {
    cleanup();
    mockAppState.clusterVersions = unreachableVersions;
    mockAppState.clusterProfile = 'auto';
  });

  it('shows unreachable cluster warning when clusterReachable is false', () => {
    renderPanel();

    expect(screen.getByText('OpenShift cluster not reachable')).toBeTruthy();
    expect(screen.getByText(/Connect to cluster to detect RHCL\/Kuadrant/)).toBeTruthy();
    expect(screen.queryByText('CORS: ResponseHeaderModifier fallback')).toBeNull();
  });

  it('shows cluster connected label when cluster is reachable', () => {
    mockAppState.clusterVersions = reachableVersions;
    renderPanel();

    expect(screen.getByText('Cluster connected')).toBeTruthy();
    expect(screen.getByText('1.4.2')).toBeTruthy();
    expect(screen.queryByText('OpenShift cluster not reachable')).toBeNull();
  });

  it('shows profile banner when source is profile', () => {
    mockAppState.clusterVersions = profileVersions;
    mockAppState.clusterProfile = 'ocp-4.21';
    renderPanel();

    expect(screen.getByText('Manual cluster profile')).toBeTruthy();
    expect(screen.getByText(/Live cluster detection is skipped/)).toBeTruthy();
    expect(screen.queryByText('OpenShift cluster not reachable')).toBeNull();
  });
});
