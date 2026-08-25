/// @vitest-environment jsdom
import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('react-router-dom', () => ({
  Routes: ({ children }: { children: React.ReactNode }) => <>{children}</>,
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
vi.mock('./RouteErrorBoundary', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../pages/ConnectionPage', () => ({ default: () => null }));
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
  it('renders without crashing and shows a sidebar nav item', () => {
    render(<AppLayout />);
    expect(screen.getByText('nav.connection')).toBeDefined();
  });
});
