import React from 'react';
import {
  Tabs,
  Tab,
  TabTitleText,
  Select,
  SelectOption,
  MenuToggle,
} from '@patternfly/react-core';
import { ConversionResultItem } from '../../api/types';
import styles from '../../pages/YAMLViewerPage.module.css';

interface Props {
  results: ConversionResultItem[];
  activeService: number;
  activeTab: number;
  selectOpen: boolean;
  onServiceChange: (idx: number) => void;
  onTabChange: (idx: number) => void;
  onSelectOpenChange: (open: boolean) => void;
  isModified: (filename: string) => boolean;
  renderTabContent: (filename: string, originalContent: string, tabIdx: number) => React.ReactNode;
}

const YamlFileTabs: React.FC<Props> = ({
  results,
  activeService,
  activeTab,
  selectOpen,
  onServiceChange,
  onTabChange,
  onSelectOpenChange,
  isModified,
  renderTabContent,
}) => {
  const current = results[activeService];
  const originalFiles = current ? Object.entries(current.yamlFiles ?? {}) : [];

  return (
    <>
      {results.length > 1 && current && (
        <div style={{ marginBottom: '16px' }}>
          <Select
            isOpen={selectOpen}
            onOpenChange={onSelectOpenChange}
            selected={current.serviceName}
            onSelect={(_e, val) => {
              const idx = results.findIndex(r => r.serviceName === val);
              if (idx >= 0) onServiceChange(idx);
              onSelectOpenChange(false);
              onTabChange(0);
            }}
            toggle={(ref) => (
              <MenuToggle ref={ref} onClick={() => onSelectOpenChange(!selectOpen)}>
                {current.serviceName}
              </MenuToggle>
            )}
          >
            {results.map((r, i) => (
              <SelectOption key={i} value={r.serviceName}>{r.serviceName}</SelectOption>
            ))}
          </Select>
        </div>
      )}

      <Tabs
        activeKey={activeTab}
        onSelect={(_e, key) => onTabChange(Number(key))}
      >
        {originalFiles.map(([filename, originalContent], i) => (
          <Tab
            key={i}
            eventKey={i}
            title={
              <TabTitleText>
                {filename}
                {isModified(filename) && (
                  <span className={styles.modifiedDot}>●</span>
                )}
              </TabTitleText>
            }
          >
            <div style={{ marginTop: '12px' }}>
              {renderTabContent(filename, originalContent, i)}
            </div>
          </Tab>
        ))}
      </Tabs>
    </>
  );
};

export default YamlFileTabs;
