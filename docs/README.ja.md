# 日本語ドキュメント

English README: **[../README.md](../README.md)**

## Migration Toolkit for Red Hat Connectivity Link

3scale から Red Hat Connectivity Link へ移行するための GUI ツールキットです。  
Quarkus バックエンド + React/PatternFly フロントエンド で構成されています。

---

## 目次

- [スクリーンショット](#スクリーンショット)
- [前提条件・必要ツール](#前提条件必要ツール)
- [クイックスタート](#クイックスタート)
  - [Helm でのインストール](#helm-でのインストールany-cluster)
- [アーキテクチャ](#アーキテクチャ)
- [処理フロー](#処理フロー)
- [機能一覧](#機能一覧)
- [ディレクトリ構成](#ディレクトリ構成)
- [API一覧](#api一覧)
- [データモデル](#データモデル)
- [国際化対応 (i18n)](#国際化対応-i18n)

---

## スクリーンショット

| 3scale 接続設定 | 互換性チェック |
|:-:|:-:|
| ![3scale 接続設定](.claude/images/connect.png) | ![互換性チェック](.claude/images/apilist.png) |

| YAML 生成 | YAML プレビュー |
|:-:|:-:|
| ![YAML 生成](.claude/images/yaml.png) | ![YAML プレビュー](.claude/images/preview.png) |

| バリデーション | ダウンロード |
|:-:|:-:|
| ![バリデーション](.claude/images/validation.png) | ![ダウンロード](.claude/images/download.png) |

| ZIP インポート / CL 設定 | curl 疎通テスト |
|:-:|:-:|
| ![ZIP インポート](.claude/images/import.png) | ![curl 疎通テスト](.claude/images/rhcltest.png) |

---

## 前提条件・必要ツール

下記ツールの利用が前提となっています
https://github.com/Everything-is-Code/from-3scale-to-connectivity-link

### ローカル開発環境

| ツール | バージョン | 用途 |
|--------|------------|------|
| Java (OpenJDK) | 21 以上 | バックエンドビルド |
| Apache Maven | 3.9.x 以上 | バックエンドビルド |
| Node.js | 22 | フロントエンドビルド |
| npm | 9 以上 | フロントエンド依存関係管理 |
| Docker / Podman | 最新版 | コンテナイメージビルド（ローカル検証時） |

### OpenShift クラスター

| ツール / コンポーネント | バージョン | 用途 |
|------------------------|------------|------|
| OpenShift Container Platform | 4.14 以上 | デプロイ先クラスター |
| `oc` CLI | クラスターに対応したバージョン | クラスター操作 |
| CrunchyData PostgreSQL Operator | 最新版 | データベース管理（OperatorHub から事前インストール） |
| Sail Operator (Istio) | 最新版 | Gateway API 実装 — `install.sh` が自動インストール |
| Red Hat Connectivity Link (`rhcl-operator`) | 最新版 | 移行対象コンポーネント — `install.sh` が自動インストール |

> **注意**: CrunchyData PostgreSQL Operator は `openshift-operators` Namespace へ  
> 事前にインストールしてください。インストールスクリプトが自動検出します。
>
> Sail Operator と Red Hat Connectivity Link（`rhcl-operator`、Red Hat Operators カタログ）は
> `install.sh` / `install.sh --kuadrant-only` が自動でインストールします。Community 版の
> `kuadrant-operator` ではなく、製品版の `rhcl-operator` を使用してください。Community 版には
> DevPortal CRD（`APIKey` / `APIProduct`）が含まれておらず、`AuthPolicy` も `v1beta2` までしか
> 提供されません（`v1` が必要です）。

### 3scale 環境

- 3scale Admin Portal への接続 URL と Personal Access Token

---

## クイックスタート

### Helm でのインストール（any cluster）

公開済みコンテナイメージを Helm で導入する推奨手順です（詳細は英語版 [Install with Helm](#install-with-helm-any-cluster) およびメンテナ向け [post-merge runbook](#maintainer-make-this-repository-operational-post-merge) を参照）。

```bash
helm repo add migration-toolkit-rhcl https://everything-is-code.github.io/migration-toolkit-rhcl/
helm repo update
helm install migration-toolkit-rhcl migration-toolkit-rhcl/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
```

変換アダプタ: [from-3scale-to-connectivity-link](https://github.com/Everything-is-Code/from-3scale-to-connectivity-link)

### OpenShift へのフルデプロイ

```bash
# 任意の Namespace を指定してインストール（デフォルト: migration-toolkit）
NAMESPACE=migration-toolkit ./deploy/install.sh

# バックエンドのみデプロイ
NAMESPACE=migration-toolkit ./deploy/install.sh --backend-only

# フロントエンドのみデプロイ
NAMESPACE=migration-toolkit ./deploy/install.sh --frontend-only

# DB のみセットアップ
NAMESPACE=migration-toolkit ./deploy/install.sh --db-only
```

インストールスクリプトが以下を自動処理します:

1. Namespace 作成
2. Sail Operator (Istio) + Red Hat Connectivity Link (`rhcl-operator`) のインストールと CRD 準備待機
3. CrunchyData PostgreSQL Operator インストール待機
4. PostgreSQL クラスター作成（SCC 含む）
5. バックエンド Maven ビルド → S2I → デプロイ
6. フロントエンド npm ビルド → S2I (nginx) → デプロイ
7. アクセス URL 表示

> **フロントエンドを手動で再ビルドする場合**（`install.sh` を使わない場合）: `npm run build` は
> `frontend/build/` を毎回空にするため（`vite.config.ts` の `emptyOutDir`）、コピーしていた
> `nginx-default-cfg/` ディレクトリも削除されます。バイナリ S2I ビルド
> （`oc start-build ... --from-dir=frontend/build`）を実行する前に、必ず再コピーしてください。
> 忘れると `/api/` へのリバースプロキシ設定なしのイメージがデプロイされ、バックエンドへの
> 全リクエストが 404 になります。
> ```bash
> mkdir -p frontend/build/nginx-default-cfg
> cp frontend/nginx-default-cfg/api-proxy.conf frontend/build/nginx-default-cfg/api-proxy.conf
> ```
> `install.sh` の `deploy_frontend` はこの処理を自動的に行います。

### 言語切替

```bash
# 英語（デフォルト）
./deploy/install.sh

# 日本語で実行
INSTALL_LANG=ja ./deploy/install.sh
```

> `install.sh` はシステムロケール（`$LANG`）を参照しなくなりました。デフォルトは英語で、
> 日本語に切り替えたい場合は明示的に `INSTALL_LANG=ja` を指定してください。

### ローカル開発

```bash
# バックエンド起動（PostgreSQL が localhost:5432 で起動していること）
cd backend
mvn quarkus:dev

# フロントエンド起動（別ターミナル）
cd frontend
npm install --legacy-peer-deps
VITE_API_URL=http://localhost:8080 npm run dev
```

CORS のデフォルト許可オリジンは `http://localhost:5173`、`http://localhost:3000`、
`http://localhost:8080` です。OpenShift や一時デモでは `CORS_ORIGINS` または
`QUARKUS_HTTP_CORS_ORIGINS`（カンマ区切り）で上書きしてください。
エクスポート API のトークンは `Authorization: Bearer <token>` で送り、
クエリの `accessToken` / `access_token` は使いません。既存の POST ボディの
`accessToken` はそのまま利用できます。

---

## アーキテクチャ

```
               +----------------------+
               |      Web UI          |
               |  (React/PatternFly)  |
               +----------+-----------+
                          |
                    REST API (JSON)
                          |
        +-----------------+------------------+
        |     Quarkus Backend (Java 21)      |
        |                                    |
        | ① 3scale Export                    |
        | ② Parser / Compatibility Checker   |
        | ③ Converter (YAML Generator)       |
        | ④ Validation                       |
        | ⑤ Package Download (ZIP)           |
        | ⑥ Import / Apply to Cluster        |
        |    (RBAC 自動プロビジョニング含む)  |
        | ⑦ ZIP Import 変換履歴              |
        | ⑧ Gateway 情報取得                 |
        | ⑨ Namespace セットアップ           |
        +-----------------+------------------+
                    |               |
       from-3scale-to-connectivity  PostgreSQL
            -link (Adapter)         (CrunchyData)
                    |
          Connectivity Link YAML
         (Gateway / HTTPRoute / AuthPolicy /
          RateLimitPolicy / DestinationRule /
          ServiceEntry / Secret / ConfigMap)
```

**主要技術スタック**

| レイヤー | 技術 |
|---------|------|
| フロントエンド | React 18, PatternFly 5, Vite, TypeScript, react-i18next |
| バックエンド | Quarkus 3.27.5.1 (Java 21), RESTEasy Reactive, Hibernate ORM Panache |
| データベース | PostgreSQL (CrunchyData Operator 管理) |
| Kubernetes クライアント | Fabric8 Kubernetes Client 6.7.x |
| OpenAPI | SmallRye OpenAPI + Swagger UI (`/q/swagger-ui`) |
| マイグレーション | Flyway (V1〜V3) |
| デプロイ | OpenShift S2I, nginx (フロントエンド静的配信) |

---

## 処理フロー

```
① 3scale 接続設定 (URL / Access Token / Tenant / Namespace 入力)
      ↓
② API 一覧取得 (Service / Backend / MappingRule / Metrics / Policies / Authentication)
      ↓
③ 変換対象選択
      ↓
④ Compatibility Check (スコアリング: JWT / Rewrite / Lua Policy / SOAP など)
      ↓
⑤ YAML 生成 (from-3scale-to-connectivity-link アダプタ経由)
      ↓
⑥ YAML プレビュー / 編集
      ↓
⑦ Validation (YAML 構文 / CRD / Namespace / Secret / Reference 整合性)
      ↓
⑧ ZIP ダウンロード
      ↓
⑨ ZIP Import → Connectivity Link 画面から適用
     ・対象 Namespace に Role/RoleBinding を自動作成
     ・Server-Side Apply でクラスターへ適用
     ・適用後リソースを -o yaml 相当でエクスポート保存
      ↓
⑩ 変換履歴確認 (成功/失敗件数・エラー詳細・適用済み YAML の ZIP 再ダウンロード)
```

---

## 機能一覧

### 1. 3scale 接続設定

画面入力項目:
- 3scale Admin Portal URL
- Personal Access Token
- Tenant 名（オプション）
- 対象 OpenShift Namespace

バックエンドが呼び出す 3scale API:
```
GET /admin/api/services.json
GET /admin/api/backends.json
GET /admin/api/proxy_configs
GET /admin/api/policies
```

### 2. API 一覧取得

取得情報:
- Service 基本情報 (ID / 名称 / デプロイオプション)
- Backend (Private Endpoint)
- Mapping Rules
- Metrics
- Policies
- Authentication 方式 (API Key / OIDC / JWT など)

### 3. Compatibility Check

各 API ポリシー・機能を Connectivity Link でサポートできるか判定:

| 判定 | 意味 |
|------|------|
| ✔ SUPPORTED | そのまま移行可能 |
| ⚠ WARNING | 手動調整が必要 |
| × UNSUPPORTED | 非対応（要カスタム対応） |

Migration Score (0–100%) でトータルの移行難易度を数値化します。

### 4. YAML 生成

`from-3scale-to-connectivity-link` アダプタを経由して以下のリソースを生成:

```
{service-name}/
  gateway.yaml        # Kuadrant Gateway
  httproute.yaml      # HTTPRoute
  policy.yaml         # AuthPolicy / RateLimitPolicy
  tlspolicy.yaml      # Kuadrant TLSPolicy（オプトイン; cert-manager issuerRef）
  dnspolicy.yaml      # Kuadrant DNSPolicy（オプトイン; 任意の providerRefs）
  secret.yaml         # 認証情報 (REPLACE_ME プレースホルダー)
  configmap.yaml      # 設定値
  destinationrule.yaml # Istio DestinationRule (TLS 設定)
  serviceentry.yaml   # Istio ServiceEntry (外部サービス登録)
  README.md
```

> `secret.yaml` 内の `REPLACE_ME` は手動で実際の値に置き換えてください。

**TLSPolicy（オプトイン）**: Conversion 画面で「TLSPolicy を生成」を有効にすると、
`{name}-gateway` を対象とし cert-manager の `issuerRef`（UI 初期値: `ClusterIssuer` /
`letsencrypt-prod`）を持つ `tlspolicy.yaml` がパッケージに含まれます。Gateway の https
listener は引き続き Secret `{name}-tls` を参照し、適用後に cert-manager がその Secret を作成します。
生の Certificate CR は生成しません。ClusterIssuer はクラスタ上に事前に存在する必要があります。

**DNSPolicy（オプトイン）**: 「DNSPolicy と Gateway hostname を生成」を有効にすると、Gateway の
`http` / `https` 両 listener に `hostname: {dnsHostname}` が設定されます（UI 初期値は
`GET /api/cluster/domain` から `{kebabName}.{clusterDomain}` — domain には既に `apps.` が
含まれるため二重に付けません）。パッケージには `{name}-gateway` を対象とする
`dnspolicy.yaml` が含まれます。DNS provider Secret 名を指定すると `providerRefs: [{ name }]`
を含め、空なら `providerRefs` を省略してクラスタの `kuadrant.io/default-provider=true`
Secret を使います。provider の認証情報はパッケージに書き込みません。

**外部バックエンドの自動判定**: 「バックエンドは外部サービス」チェックボックスをオフのままでも、
バックエンドタイプの判定はチェックボックスの値だけでなく、3scale に登録済みのバックエンド URL
（`service.backends[0].privateEndpoint`）へ自動フォールバックするようになりました。これにより
`httproute.yaml` / `serviceentry.yaml` / `configmap.yaml` の内容が矛盾しなくなります。また、
バックエンドのポートと `DestinationRule` の TLS 発信有無は、この URL のスキームから決定されます
（`http://` → ポート 80・TLS なし / `https://` → ポート 443・TLS `SIMPLE`）。以前は常に `443` +
TLS 固定だったため、TLS 未設定のプレーン HTTP な外部バックエンド（例: edge TLS 未設定の
OpenShift Route）に接続できませんでした。

### 5. YAML プレビュー

生成した全ファイルをブラウザ上でコードビューア形式で確認・編集できます。

### 6. Validation

生成 YAML に対して以下を自動チェック:
- ✔ YAML 構文チェック
- ✔ CRD (API バージョン) チェック
- ✔ Namespace 設定チェック
- ✔ リソース参照整合性チェック
- ✔ Secret プレースホルダーチェック

### 7. ZIP ダウンロード

全ファイルを ZIP アーカイブとしてダウンロード。  
ファイル名例: `customer-api.zip`

### 8. ZIP Import / Connectivity Link 設定適用

アップロードした ZIP の YAML を:
- ブラウザ上でプレビュー・編集
- Namespace を一括置換
- `oc apply` コマンドでクラスターへ直接適用（バックエンド経由）

適用時の自動処理:
- 対象 Namespace に `migration-tool-istio-manager` Role と RoleBinding を自動作成（RBAC 自動プロビジョニング）
- Istio / Kuadrant / Gateway API / コア API リソースへの権限を付与
- `apiVersion` の自動正規化（`kuadrant.io/v1beta2 → v1` など）
- 適用後に各リソースをクラスターからエクスポートし履歴に保存

テストコマンド機能:
- 適用後の疎通確認用 curl コマンドを自動生成
- 追加パス入力欄でベース URL にパスを付与可能

### 9. 変換履歴（ZIP Import 専用）

ZIP Import を実行するたびに 1 件の履歴レコードを作成:

| 項目 | 内容 |
|------|------|
| 実行日時 | タイムスタンプ |
| 種別 | ZIP Import / Convert |
| Namespace | 適用先 Namespace |
| ステータス | COMPLETED / PARTIAL / FAILED |
| 成功件数 | 正常適用されたリソース数 |
| 失敗件数 | エラーになったリソース数 |
| エラー詳細 | 失敗リソースのファイル名・Kind・Name・エラーメッセージ（展開表示） |
| YAML ダウンロード | 適用済みリソースを `-o yaml` 相当でエクスポートした ZIP |

操作:
- チェックボックスで複数選択して一括削除（DB サイズ削減目的）
- 各行の ZIP ダウンロードボタンで適用済み YAML を取得

### 10. Gateway 情報取得

クラスターの Gateway リソース一覧と Listener 情報をリアルタイムに取得。

### 11. Namespace セットアップ

対象 Namespace に Connectivity Link 動作に必要な初期リソースを自動適用。

---

## ディレクトリ構成

```
migration-toolkit-rhcl/
├── backend/                    # Quarkus バックエンド (Java 21)
│   └── src/main/java/com/redhat/migrationtoolkit/rhcl/
│       ├── controller/         # REST エンドポイント
│       │   ├── ConnectionController.java   # 3scale 接続テスト
│       │   ├── ExportController.java       # サービス一覧・互換性チェック
│       │   ├── ConversionController.java   # YAML 生成
│       │   ├── ValidationController.java   # YAML バリデーション
│       │   ├── PackageController.java      # ZIP ダウンロード
│       │   ├── ApplyController.java        # クラスター適用・履歴保存・RBAC 自動作成
│       │   ├── ImportController.java       # ZIP アップロード・解析
│       │   ├── HistoryController.java      # 変換履歴 CRUD・ZIP ダウンロード
│       │   ├── GatewayInfoController.java  # Gateway リソース情報取得
│       │   ├── ClusterController.java      # クラスタードメイン自動検出
│       │   └── SetupController.java        # Namespace セットアップ
│       ├── service/
│       │   └── ConversionService.java  # 3scale → Connectivity Link YAML 生成ロジック
│       ├── entity/             # Panache エンティティ (JPA)
│       │   ├── ProjectEntity.java
│       │   └── ConversionHistoryEntity.java
│       ├── dto/                # リクエスト/レスポンス DTO
│       ├── model/              # ドメインモデル
│       ├── client/             # 3scale API クライアント
│       ├── util/
│       │   └── Messages.java   # i18n ResourceBundle ラッパー（Accept-Language 対応、デフォルト: 英語）
│       └── resources/
│           ├── application.properties
│           ├── db/migration/
│           │   ├── V1__init.sql            # 初期スキーマ
│           │   ├── V2__add_sequences.sql   # シーケンス追加
│           │   └── V3__import_history.sql  # Import 履歴フィールド追加
│           ├── messages_ja.properties      # バックエンド日本語メッセージ
│           └── messages_en.properties      # バックエンド英語メッセージ
├── frontend/                   # React + PatternFly フロントエンド
│   └── src/
│       ├── pages/              # 各画面コンポーネント
│       │   ├── ConnectionPage.tsx      # 3scale 接続設定
│       │   ├── APISelectionPage.tsx    # API 一覧・選択
│       │   ├── CompatibilityPage.tsx   # 互換性チェック結果
│       │   ├── ConversionPage.tsx      # YAML 生成実行
│       │   ├── YAMLViewerPage.tsx      # YAML プレビュー・編集
│       │   ├── ValidationPage.tsx      # バリデーション結果
│       │   ├── DownloadPage.tsx        # ZIP ダウンロード
│       │   ├── ImportPage.tsx          # ZIP Import・クラスター適用・curl テスト
│       │   └── HistoryPage.tsx         # 変換履歴一覧・削除・ZIP 再ダウンロード
│       ├── api/
│       │   ├── client.ts       # Axios API クライアント
│       │   └── types.ts        # TypeScript 型定義
│       ├── locales/
│       │   ├── ja.json         # 日本語 UI 文字列
│       │   └── en.json         # 英語 UI 文字列
│       ├── i18n.ts             # react-i18next 設定
│       └── App.tsx             # レイアウト・ルーティング
├── deploy/                     # OpenShift プロビジョニング
│   ├── install.sh              # 一括インストールスクリプト（日英対応）
│   ├── backend/                # Backend OpenShift リソース YAML
│   ├── frontend/               # Frontend OpenShift リソース YAML
│   └── postgres/               # PostgreSQL Operator / Cluster / SCC YAML
└── README.md
```

---

## API 一覧

| Method | Path | 説明 |
|--------|------|------|
| POST | `/api/connection/test` | 3scale 接続テスト |
| GET | `/api/services` | API サービス一覧取得 |
| GET | `/api/services/{id}` | サービス詳細取得 |
| GET | `/api/services/{id}/compatibility` | 互換性チェック |
| POST | `/api/convert` | YAML 生成（変換実行） |
| POST | `/api/validate` | YAML バリデーション |
| POST | `/api/download/zip` | ZIP ダウンロード |
| POST | `/api/apply` | クラスターへ適用（Server-Side Apply・RBAC 自動作成・履歴保存） |
| POST | `/api/import/zip` | ZIP アップロード・解析 |
| GET | `/api/history` | 変換履歴一覧（`exportedYaml` 除く軽量レスポンス） |
| GET | `/api/history/{id}` | 変換履歴詳細 |
| GET | `/api/history/{id}/download` | 変換履歴の適用済み YAML を ZIP ダウンロード |
| DELETE | `/api/history` | 変換履歴の一括削除（ID リスト指定） |
| GET | `/api/history/projects` | プロジェクト一覧 |
| GET | `/api/gateway/info` | Gateway リソース情報取得 |
| GET | `/api/cluster/domain` | バックエンド自身の Route からクラスタードメインを自動検出（「Auto-detect URL」で使用） |
| POST | `/api/setup/namespace` | Namespace セットアップ |
| GET | `/api/setup/status` | Namespace セットアップ状態確認 |

Swagger UI: `https://<backend-route>/q/swagger-ui`

---

## データモデル

```
Project
  ├── id (PK)
  ├── name
  ├── namespace
  ├── threescaleUrl
  └── createdAt

ConversionHistory
  ├── id (PK)
  ├── source          CONVERT | IMPORT
  ├── namespace       適用先 Namespace
  ├── serviceId       (変換時のみ)
  ├── serviceName     (変換時のみ)
  ├── status          COMPLETED | PARTIAL | FAILED
  ├── compatibilityScore  (変換時のみ)
  ├── totalCount      適用試行リソース数
  ├── successCount    成功リソース数
  ├── failureCount    失敗リソース数
  ├── failureDetails  JSON: [{fileName, kind, name, error}]
  ├── exportedYaml    JSON: {filename → yaml} クラスターからエクスポートした YAML
  ├── yamlContent     (変換時のみ: 生成 YAML 全文)
  └── createdAt
```

Flyway マイグレーション:
- `V1__init.sql` — 初期スキーマ（Project / ConversionHistory）
- `V2__add_sequences.sql` — シーケンス追加
- `V3__import_history.sql` — Import 履歴フィールド追加（source / namespace / totalCount など）

---

## 国際化対応 (i18n)

### フロントエンド

- `react-i18next` を使用
- デフォルト言語: **英語 (en)**
- `frontend/src/locales/ja.json` / `en.json` で文字列管理
- マストヘッド右端の **JA / EN** タブで実行時に切り替え可能

### バックエンド

- Java 標準 `ResourceBundle` を使用
- `backend/src/main/resources/messages_ja.properties` / `messages_en.properties`
- `Messages` Bean (`@ApplicationScoped`) が各コントローラに注入される
- デフォルトロケール: **英語**。ユーザー向けメッセージを返すコントローラ
  （`ApplyController`、`ImportController`）は、リクエストの `Accept-Language` ヘッダーから
  `Messages.resolveLocale(...)` でロケールを解決し、`ja` 以外はすべて英語にフォールバックします。
- フロントエンドの Axios クライアント（`frontend/src/api/client.ts`）が、リクエストごとに現在の
  `i18n.language` を `Accept-Language` として送信するため、バックエンドの応答メッセージ
  （例: `apply.success`）は常に UI の表示言語と一致します。

## テスト

特に記載がない限り `backend/` で実行します。

### 全ツール実行 + HTML レポート生成

```bash
./generate-report.sh
```

### Wapiti (DAST) も含める場合

```bash
./generate-report.sh --with-wapiti --wapiti-url http://your-app:8080
```

### Maven のみ (Checkstyle + PMD + JaCoCo + Unit Test)

```bash
mvn verify checkstyle:checkstyle pmd:pmd
```

ローカルセットアップとフロントエンドのテストは [CONTRIBUTING.md](CONTRIBUTING.md) を参照してください。SPA の API ベース URL はビルド時の `VITE_API_URL` です（ランタイムの `REACT_APP_*` ではありません）。
