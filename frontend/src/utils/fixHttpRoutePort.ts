/**
 * Rewrite HTTPRoute backendRefs.port 8080 → 443.
 * Only targets the backendRefs block; does not change Gateway listener ports.
 * Rewrites every `port: 8080` inside each backendRefs block (multi-ref case).
 */
export function fixHttpRoutePort(yaml: string): string {
  return yaml.replace(
    /(^[ \t]*backendRefs:\r?\n)((?:[ \t]+[^\n]*(?:\r?\n|$))*)/gm,
    (_full, header: string, body: string) =>
      header + body.replace(/([ \t]+port:[ \t]*)8080\b/g, '$1443')
  );
}
