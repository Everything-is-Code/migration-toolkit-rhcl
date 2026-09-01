/// @vitest-environment jsdom
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('i18next', () => ({
  default: { t: (key: string) => key },
}));

import RouteErrorBoundary from './RouteErrorBoundary';

function Thrower({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) {
    throw new Error('route render failed');
  }
  return <div data-testid="child-ok">ok</div>;
}

describe('RouteErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('renders children when no error', () => {
    render(
      <RouteErrorBoundary>
        <Thrower shouldThrow={false} />
      </RouteErrorBoundary>,
    );
    expect(screen.getByTestId('child-ok')).toBeTruthy();
  });

  it('shows error banner when child throws and recovers on retry', async () => {
    const user = userEvent.setup();
    let shouldThrow = true;
    const Toggle = () => <Thrower shouldThrow={shouldThrow} />;

    render(
      <RouteErrorBoundary>
        <Toggle />
      </RouteErrorBoundary>,
    );

    expect(screen.getByText('route render failed')).toBeTruthy();
    expect(screen.getByText('app.errorTitle')).toBeTruthy();

    shouldThrow = false;
    await user.click(screen.getByText('app.btnRetry'));

    expect(screen.getByTestId('child-ok')).toBeTruthy();
  });
});
