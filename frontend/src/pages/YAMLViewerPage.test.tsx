/// @vitest-environment jsdom
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { ConversionResultItem } from '../api/types';

const mockNavigate = vi.fn();

let mockAppState: { conversionResults: ConversionResultItem[] };
const mockSetAppState = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('../components/AppStateContext', () => ({
  useAppState: () => ({ appState: mockAppState, setAppState: mockSetAppState }),
}));

vi.mock('../components/yaml/YamlFileTabs', () => ({
  default: ({
    renderTabContent,
  }: {
    renderTabContent: (filename: string, original: string) => React.ReactNode;
  }) => (
    <div data-testid="yaml-tabs">
      {renderTabContent('gateway.yaml', mockAppState.conversionResults[0]?.yamlFiles?.['gateway.yaml'] ?? '')}
    </div>
  ),
}));

vi.mock('../components/yaml/YamlEditorPanel', () => ({
  default: ({ editedContent }: { editedContent: string }) => (
    <pre data-testid="yaml-content">{editedContent}</pre>
  ),
}));

import YAMLViewerPage from './YAMLViewerPage';

const result = (
  serviceId: string,
  yaml: string,
  historyId: number,
): ConversionResultItem => ({
  serviceId,
  serviceName: serviceId,
  packageName: `${serviceId}-pkg`,
  historyId,
  compatibilityScore: 90,
  files: ['gateway.yaml'],
  yamlFiles: { 'gateway.yaml': yaml },
});

describe('YAMLViewerPage edits resync', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAppState = {
      conversionResults: [result('api-a', 'name: api-a-gateway', 10)],
    };
  });

  afterEach(() => cleanup());

  it('shows yaml from current conversion results', () => {
    render(<YAMLViewerPage />);
    expect(screen.getByTestId('yaml-content').textContent).toBe('name: api-a-gateway');
  });

  it('rebuilds edits when conversion results fingerprint changes', () => {
    const { rerender } = render(<YAMLViewerPage />);
    expect(screen.getByTestId('yaml-content').textContent).toBe('name: api-a-gateway');

    mockAppState = {
      conversionResults: [result('api-b', 'name: api-b-gateway', 20)],
    };
    rerender(<YAMLViewerPage />);

    expect(screen.getByTestId('yaml-content').textContent).toBe('name: api-b-gateway');
  });

  it('shows warning when no yaml results', () => {
    mockAppState = { conversionResults: [] };
    render(<YAMLViewerPage />);
    expect(screen.getByText('yamlViewer.warningTitle')).toBeTruthy();
  });
});
