/// @vitest-environment jsdom
import React from 'react';
import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ApplyConfirmModal from './ApplyConfirmModal';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  Trans: ({ i18nKey }: { i18nKey?: string }) => <span data-testid={i18nKey} />,
}));

describe('ApplyConfirmModal', () => {
  afterEach(() => cleanup());
  it('calls onClose when cancel is clicked', async () => {
    const onClose = vi.fn();
    render(
      <ApplyConfirmModal
        isOpen
        namespace="test-ns"
        packageName="my-api"
        fileCount={2}
        applying={false}
        onNamespaceChange={vi.fn()}
        onClose={onClose}
        onConfirm={vi.fn()}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'download.applyConfirmCancel' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('shows applying label when apply is in progress', () => {
    render(
      <ApplyConfirmModal
        isOpen
        namespace="test-ns"
        packageName="my-api"
        fileCount={2}
        applying
        onNamespaceChange={vi.fn()}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );

    expect(screen.getByText('download.btnApplying')).toBeTruthy();
  });

  it('calls onNamespaceChange when namespace input is edited', async () => {
    const onNamespaceChange = vi.fn();
    render(
      <ApplyConfirmModal
        isOpen
        namespace="test-ns"
        packageName="my-api"
        fileCount={2}
        applying={false}
        onNamespaceChange={onNamespaceChange}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );

    const input = screen.getByDisplayValue('test-ns');
    await userEvent.clear(input);
    await userEvent.type(input, 'prod-ns');
    expect(onNamespaceChange).toHaveBeenCalled();
  });

  it('disables apply when namespace is blank', () => {
    render(
      <ApplyConfirmModal
        isOpen
        namespace="   "
        packageName="my-api"
        fileCount={2}
        applying={false}
        onNamespaceChange={vi.fn()}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'download.applyConfirmAction' })).toBeDisabled();
  });
});
