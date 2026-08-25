import React from 'react';
import {
  Toolbar,
  ToolbarContent,
  ToolbarItem,
  SearchInput,
  Button,
  Spinner,
  Badge,
  Pagination,
} from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import { ApiService } from '../../api/types';

const PER_PAGE_OPTIONS = [
  { title: '10', value: 10 },
  { title: '20', value: 20 },
  { title: '50', value: 50 },
  { title: '100', value: 100 },
];

interface Props {
  search: string;
  loading: boolean;
  page: number;
  perPage: number;
  itemCount: number;
  hasMore: boolean;
  total: number | null;
  selectedService: ApiService | null;
  onSearchChange: (val: string) => void;
  onSearchClear: () => void;
  onRefresh: () => void;
  onSetPage: (page: number) => void;
  onPerPageSelect: (perPage: number) => void;
}

const ServiceToolbar: React.FC<Props> = ({
  search,
  loading,
  page,
  perPage,
  itemCount,
  hasMore,
  total,
  selectedService,
  onSearchChange,
  onSearchClear,
  onRefresh,
  onSetPage,
  onPerPageSelect,
}) => {
  const { t } = useTranslation();
  return (
    <Toolbar>
      <ToolbarContent>
        <ToolbarItem>
          <SearchInput
            placeholder={t('apiSelection.searchPlaceholder')}
            value={search}
            onChange={(_e, val) => onSearchChange(val)}
            onClear={onSearchClear}
          />
        </ToolbarItem>
        <ToolbarItem>
          <Button variant="secondary" onClick={onRefresh} isDisabled={loading}>
            {loading ? <Spinner size="sm" /> : t('apiSelection.btnRefresh')}
          </Button>
        </ToolbarItem>
        <ToolbarItem align={{ default: 'alignRight' }}>
          {selectedService && <Badge isRead={false}>{selectedService.name}</Badge>}
        </ToolbarItem>
        <ToolbarItem align={{ default: 'alignRight' }} variant="pagination">
          <Pagination
            itemCount={itemCount}
            page={page}
            perPage={perPage}
            perPageOptions={PER_PAGE_OPTIONS}
            onSetPage={(_e, newPage) => onSetPage(newPage)}
            onPerPageSelect={(_e, newPerPage) => onPerPageSelect(newPerPage)}
            isCompact
            isDisabled={loading}
            toggleTemplate={
              total == null
                ? ({ firstIndex, lastIndex }) => (
                    <>
                      {firstIndex} - {lastIndex}
                      {hasMore ? '+' : ''}
                    </>
                  )
                : undefined
            }
          />
        </ToolbarItem>
      </ToolbarContent>
    </Toolbar>
  );
};

export default ServiceToolbar;
