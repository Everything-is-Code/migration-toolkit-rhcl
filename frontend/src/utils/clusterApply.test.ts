import { describe, expect, it } from 'vitest';
import { buildApplyPayload, yamlFilesContainPlaceholder } from './clusterApply';

describe('yamlFilesContainPlaceholder', () => {
  it('detects REPLACE_ME in any file', () => {
    expect(yamlFilesContainPlaceholder({ 'secret.yaml': 'client-id: REPLACE_ME' })).toBe(true);
    expect(yamlFilesContainPlaceholder({ 'gateway.yaml': 'kind: Gateway' })).toBe(false);
  });
});

describe('buildApplyPayload', () => {
  it('normalizes apiVersion and sets namespace', () => {
    const out = buildApplyPayload({
      'gateway.yaml': 'apiVersion: kuadrant.io/v1beta2\nmetadata:\n  namespace: old\n',
    }, 'target-ns');
    expect(out['gateway.yaml']).toContain('apiVersion: kuadrant.io/v1');
    expect(out['gateway.yaml']).toContain('namespace: target-ns');
  });

  it('fixes httproute port for external backends', () => {
    const route = `
spec:
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /
      backendRefs:
        - name: a-backend
          port: 8080
`;
    const out = buildApplyPayload({
      'serviceentry.yaml': 'kind: ServiceEntry\n',
      'httproute.yaml': route,
    }, 'ns');
    expect(out['httproute.yaml']).toContain('port: 443');
    expect(out['httproute.yaml']).not.toMatch(/backendRefs:[\s\S]*port: 8080/);
  });
});
