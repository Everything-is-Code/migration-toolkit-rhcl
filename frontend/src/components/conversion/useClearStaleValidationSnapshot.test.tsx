/// @vitest-environment jsdom
import React, { useEffect, useRef } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import type { ConversionResultItem, ValidationSnapshot } from '../../api/types';
import { AppStateProvider, useAppState } from '../AppStateContext';
import { useClearStaleValidationSnapshot } from './useClearStaleValidationSnapshot';

vi.mock('../../utils/appStateStorage', () => ({
  loadPersistedConnection: () => null,
  savePersistedConnection: vi.fn(),
}));

const conversionResult = (): ConversionResultItem => ({
  serviceId: 'svc1',
  serviceName: 'API',
  packageName: 'api-pkg',
  historyId: 10,
  compatibilityScore: 90,
  files: ['gateway.yaml'],
  yamlFiles: { 'gateway.yaml': 'kind: Gateway\n' },
});

function StateSeeder({
  children,
  conversionResults,
  validationSnapshot,
}: {
  children: React.ReactNode;
  conversionResults: ConversionResultItem[];
  validationSnapshot: ValidationSnapshot | null;
}) {
  const { setAppState } = useAppState();
  const seeded = useRef(false);

  useEffect(() => {
    if (seeded.current) {
      return;
    }
    seeded.current = true;
    setAppState(prev => ({
      ...prev,
      conversionResults,
      validationSnapshot,
    }));
  }, [conversionResults, setAppState, validationSnapshot]);

  return <>{children}</>;
}

function createWrapper(
  conversionResults: ConversionResultItem[],
  validationSnapshot: ValidationSnapshot | null,
) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <AppStateProvider>
        <StateSeeder conversionResults={conversionResults} validationSnapshot={validationSnapshot}>
          {children}
        </StateSeeder>
      </AppStateProvider>
    );
  };
}

describe('useClearStaleValidationSnapshot', () => {
  it('clears validation snapshot when conversion fingerprint changes', async () => {
    const staleSnapshot: ValidationSnapshot = {
      fingerprint: 'svc1:99',
      results: { svc1: { valid: true, items: [{ check: 'ok', status: 'OK', message: 'ok' }] } },
    };

    const { result } = renderHook(
      () => ({
        hook: useClearStaleValidationSnapshot(),
        state: useAppState(),
      }),
      { wrapper: createWrapper([conversionResult()], staleSnapshot) },
    );

    await waitFor(() => {
      expect(result.current.state.appState.validationSnapshot).toBeNull();
    });
  });

  it('keeps validation snapshot when fingerprint matches', async () => {
    const snapshot: ValidationSnapshot = {
      fingerprint: 'svc1:10',
      results: { svc1: { valid: true, items: [{ check: 'ok', status: 'OK', message: 'ok' }] } },
    };

    const { result } = renderHook(
      () => ({
        hook: useClearStaleValidationSnapshot(),
        state: useAppState(),
      }),
      { wrapper: createWrapper([conversionResult()], snapshot) },
    );

    await waitFor(() => {
      expect(result.current.state.appState.validationSnapshot).toEqual(snapshot);
    });
  });
});
