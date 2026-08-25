import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Page,
  PageSidebar,
  PageSidebarBody,
  Nav,
  NavItem,
  NavExpandable,
  Masthead,
  MastheadToggle,
  MastheadMain,
  MastheadBrand,
  MastheadContent,
  PageToggleButton,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
  TextContent,
  Text,
  TextVariants,
} from '@patternfly/react-core';
import {
  BarsIcon,
  PluggedIcon,
  ListIcon,
  CheckCircleIcon,
  CodeIcon,
  EyeIcon,
  SecurityIcon,
  DownloadIcon,
  UploadIcon,
  HistoryIcon,
  CogIcon,
} from '@patternfly/react-icons';

import ConnectionPage from './pages/ConnectionPage';
import APISelectionPage from './pages/APISelectionPage';
import CompatibilityPage from './pages/CompatibilityPage';
import ConversionPage from './pages/ConversionPage';
import YAMLViewerPage from './pages/YAMLViewerPage';
import ValidationPage from './pages/ValidationPage';
import DownloadPage from './pages/DownloadPage';
import HistoryPage from './pages/HistoryPage';
import ImportPage from './pages/ImportPage';
import SettingsPage from './pages/SettingsPage';
import SupportedPoliciesPage from './pages/SupportedPoliciesPage';

import { AppStateProvider, useAppState } from './components/AppStateContext';
import LangSwitcher from './components/LangSwitcher';
import RouteErrorBoundary from './components/RouteErrorBoundary';

import styles from './App.module.css';

type NavSectionItem = { path: string; label: string; icon: React.ReactNode };

const RHHatIcon: React.FC<{ size?: number }> = ({ size = 32 }) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 192.3 146"
    width={size}
    height={Math.round(size * 146 / 192.3)}
    aria-hidden="true"
    style={{ flexShrink: 0 }}
  >
    <path fill="#ffffff" d="m128,84c12.5,0 30.6,-2.6 30.6,-17.5a19.53,19.53 0 0 0-0.3,-3.4L150.9,30.7C149.2,23.6 147.7,20.3 135.2,14.1 125.5,9.1 104.4,1 98.1,1 92.2,1 90.5,8.5 83.6,8.5 76.9,8.5 72,2.9 65.7,2.9c-6,0-9.9,4.1-12.9,12.5 0,0-8.4,23.7-9.5,27.2a6.15,6.15 0 0 0-0.2,1.9C43,53.7 79.3,83.9 128,84m32.5,-11.4c1.7,8.2 1.7,9.1 1.7,10.1 0,14-15.7,21.8-36.4,21.8C79,104.5 38.1,77.1 38.1,59a18.35,18.35 0 0 1 1.5,-7.3C22.8,52.5 1,55.5 1,74.7 1,106.2 75.6,145 134.6,145c45.3,0 56.7,-20.5 56.7,-36.7 0,-12.7-11,-27.1-30.8,-35.7"/>
    <path fill="rgba(255,255,255,0.55)" d="m160.5,72.6c1.7,8.2 1.7,9.1 1.7,10.1 0,14-15.7,21.8-36.4,21.8C79,104.5 38.1,77.1 38.1,59a18.35,18.35 0 0 1 1.5,-7.3l3.7,-9.1a6.15,6.15 0 0 0-0.2,1.9c0,9.2 36.3,39.4 84.9,39.4 12.5,0 30.6,-2.6 30.6,-17.5A19.53,19.53 0 0 0 158.3,63Z"/>
  </svg>
);

const RedHatLogo: React.FC = () => (
  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
    <RHHatIcon size={30} />
    <span className={styles.brandWordmark}>Red Hat</span>
  </div>
);

const Footer: React.FC = () => {
  const { t } = useTranslation();
  return <div className={styles.footer}><span>{t('app.copyright')}</span></div>;
};

