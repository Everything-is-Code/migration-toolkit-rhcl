/// @vitest-environment jsdom
import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { AppStateProvider, useAppState } from './AppStateContext';

vi.mock('../utils/appStateStorage', () => ({
  loadPersistedConnection: () => null,
  savePersistedConnection: vi.fn(),
}));

describe('AppStateContext', () => {
  it('throws when useAppState is called outside AppStateProvider', () => {
    expect(() => renderHook(() => useAppState())).toThrow(
      'useAppState must be used within AppStateProvider',
    );
  });

  it('provides default state inside AppStateProvider', () => {
    const { result } = renderHook(() => useAppState(), {
      wrapper: ({ children }) => <AppStateProvider>{children}</AppStateProvider>,
    });
    expect(result.current.appState.namespace).toBe('default');
    expect(result.current.appState.connection.connected).toBe(false);
    expect(result.current.appState.selectedServices).toEqual([]);
  });
});
