import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ApiService } from '../../api/types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('../../api/client', () => ({
  clusterApi: { getDomain: vi.fn() },
}));

import ConversionForm from './ConversionForm';

const service: ApiService = {
  id: '1',
  name: 'Neural Bank',
  systemName: 'neuralbank',
  policies: [],
};

function renderForm(
  namespace: string,
  onConvert = vi.fn(),
  onNamespaceChange = vi.fn(),
) {
  render(
    <ConversionForm
      loading={false}
      error={null}
      progress={0}
      selectedServices={[service]}
      conversionResults={[]}
      namespace={namespace}
      onNamespaceChange={onNamespaceChange}
      onConvert={onConvert}
      onBack={vi.fn()}
    />,
  );
  return { onConvert, onNamespaceChange };
}

describe('ConversionForm namespace', () => {
  afterEach(() => cleanup());

  it('sends the trimmed namespace when generating', async () => {
    const { onConvert } = renderForm('  business-apis  ');

    await userEvent.click(screen.getByRole('button', { name: 'conversion.btnConvert' }));

    expect(onConvert).toHaveBeenCalledWith(expect.objectContaining({ namespace: 'business-apis' }));
    expect(screen.getByText('conversion.namespaceHelper')).toBeTruthy();
  });

  it('disables generate when the namespace is blank', () => {
    renderForm('   ');

    expect(screen.getByRole('button', { name: 'conversion.btnConvert' })).toBeDisabled();
  });

  it('reports edits to the namespace field', async () => {
    const { onNamespaceChange } = renderForm('business-apis');

    await userEvent.type(screen.getByPlaceholderText('business-apis'), 'x');

    expect(onNamespaceChange).toHaveBeenCalledWith('business-apisx');
  });
});
