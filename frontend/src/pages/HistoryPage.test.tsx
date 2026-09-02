/// @vitest-environment jsdom
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mockList = vi.fn();
const mockDeleteByIds = vi.fn();

vi.mock('../api/client', () => ({
  historyApi: {
    list: (...args: unknown[]) => mockList(...args),
    deleteByIds: (...args: unknown[]) => mockDeleteByIds(...args),
  },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts?.count !== undefined ? `${key}:${opts.count}` : key,
  }),
}));

const sampleHistory = [
  {
    id: 1,
    serviceName: 'api-a',
    status: 'SUCCESS',
    createdAt: '2026-09-01T12:00:00Z',
  },
  {
    id: 2,
    serviceName: 'api-b',
    status: 'SUCCESS',
    createdAt: '2026-09-01T13:00:00Z',
  },
];

vi.mock('../components/history/HistoryToolbar', () => ({
  HistoryHeader: ({
    onOpenDeleteModal,
    onReload,
  }: {
    onOpenDeleteModal: () => void;
    onReload: () => void;
  }) => (
    <div>
      <button data-testid="reload-btn" onClick={onReload}>Reload</button>
      <button data-testid="delete-open-btn" onClick={onOpenDeleteModal}>Delete</button>
    </div>
  ),
  default: ({
    onToggleAll,
    selectedCount,
  }: {
    onToggleAll: () => void;
    selectedCount: number;
  }) => (
    <button data-testid="toggle-all-btn" onClick={onToggleAll}>
      Toggle all ({selectedCount})
    </button>
  ),
}));

vi.mock('../components/history/HistoryTable', () => ({
  default: ({
    history,
    selected,
    onToggleSelect,
  }: {
    history: { id: number; serviceName?: string }[];
    selected: Set<number>;
    onToggleSelect: (id: number) => void;
  }) => (
    <div data-testid="history-table">
      {history.map(entry => (
        <label key={entry.id}>
          <input
            type="checkbox"
            data-testid={`select-${entry.id}`}
            checked={selected.has(entry.id)}
            onChange={() => onToggleSelect(entry.id)}
          />
          {entry.serviceName}
        </label>
      ))}
    </div>
  ),
}));

vi.mock('../components/history/HistoryDeleteModal', () => ({
  default: ({
    isOpen,
    onConfirm,
    onClose,
  }: {
    isOpen: boolean;
    onConfirm: () => void;
    onClose: () => void;
  }) =>
    isOpen ? (
      <div data-testid="delete-modal">
        <button data-testid="delete-confirm-btn" onClick={onConfirm}>Confirm</button>
        <button data-testid="delete-cancel-btn" onClick={onClose}>Cancel</button>
      </div>
    ) : null,
}));

import HistoryPage from './HistoryPage';

describe('HistoryPage orchestration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockList.mockResolvedValue({
      data: { items: sampleHistory, total: 2, page: 0, size: 50, hasMore: false },
    });
    mockDeleteByIds.mockResolvedValue({});
  });

  afterEach(() => cleanup());

  it('loads history on mount', async () => {
    render(<HistoryPage />);

    await waitFor(() => expect(mockList).toHaveBeenCalledWith(0, 50));
    expect(screen.getByTestId('history-table')).toBeTruthy();
    expect(screen.getByText('api-a')).toBeTruthy();
  });

  it('selects rows and deletes via modal confirm', async () => {
    const user = userEvent.setup();
    render(<HistoryPage />);

    await waitFor(() => expect(screen.getByTestId('select-1')).toBeTruthy());

    await user.click(screen.getByTestId('select-1'));
    await user.click(screen.getByTestId('delete-open-btn'));
    expect(screen.getByTestId('delete-modal')).toBeTruthy();

    await user.click(screen.getByTestId('delete-confirm-btn'));

    await waitFor(() => {
      expect(mockDeleteByIds).toHaveBeenCalledWith([1]);
    });
    expect(mockList).toHaveBeenCalledTimes(2);
  });

  it('toggle all selects every row on current page', async () => {
    const user = userEvent.setup();
    render(<HistoryPage />);

    await waitFor(() => expect(screen.getByTestId('toggle-all-btn')).toBeTruthy());
    await user.click(screen.getByTestId('toggle-all-btn'));

    expect(screen.getByTestId('select-1')).toHaveProperty('checked', true);
    expect(screen.getByTestId('select-2')).toHaveProperty('checked', true);
    expect(screen.getByText('Toggle all (2)')).toBeTruthy();
  });
});
