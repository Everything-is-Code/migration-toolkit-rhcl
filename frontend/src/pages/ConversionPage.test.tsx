import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const harness = vi.hoisted(() => ({
  namespace: 'test-ns',
  setAppState: vi.fn(),
}));
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
    get namespace() { return harness.namespace; },
    clusterVersions: null,
    clusterProfile: 'auto' as const,
    validationSnapshot: null,
  };
  return {
    useAppState: () => ({ appState: state, setAppState: harness.setAppState }),
    AppStateProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, defaultOrOpts?: string | Record<string, unknown>) => {
      if (typeof defaultOrOpts === 'string') return defaultOrOpts;
      if (defaultOrOpts && typeof defaultOrOpts === 'object' && 'message' in defaultOrOpts) {
        return String(defaultOrOpts.message);
      }
      return key;
    },
  }),
}));

vi.mock('../components/conversion/useClearStaleConversionResults', () => ({
  useClearStaleConversionResults: () => {},
}));

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
      <button data-testid="convert-dns-btn" onClick={() => onConvert({
        isExternal: false, externalBackendUrl: '',
        loggingTarget: 'gateway', anonymousTarget: 'httproute',
        ipCheckMode: 'authorizationPolicy', includeMigratedFromLabel: true,
        includeTlsPolicy: false, tlsIssuerKind: '', tlsIssuerName: '',
        includeDnsPolicy: true, dnsHostname: '', dnsProviderSecretName: '',
      })}>
        Convert DNS
      </button>
      <button data-testid="convert-explicit-ns" onClick={() => onConvert({
        isExternal: false, externalBackendUrl: '',
        loggingTarget: 'gateway', anonymousTarget: 'httproute',
        ipCheckMode: 'authorizationPolicy', includeMigratedFromLabel: true,
        includeTlsPolicy: false, tlsIssuerKind: '', tlsIssuerName: '',
        includeDnsPolicy: false, dnsHostname: '', dnsProviderSecretName: '',
        namespace: '  business-apis  ',
      })}>
        Convert explicit namespace
      </button>
      <button data-testid="convert-blank-ns" onClick={() => onConvert({
        isExternal: false, externalBackendUrl: '',
        loggingTarget: 'gateway', anonymousTarget: 'httproute',
        ipCheckMode: 'authorizationPolicy', includeMigratedFromLabel: true,
        includeTlsPolicy: false, tlsIssuerKind: '', tlsIssuerName: '',
        includeDnsPolicy: false, dnsHostname: '', dnsProviderSecretName: '',
        namespace: '   ',
      })}>
        Convert blank namespace
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
  beforeEach(() => {
    harness.namespace = 'test-ns';
    vi.clearAllMocks();
  });
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

    expect(screen.getByTestId('error').textContent).toContain('backend exploded');
  });

  it('requires dns hostname when includeDnsPolicy is enabled', async () => {
    render(<ConversionPage />);
    await act(async () => {
      await userEvent.click(screen.getByTestId('convert-dns-btn'));
    });

    expect(mockConvert).not.toHaveBeenCalled();
    expect(screen.getByTestId('error').textContent).toContain('Gateway hostname is required');
  });

  it('trims an explicit namespace and stores it', async () => {
    mockConvert.mockResolvedValue({ data: { results: [{ name: 'gw', yaml: 'kind: Gateway' }] } } as never);

    render(<ConversionPage />);
    await act(async () => {
      await userEvent.click(screen.getByTestId('convert-explicit-ns'));
    });

    const call = mockConvert.mock.calls[0][0] as Record<string, unknown>;
    expect(call.namespace).toBe('business-apis');
    const updater = harness.setAppState.mock.calls[0][0] as (prev: { namespace: string }) => { namespace: string };
    expect(updater({ namespace: 'old' }).namespace).toBe('business-apis');
  });

  it('rejects a blank namespace', async () => {
    harness.namespace = '   ';

    render(<ConversionPage />);
    await act(async () => {
      await userEvent.click(screen.getByTestId('convert-blank-ns'));
    });

    expect(mockConvert).not.toHaveBeenCalled();
    expect(screen.getByTestId('error').textContent).toContain('conversion.namespaceHelper');
  });
});
