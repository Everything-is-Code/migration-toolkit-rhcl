package com.redhat.migrationtoolkit.rhcl.dto;

import java.util.List;

public class ConversionRequest {
    public String threescaleUrl;
    public String accessToken;
    public String tenant;
    public String namespace;
    public List<String> serviceIds;
    /** 外部バックエンドURL (例: https://foo.ecs.us-east-2.on.aws/api)。
     * 指定時は ServiceEntry + DestinationRule + Host rewrite を生成する。 */
    public String externalBackendUrl;
    /** 互換性チェックで「対応済み」と見なすポリシー表示名のリスト。 */
    public List<String> supportedPolicies;
    /** Logging ポリシーの適用先: "gateway"（デフォルト）または "workload" */
    public String loggingTarget;
    /** Anonymous Access ポリシーの targetRef: "httproute"（デフォルト）または "gateway" */
    public String anonymousTarget;
    /** 生成リソースに "migrated-from: 3scale" ラベルを付与するかどうか（デフォルト: true）。 */
    public Boolean includeMigratedFromLabel;
    /**
     * ip_check emit target: "authorizationPolicy" (default) or "authPolicyOpa".
     * Convert-time preference — same pattern as anonymousTarget.
     */
    public String ipCheckMode;
}
