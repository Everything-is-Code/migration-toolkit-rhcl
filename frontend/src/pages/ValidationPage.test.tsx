import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import type { ConversionResultItem } from '../api/types';
import { conversionResultsFingerprint } from '../components/conversion/conversionWorkflowState';

const mockValidate = vi.fn();
const mockSetAppState = vi.fn();
const mockNavigate = vi.fn();

const mockAppState = {
  connection: { url: '', accessToken: '', tenant: '', connected: false },
  selectedServices: [],
  conversionResults: [] as ConversionResultItem[],
  namespace: 'default',
  clusterVersions: null,
  clusterProfile: 'auto' as const,
  validationSnapshot: null,
};

vi.mock('../api/client', () => ({
  validationApi: { validate: (...args: unknown[]) => mockValidate(...args) },
}));

vi.mock('../components/AppStateContext', () => ({
  useAppState: () => ({ appState: mockAppState, setAppState: mockSetAppState }),
  AppStateProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('../components/conversion/useClearStaleConversionResults', () => ({
  useClearStaleConversionResults: () => {},
}));

vi.mock('../components/conversion/useClearStaleValidationSnapshot', () => ({
  useClearStaleValidationSnapshot: () => {},
}));

import ValidationPage from './ValidationPage';

const conversionResult = (): ConversionResultItem => ({
  serviceId: 'svc1',
  serviceName: 'My API',
  packageName: 'my-api',
  historyId: 10,
  compatibilityScore: 88,
  files: ['gateway.yaml'],
  yamlFiles: { 'gateway.yaml': 'kind: Gateway\n' },
});

describe('ValidationPage snapshot persistence', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAppState.conversionResults = [conversionResult()];
  });

  afterEach(() => cleanup());

  it('writes validationSnapshot to app state after successful validate', async () => {
    mockValidate.mockResolvedValue({
      data: { valid: true, items: [{ check: 'yaml', status: 'OK', message: 'ok' }] },
    });

    render(<ValidationPage />);
    await act(async () => {
      await userEvent.click(screen.getByRole('button', { name: 'validation.btnRun' }));
    });

    expect(mockValidate).toHaveBeenCalledWith(conversionResult().yamlFiles);
    expect(mockSetAppState).toHaveBeenCalled();
    const updater = mockSetAppState.mock.calls.at(-1)?.[0];
    expect(typeof updater).toBe('function');
    const next = updater!(mockAppState);
    expect(next.validationSnapshot).toEqual({
      fingerprint: conversionResultsFingerprint([conversionResult()]),
      results: {
        svc1: { valid: true, items: [{ check: 'yaml', status: 'OK', message: 'ok' }] },
      },
    });
  });

  it('writes ERROR validation into snapshot when validate API fails', async () => {
    mockValidate.mockRejectedValue(new Error('network down'));

    render(<ValidationPage />);
    await act(async () => {
      await userEvent.click(screen.getByRole('button', { name: 'validation.btnRun' }));
    });

    const updater = mockSetAppState.mock.calls.at(-1)?.[0];
    const next = updater!(mockAppState);
    expect(next.validationSnapshot?.results.svc1.valid).toBe(false);
    expect(next.validationSnapshot?.results.svc1.items[0].status).toBe('ERROR');
  });

  it('shows warning when no conversion results exist', () => {
    mockAppState.conversionResults = [];
    render(<ValidationPage />);
    expect(screen.getByText('validation.warningTitle')).toBeTruthy();
  });
});
