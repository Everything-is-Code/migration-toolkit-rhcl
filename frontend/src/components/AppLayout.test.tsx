/// @vitest-environment jsdom
import React from 'react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';

let connectionPageThrows = false;

vi.mock('i18next', () => ({
  default: { t: (key: string) => key },
}));

vi.mock('react-router-dom', () => ({
  Routes: ({ children }: { children: React.ReactNode }) => {
    const routes = React.Children.toArray(children) as React.ReactElement<{
      path?: string;
      element?: React.ReactNode;
    }>[];
    const active = routes.find((route) => route.props.path === '/');
    return <>{active?.props.element}</>;
  },
  Route: () => null,
  useNavigate: () => vi.fn(),
  useLocation: () => ({ pathname: '/' }),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
  Trans: ({ i18nKey }: { i18nKey: string }) => <span>{i18nKey}</span>,
}));

vi.mock('./AppStateContext', () => ({
  useAppState: () => ({
    appState: {
      connection: { connected: false, url: '' },
      namespace: 'default',
      selectedServices: [],
    },
  }),
}));

vi.mock('./AppChrome', () => ({
  RedHatLogo: () => <span>Logo</span>,
  Footer: () => <footer>Footer</footer>,
}));

vi.mock('./LangSwitcher', () => ({ default: () => <div>LangSwitcher</div> }));

vi.mock('../pages/ConnectionPage', () => ({
  default: () => {
    if (connectionPageThrows) {
      throw new Error('route render failed');
    }
    return <div data-testid="connection-page" />;
  },
}));
vi.mock('../pages/APISelectionPage', () => ({ default: () => null }));
vi.mock('../pages/CompatibilityPage', () => ({ default: () => null }));
vi.mock('../pages/ConversionPage', () => ({ default: () => null }));
vi.mock('../pages/YAMLViewerPage', () => ({ default: () => null }));
vi.mock('../pages/ValidationPage', () => ({ default: () => null }));
vi.mock('../pages/DownloadPage', () => ({ default: () => null }));
vi.mock('../pages/HistoryPage', () => ({ default: () => null }));
vi.mock('../pages/ImportPage', () => ({ default: () => null }));
vi.mock('../pages/SettingsPage', () => ({ default: () => null }));
vi.mock('../pages/SupportedPoliciesPage', () => ({ default: () => null }));

import AppLayout from './AppLayout';

describe('AppLayout', () => {
  beforeEach(() => {
    connectionPageThrows = false;
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders without crashing and shows a sidebar nav item', () => {
    render(<AppLayout />);
    expect(screen.getAllByText('nav.connection').length).toBeGreaterThan(0);
    expect(screen.getByTestId('connection-page')).toBeTruthy();
  });

  it('keeps masthead and nav visible when the active route throws', () => {
    connectionPageThrows = true;

    render(<AppLayout />);

    expect(screen.getAllByText('nav.connection').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Footer').length).toBeGreaterThan(0);
    expect(screen.getByText('route render failed')).toBeTruthy();
    expect(screen.getByText('app.errorTitle')).toBeTruthy();
  });
});
