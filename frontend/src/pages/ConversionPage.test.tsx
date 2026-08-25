import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

vi.mock('../api/client', () => ({
  conversionApi: {
    convert: vi.fn(),
  },
}));

vi.mock('../utils/supportedPolicies', () => ({
  loadSupportedPolicies: vi.fn().mockResolvedValue(['3scale APIcast', 'Logging']),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  BrowserRouter: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../components/AppStateContext', () => {
  const state = {
    connection: { url: 'https://3scale.example.com', accessToken: 'tok', tenant: 't', connected: true },
    selectedServices: [{ id: 1, name: 'Svc1', systemName: 'svc1', policies: [] }],
    conversionResults: [],
    namespace: 'test-ns',
    clusterVersions: null,
    clusterProfile: 'auto' as const,
  };
  return {
    useAppState: () => ({ appState: state, setAppState: vi.fn() }),
    AppStateProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  };
});

vi.mock('../components/conversion/ConversionForm', () => ({
  default: ({ onConvert, loading, error }: {
    onConvert: (opts: Record<string, unknown>) => void;
    loading: boolean;
    error: string | null;
  }) => (
    <div>
      <button data-testid="convert-btn" onClick={() => onConvert({
        isExternal: false, externalBackendUrl: '',
        loggingTarget: 'gateway', anonymousTarget: 'httproute',
        ipCheckMode: 'authorizationPolicy', includeMigratedFromLabel: true,
        includeTlsPolicy: false, tlsIssuerKind: '', tlsIssuerName: '',
        includeDnsPolicy: false, dnsHostname: '', dnsProviderSecretName: '',
      })}>
        Convert
      </button>
      {loading && <span data-testid="loading">Loading...</span>}
      {error && <span data-testid="error">{error}</span>}
    </div>
  ),
}));

vi.mock('../components/conversion/ConversionResults', () => ({
  default: () => <div data-testid="results">Results</div>,
}));

import ConversionPage from './ConversionPage';
import { conversionApi } from '../api/client';

const mockConvert = vi.mocked(conversionApi.convert);

describe('ConversionPage handleConvert', () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(() => cleanup());

  it('calls conversionApi.convert with correct payload on success', async () => {
    mockConvert.mockResolvedValue({ data: { results: [{ name: 'gw', yaml: 'kind: Gateway' }] } } as never);

    render(<ConversionPage />);
    await act(async () => {
      await userEvent.click(screen.getByTestId('convert-btn'));
    });

    expect(mockConvert).toHaveBeenCalledTimes(1);
    const call = mockConvert.mock.calls[0][0] as Record<string, unknown>;
    expect(call.threescaleUrl).toBe('https://3scale.example.com');
    expect(call.namespace).toBe('test-ns');
    expect(call.serviceIds).toEqual([1]);
    expect(call.supportedPolicies).toEqual(['3scale APIcast', 'Logging']);
  });

  it('shows error message on API failure', async () => {
    mockConvert.mockRejectedValue({
      response: { data: { error: 'backend exploded' } },
    });

    render(<ConversionPage />);
    await act(async () => {
      await userEvent.click(screen.getByTestId('convert-btn'));
    });

    expect(screen.getByTestId('error')).toBeTruthy();
  });
});
