import React, { useState } from 'react';
import { Routes, Route, useNavigate, useLocation } from 'react-router-dom';
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

import ConnectionPage from '../pages/ConnectionPage';
import APISelectionPage from '../pages/APISelectionPage';
import CompatibilityPage from '../pages/CompatibilityPage';
import ConversionPage from '../pages/ConversionPage';
import YAMLViewerPage from '../pages/YAMLViewerPage';
import ValidationPage from '../pages/ValidationPage';
import DownloadPage from '../pages/DownloadPage';
import HistoryPage from '../pages/HistoryPage';
import ImportPage from '../pages/ImportPage';
import SettingsPage from '../pages/SettingsPage';
import SupportedPoliciesPage from '../pages/SupportedPoliciesPage';

import { useAppState } from './AppStateContext';
import LangSwitcher from './LangSwitcher';
import RouteErrorBoundary from './RouteErrorBoundary';
import { RedHatLogo, Footer } from './AppChrome';

import styles from '../App.module.css';

type NavSectionItem = { path: string; label: string; icon: React.ReactNode };

const AppLayout: React.FC = () => {
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

export default AppLayout;
