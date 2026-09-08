import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import type { ConversionResultItem, ClusterVersionsResponse } from '../api/types';

const mockApply = vi.fn();
const mockDownloadZip = vi.fn();

vi.mock('../api/client', () => ({
  applyApi: { apply: (...args: unknown[]) => mockApply(...args) },
  downloadApi: { downloadZip: (...args: unknown[]) => mockDownloadZip(...args) },
}));

const { mockAppState } = vi.hoisted(() => ({
  mockAppState: {
    connection: { url: 'https://3scale.example.com', accessToken: 'tok', tenant: 't', connected: true },
    selectedServices: [],
    conversionResults: [] as ConversionResultItem[],
    namespace: 'test-ns',
    clusterVersions: null as ClusterVersionsResponse | null,
    clusterProfile: 'auto' as const,
    validationSnapshot: null as {
      fingerprint: string;
      results: Record<string, { valid: boolean; items: { check: string; status: string; message: string }[] }>;
    } | null,
  },
}));

vi.mock('../components/AppStateContext', () => ({
  useAppState: () => ({ appState: mockAppState, setAppState: vi.fn() }),
  AppStateProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts && 'namespace' in opts) return `${key}:${opts.namespace}`;
      return key;
    },
  }),
  Trans: ({ i18nKey, children }: { i18nKey?: string; children?: React.ReactNode }) => (
    <span data-testid={i18nKey}>{children ?? i18nKey}</span>
  ),
}));

vi.mock('../components/conversion/useClearStaleConversionResults', () => ({
  useClearStaleConversionResults: () => {},
}));

vi.mock('../components/conversion/useClearStaleValidationSnapshot', () => ({
  useClearStaleValidationSnapshot: () => {},
}));

vi.mock('../components/import/ImportResultTable', () => ({
  default: ({ results }: { results: unknown[] }) => (
    <div data-testid="result-table">{results.length} results</div>
  ),
}));

import DownloadPage from './DownloadPage';

const result = (yaml: Record<string, string>, serviceId = 'svc1'): ConversionResultItem => ({
  serviceId,
  serviceName: 'My API',
  packageName: 'my-api',
  historyId: 10,
  compatibilityScore: 88,
  files: Object.keys(yaml),
  yamlFiles: yaml,
});

describe('DownloadPage apply flow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAppState.clusterVersions = {
      profile: 'auto',
      source: 'detected',
      ocp: '4.16',
      gatewayApi: '1.2',
      kuadrant: '1.0',
      ossm: null,
      ossmExpectedForOcp: null,
      capabilities: {
        clusterReachable: true,
        corsNative: false,
        kuadrantPresent: true,
        ossmPresent: false,
        ossmMatchesOcp: true,
        timeoutsSupported: false,
        retriesSupported: false,
      },
    };
    mockAppState.validationSnapshot = {
      fingerprint: 'svc1:10',
      results: {
        svc1: { valid: true, items: [{ check: 'ok', status: 'OK', message: 'ok' }] },
      },
    };
    mockAppState.conversionResults = [result({ 'gateway.yaml': 'kind: Gateway\n' })];
  });

  afterEach(() => cleanup());

  it('disables apply when cluster is not reachable', () => {
    mockAppState.clusterVersions!.capabilities!.clusterReachable = false;
    render(<DownloadPage />);
    const applyBtn = screen.getByRole('button', { name: 'download.btnApplyCluster' });
    expect(applyBtn).toBeDisabled();
    expect(screen.getByText('download.applyDisabledClusterTitle')).toBeTruthy();
  });

  it('disables apply when validation snapshot is missing', () => {
    mockAppState.validationSnapshot = null;
    render(<DownloadPage />);
    expect(screen.getByRole('button', { name: 'download.btnApplyCluster' })).toBeDisabled();
  });

  it('disables apply when REPLACE_ME is present', () => {
    mockAppState.conversionResults = [result({ 'secret.yaml': 'client-id: REPLACE_ME\n' })];
    render(<DownloadPage />);
    expect(screen.getByRole('button', { name: 'download.btnApplyCluster' })).toBeDisabled();
  });

  it('calls applyApi after confirm with CONVERT source', async () => {
    mockApply.mockResolvedValue({
      data: { results: [{ fileName: 'gateway.yaml', success: true, message: 'ok' }] },
    });
    render(<DownloadPage />);
    await act(async () => {
      await userEvent.click(screen.getByRole('button', { name: 'download.btnApplyCluster' }));
    });
    await act(async () => {
      await userEvent.click(screen.getByRole('button', { name: 'download.applyConfirmAction' }));
    });
    expect(mockApply).toHaveBeenCalledWith(
      'test-ns',
      expect.objectContaining({ 'gateway.yaml': expect.any(String) }),
      'CONVERT',
      'my-api',
    );
    expect(screen.getByTestId('result-table')).toBeTruthy();
  });

  it('shows next steps with direct apply mention', () => {
    render(<DownloadPage />);
    expect(screen.getByTestId('download.step1')).toBeTruthy();
  });
});
