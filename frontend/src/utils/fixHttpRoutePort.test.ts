import { describe, expect, it } from 'vitest';
import { fixHttpRoutePort } from './fixHttpRoutePort';

describe('fixHttpRoutePort', () => {
  it('rewrites every backendRefs port 8080 to 443', () => {
    const yaml = `
spec:
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /
      backendRefs:
        - name: a-backend
          port: 8080
        - name: b-backend
          port: 8080
`;
    const fixed = fixHttpRoutePort(yaml);
    expect(fixed.match(/port: 443/g)?.length).toBe(2);
    expect(fixed).not.toMatch(/backendRefs:[\s\S]*port: 8080/);
  });

  it('does not rewrite Gateway listener port 8080', () => {
    const yaml = `
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
spec:
  listeners:
    - name: http
      port: 8080
---
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
spec:
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /
      backendRefs:
        - name: svc
          port: 8080
`;
    const fixed = fixHttpRoutePort(yaml);
    expect(fixed).toMatch(/listeners:[\s\S]*port: 8080/);
    expect(fixed).toMatch(/backendRefs:[\s\S]*port: 443/);
  });
});
