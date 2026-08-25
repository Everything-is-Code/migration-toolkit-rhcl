/// @vitest-environment jsdom
import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('@patternfly/react-core', () => ({
  Radio: ({
    id,
    label,
  }: {
    id: string;
    label: string;
  }) => <label htmlFor={id}>{label}</label>,
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

import ConversionPolicySettings from './ConversionPolicySettings';

describe('ConversionPolicySettings', () => {
  const noop = vi.fn();

  it('renders nothing when no policy flags are set', () => {
    const { container } = render(
      <ConversionPolicySettings
        hasLoggingPolicy={false}
        hasAnonymousPolicy={false}
        hasIpCheckPolicy={false}
        loggingTarget="gateway"
        anonymousTarget="httproute"
        ipCheckMode="authorizationPolicy"
        onLoggingTargetChange={noop}
        onAnonymousTargetChange={noop}
        onIpCheckModeChange={noop}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('shows logging target radios when logging policy is enabled', () => {
    render(
      <ConversionPolicySettings
        hasLoggingPolicy={true}
        hasAnonymousPolicy={false}
        hasIpCheckPolicy={false}
        loggingTarget="gateway"
        anonymousTarget="httproute"
        ipCheckMode="authorizationPolicy"
        onLoggingTargetChange={noop}
        onAnonymousTargetChange={noop}
        onIpCheckModeChange={noop}
      />,
    );
    expect(screen.getByText('conversion.loggingTargetGateway')).toBeTruthy();
    expect(screen.getByText('conversion.loggingTargetWorkload')).toBeTruthy();
  });
});
