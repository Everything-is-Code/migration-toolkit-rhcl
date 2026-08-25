import React from 'react';
import { Radio } from '@patternfly/react-core';
import { useTranslation } from 'react-i18next';
import type { AnonymousTarget, IpCheckMode, LoggingTarget } from './conversionFormTypes';
import styles from '../../styles/shared.module.css';

interface Props {
  hasLoggingPolicy: boolean;
  hasAnonymousPolicy: boolean;
  hasIpCheckPolicy: boolean;
  loggingTarget: LoggingTarget;
  anonymousTarget: AnonymousTarget;
  ipCheckMode: IpCheckMode;
  onLoggingTargetChange: (target: LoggingTarget) => void;
  onAnonymousTargetChange: (target: AnonymousTarget) => void;
  onIpCheckModeChange: (mode: IpCheckMode) => void;
}

const ConversionPolicySettings: React.FC<Props> = ({
  hasLoggingPolicy,
  hasAnonymousPolicy,
  hasIpCheckPolicy,
  loggingTarget,
  anonymousTarget,
  ipCheckMode,
  onLoggingTargetChange,
  onAnonymousTargetChange,
  onIpCheckModeChange,
}) => {
  const { t } = useTranslation();

  if (!hasLoggingPolicy && !hasAnonymousPolicy && !hasIpCheckPolicy) {
    return null;
  }

  return (
    <div className={styles.bluePanel}>
      <div className={styles.bluePanelTitle}>
        {t('conversion.policySettings', 'Policy Settings')}
      </div>

      {hasLoggingPolicy && (
        <div style={{ marginBottom: (hasAnonymousPolicy || hasIpCheckPolicy) ? '16px' : 0 }}>
          <div className={styles.sectionHeading}>
            {t('conversion.loggingTarget', 'Logging Policy Target')}
          </div>
          <div style={{ display: 'flex', gap: '24px' }}>
            <Radio
              id="logging-target-gateway"
              name="loggingTarget"
              label={t('conversion.loggingTargetGateway', 'Gateway Pod (recommended)')}
              isChecked={loggingTarget === 'gateway'}
              onChange={() => onLoggingTargetChange('gateway')}
              description={t('conversion.loggingTargetGatewayDesc', 'context: GATEWAY / istio.io/gateway-name selector')}
            />
            <Radio
              id="logging-target-workload"
              name="loggingTarget"
              label={t('conversion.loggingTargetWorkload', 'Workload Pod')}
              isChecked={loggingTarget === 'workload'}
              onChange={() => onLoggingTargetChange('workload')}
              description={t('conversion.loggingTargetWorkloadDesc', 'context: SIDECAR_INBOUND / app selector')}
            />
          </div>
        </div>
      )}

      {hasAnonymousPolicy && (
        <div style={{ marginBottom: hasIpCheckPolicy ? '16px' : 0 }}>
          <div className={styles.sectionHeading}>
            {t('conversion.anonymousTarget', 'Anonymous Access Policy Target')}
          </div>
          <div style={{ display: 'flex', gap: '24px' }}>
            <Radio
              id="anonymous-target-gateway"
              name="anonymousTarget"
              label={t('conversion.anonymousTargetGateway', 'Gateway')}
              isChecked={anonymousTarget === 'gateway'}
              onChange={() => onAnonymousTargetChange('gateway')}
              description={t('conversion.anonymousTargetGatewayDesc', 'targetRef.kind: Gateway — Applies to all routes via Gateway')}
            />
            <Radio
              id="anonymous-target-httproute"
              name="anonymousTarget"
              label={t('conversion.anonymousTargetHttpRoute', 'HTTPRoute (recommended)')}
              isChecked={anonymousTarget === 'httproute'}
              onChange={() => onAnonymousTargetChange('httproute')}
              description={t('conversion.anonymousTargetHttpRouteDesc', 'targetRef.kind: HTTPRoute — Applies to specific routes only')}
            />
          </div>
        </div>
      )}

      {hasIpCheckPolicy && (
        <div>
          <div className={styles.sectionHeading}>
            {t('conversion.ipCheckMode', 'IP Check target')}
          </div>
          <div style={{ display: 'flex', gap: '24px' }}>
            <Radio
              id="ip-check-authorization-policy"
              name="ipCheckMode"
              label={t('conversion.ipCheckModeAuthz', 'AuthorizationPolicy (recommended)')}
              isChecked={ipCheckMode === 'authorizationPolicy'}
              onChange={() => onIpCheckModeChange('authorizationPolicy')}
              description={t('conversion.ipCheckModeAuthzDesc', 'Istio AuthorizationPolicy with remoteIpBlocks')}
            />
            <Radio
              id="ip-check-auth-policy-opa"
              name="ipCheckMode"
              label={t('conversion.ipCheckModeOpa', 'AuthPolicy / OPA')}
              isChecked={ipCheckMode === 'authPolicyOpa'}
              onChange={() => onIpCheckModeChange('authPolicyOpa')}
              description={t('conversion.ipCheckModeOpaDesc', 'Kuadrant AuthPolicy authorization with OPA/Rego')}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default ConversionPolicySettings;