const AppContent: React.FC = () => {
  const { t } = useTranslation();
  const { appState } = useAppState();
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [workflowExpanded, setWorkflowExpanded] = useState(true);
  const [toolsExpanded, setToolsExpanded] = useState(true);
  const navigate = useNavigate();
  const location = useLocation();
  const [settingsExpanded, setSettingsExpanded] = useState(() => location.pathname.startsWith('/settings'));

  const workflowItems: NavSectionItem[] = [
    { path: '/',              label: t('nav.connection'),    icon: <PluggedIcon /> },
    { path: '/services',      label: t('nav.apiList'),        icon: <ListIcon /> },
    { path: '/compatibility', label: t('nav.compatibility'),  icon: <CheckCircleIcon /> },
    { path: '/convert',       label: t('nav.convert'),        icon: <CodeIcon /> },
    { path: '/yaml',          label: t('nav.yamlPreview'),    icon: <EyeIcon /> },
    { path: '/validate',      label: t('nav.validation'),     icon: <SecurityIcon /> },
    { path: '/download',      label: t('nav.download'),       icon: <DownloadIcon /> },
  ];
  const toolItems: NavSectionItem[] = [
    { path: '/import',  label: t('nav.import'),  icon: <UploadIcon /> },
    { path: '/history', label: t('nav.history'), icon: <HistoryIcon /> },
  ];
  const settingsItems: NavSectionItem[] = [
    { path: '/settings',          label: t('nav.settingsGeneral'),  icon: <CogIcon /> },
    { path: '/settings/policies', label: t('nav.settingsPolicies'), icon: <CogIcon /> },
  ];

  const renderNavSection = (
    title: string,
    items: NavSectionItem[],
    isExpanded: boolean,
    onExpand: (v: boolean) => void,
    isActive: boolean,
  ) => (
    <NavExpandable title={title} isExpanded={isExpanded} onExpand={(_e, val) => onExpand(val)} isActive={isActive}>
      {items.map(item => (
        <NavItem key={item.path} isActive={location.pathname === item.path} onClick={() => navigate(item.path)}>
          <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ width: '16px', flexShrink: 0, opacity: 0.85 }}>{item.icon}</span>
            {item.label}
          </span>
        </NavItem>
      ))}
    </NavExpandable>
  );

  const isWorkflowActive = workflowItems.some(i => i.path === location.pathname);
  const isToolsActive = toolItems.some(i => i.path === location.pathname);
  const isSettingsActive = settingsItems.some(i => i.path === location.pathname);

  const sidebar = (
    <PageSidebar isSidebarOpen={isSidebarOpen}>
      <PageSidebarBody>
        <Nav theme="dark">
          {renderNavSection(t('nav.workflow'), workflowItems, workflowExpanded, setWorkflowExpanded, isWorkflowActive)}
          {renderNavSection(t('nav.tools'),    toolItems,     toolsExpanded,    setToolsExpanded,    isToolsActive)}
          {renderNavSection(t('nav.settings'), settingsItems, settingsExpanded, setSettingsExpanded, isSettingsActive)}
        </Nav>
      </PageSidebarBody>
    </PageSidebar>
  );

  const masthead = (
    <Masthead>
      <MastheadToggle>
        <PageToggleButton
          variant="plain"
          aria-label="Global navigation"
          isSidebarOpen={isSidebarOpen}
          onSidebarToggle={() => setIsSidebarOpen(!isSidebarOpen)}
          id="nav-toggle"
        >
          <BarsIcon />
        </PageToggleButton>
      </MastheadToggle>
      <MastheadMain>
        <MastheadBrand><RedHatLogo /></MastheadBrand>
      </MastheadMain>
      <MastheadContent>
        <Toolbar>
          <ToolbarContent>
            <ToolbarItem>
              <TextContent>
                <Text component={TextVariants.p} className={styles.appTitle}>{t('nav.appTitle')}</Text>
              </TextContent>
            </ToolbarItem>
            <ToolbarItem align={{ default: 'alignRight' }}>
              {appState.connection.connected && (
                <TextContent>
                  <Text component={TextVariants.small} className={styles.connectedHint}>
                    {t('nav.connected', { url: appState.connection.url })}
                  </Text>
                </TextContent>
              )}
            </ToolbarItem>
            <ToolbarItem><LangSwitcher /></ToolbarItem>
          </ToolbarContent>
        </Toolbar>
      </MastheadContent>
    </Masthead>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <Page
        header={masthead}
        sidebar={isSidebarOpen ? sidebar : undefined}
        isManagedSidebar={false}
        style={{ flex: 1, minHeight: 0 }}
      >
        <RouteErrorBoundary>
          <Routes>
            <Route path="/" element={<ConnectionPage />} />
            <Route path="/services" element={<APISelectionPage />} />
            <Route path="/compatibility" element={<CompatibilityPage />} />
            <Route path="/convert" element={<ConversionPage />} />
            <Route path="/yaml" element={<YAMLViewerPage />} />
            <Route path="/validate" element={<ValidationPage />} />
            <Route path="/download" element={<DownloadPage />} />
            <Route path="/import" element={<ImportPage />} />
            <Route path="/history" element={<HistoryPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/settings/policies" element={<SupportedPoliciesPage />} />
          </Routes>
        </RouteErrorBoundary>
      </Page>
      <Footer />
    </div>
  );
};

const App: React.FC = () => (
  <BrowserRouter>
    <AppStateProvider>
      <AppContent />
    </AppStateProvider>
  </BrowserRouter>
);

export default App;
