import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

const mockUploadZip = vi.fn();
const mockDownloadZip = vi.fn();
const mockApply = vi.fn();

vi.mock('../api/client', () => ({
  importApi: { uploadZip: (...args: unknown[]) => mockUploadZip(...args) },
  downloadApi: { downloadZip: (...args: unknown[]) => mockDownloadZip(...args) },
  applyApi: { apply: (...args: unknown[]) => mockApply(...args) },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('../utils/fixHttpRoutePort', () => ({
  fixHttpRoutePort: (yaml: string) => yaml,
}));

vi.mock('../components/import/TestInfoPanel', () => ({
  default: () => <div data-testid="test-info-panel" />,
}));
vi.mock('../components/import/YamlDropzone', () => ({
  default: ({ onFileSelected }: { onFileSelected: (f: File) => void }) => (
    <button data-testid="dropzone" onClick={() => onFileSelected(new File(['content'], 'test.zip'))}>
      Upload
    </button>
  ),
}));
vi.mock('../components/import/YamlDiffViewer', () => ({
  default: () => <div data-testid="yaml-diff" />,
}));
vi.mock('../components/import/ImportResultTable', () => ({
  default: ({ results }: { results: { resource: string; success: boolean }[] }) => (
    <div data-testid="result-table">{results.length} results</div>
  ),
}));
vi.mock('../components/import/NamespaceFormCard', () => ({
  default: ({ onApply, onDownload }: { onApply: () => void; onDownload: () => void }) => (
    <div>
      <button data-testid="apply-btn" onClick={onApply}>Apply</button>
      <button data-testid="download-btn" onClick={onDownload}>Download</button>
    </div>
  ),
}));
vi.mock('../components/import/FileInfoBar', () => ({
  default: () => <div data-testid="file-bar" />,
}));
vi.mock('../components/import/ManualSteps', () => ({
  default: () => <div data-testid="manual-steps" />,
}));

import ImportPage from './ImportPage';

describe('ImportPage orchestration', () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(() => cleanup());

  it('calls importApi.uploadZip and renders file list on success', async () => {
    mockUploadZip.mockResolvedValue({
      data: { files: { 'gateway.yaml': 'kind: Gateway', 'httproute.yaml': 'kind: HTTPRoute' } },
    });

    render(<ImportPage />);
    await act(async () => {
      await userEvent.click(screen.getByTestId('dropzone'));
    });

    expect(mockUploadZip).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('file-bar')).toBeTruthy();
    expect(screen.getByTestId('yaml-diff')).toBeTruthy();
  });

  it('calls applyApi.apply and shows results', async () => {
    mockUploadZip.mockResolvedValue({
      data: { files: { 'gateway.yaml': 'kind: Gateway' } },
    });
    mockApply.mockResolvedValue({
      data: { results: [{ resource: 'gateway.yaml', success: true }] },
    });

    render(<ImportPage />);
    await act(async () => {
      await userEvent.click(screen.getByTestId('dropzone'));
    });
    await act(async () => {
      await userEvent.click(screen.getByTestId('apply-btn'));
    });

    expect(mockApply).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('result-table')).toBeTruthy();
  });

  it('calls downloadApi.downloadZip on download click', async () => {
    mockUploadZip.mockResolvedValue({
      data: { files: { 'gateway.yaml': 'kind: Gateway' } },
    });
    const blob = new Blob(['zip'], { type: 'application/zip' });
    mockDownloadZip.mockResolvedValue({ data: blob });

    const mockCreateObjectURL = vi.fn().mockReturnValue('blob:mock');
    const mockRevokeObjectURL = vi.fn();
    globalThis.URL.createObjectURL = mockCreateObjectURL;
    globalThis.URL.revokeObjectURL = mockRevokeObjectURL;

    render(<ImportPage />);
    await act(async () => {
      await userEvent.click(screen.getByTestId('dropzone'));
    });
    await act(async () => {
      await userEvent.click(screen.getByTestId('download-btn'));
    });

    expect(mockDownloadZip).toHaveBeenCalledTimes(1);
  });

  it('shows error on upload failure', async () => {
    mockUploadZip.mockRejectedValue({
      response: { data: { error: 'bad zip' } },
    });

    render(<ImportPage />);
    await act(async () => {
      await userEvent.click(screen.getByTestId('dropzone'));
    });

    expect(screen.getByText(/import.errorUpload/)).toBeTruthy();
  });
});
