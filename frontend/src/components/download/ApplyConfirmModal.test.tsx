/// @vitest-environment jsdom
import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ApplyConfirmModal from './ApplyConfirmModal';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  Trans: ({ i18nKey }: { i18nKey?: string }) => <span data-testid={i18nKey} />,
}));

describe('ApplyConfirmModal', () => {
  it('calls onClose when cancel is clicked', async () => {
    const onClose = vi.fn();
    render(
      <ApplyConfirmModal
        isOpen
        namespace="test-ns"
        packageName="my-api"
        fileCount={2}
        applying={false}
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
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );

    expect(screen.getByText('download.btnApplying')).toBeTruthy();
  });
});
