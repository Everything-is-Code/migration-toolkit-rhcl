import { describe, expect, it } from 'vitest';
import {
  parseTestInfo,
  detectExternalBackend,
  normalizeApiVersions,
  computeDiff,
  deriveEdits,
} from './importUtils';

describe('parseTestInfo', () => {
  it('extracts gateway name, routes, jwt auth, and api key from edits', () => {
    const edits = {
      'gateway.yaml': 'metadata:\n  name: my-gw\n',
      'httproute.yaml': 'value: "/v1"\nmethod: GET\nvalue: "/v2"\nmethod: POST\n',
      'policy.yaml': 'jwt:\n  issuer: test\n',
      'secret.yaml': 'api_key: "abc12345"\n',
    };
    const info = parseTestInfo(edits);
    expect(info.gatewayName).toBe('my-gw');
    expect(info.routes).toEqual([
      { path: '/v1', method: 'GET' },
      { path: '/v2', method: 'POST' },
    ]);
    expect(info.auth).toEqual({ type: 'jwt', headerName: 'Authorization' });
    expect(info.apiKey).toBe('abc12345');
  });

  it('defaults route and auth when yaml is empty', () => {
    const info = parseTestInfo({});
    expect(info.routes).toEqual([{ path: '/', method: 'GET' }]);
    expect(info.auth).toEqual({ type: 'none' });
  });
});

describe('detectExternalBackend', () => {
  it('returns true when serviceentry.yaml is present', () => {
    expect(detectExternalBackend({ 'serviceentry.yaml': 'kind: ServiceEntry' })).toBe(true);
  });

  it('returns false for internal backends only', () => {
    expect(detectExternalBackend({ 'deployment.yaml': 'kind: Deployment' })).toBe(false);
  });
});

describe('normalizeApiVersions', () => {
  it('normalizes kuadrant and gateway beta apiVersions', () => {
    const yaml = [
      'apiVersion: kuadrant.io/v1beta2',
      'apiVersion: gateway.networking.k8s.io/v1beta1',
    ].join('\n');
    const out = normalizeApiVersions(yaml);
    expect(out).toContain('apiVersion: kuadrant.io/v1');
    expect(out).toContain('apiVersion: gateway.networking.k8s.io/v1');
  });
});

describe('computeDiff', () => {
  it('marks added and removed lines', () => {
    const diff = computeDiff('a\nb', 'a\nc');
    expect(diff).toEqual([
      { type: 'same', text: 'a' },
      { type: 'add', text: 'c' },
      { type: 'remove', text: 'b' },
    ]);
  });
});

describe('deriveEdits', () => {
  it('applies namespace and package substitutions', () => {
    const base = {
      'deployment.yaml': '  name: api\n  namespace: old\n  app: api\n',
    };
    const out = deriveEdits(base, 'payments', 'prod');
    expect(out['deployment.yaml']).toContain('namespace: prod');
    expect(out['deployment.yaml']).toContain('name: payments');
    expect(out['deployment.yaml']).toContain('app: payments');
  });
});
