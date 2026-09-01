# Migration Toolkit for Red Hat Connectivity Link（日本語）

English README: **[../README.md](../README.md)**

3scale から **Red Hat Connectivity Link**（Kuadrant / Gateway API / Istio）へ移行する **単一アプリ GUI** です。  
Quarkus バックエンド + React/PatternFly フロントエンドで、エクスポート・互換性チェック・YAML 変換・検証・クラスタ適用・履歴を **1 リポジトリ** で提供します。

---

## スクリーンショット

| 接続設定 | 互換性チェック |
|:-:|:-:|
| ![接続](../.claude/images/connect.png) | ![API 一覧](../.claude/images/apilist.png) |

| YAML 生成 | プレビュー |
|:-:|:-:|
| ![YAML](../.claude/images/yaml.png) | ![プレビュー](../.claude/images/preview.png) |

詳細なスクリーンショットと機能説明: **[documentation/user-guide.md](../documentation/user-guide.md)**（英語）

---

## クイックスタート

### Helm（推奨）

```bash
helm repo add migration-toolkit-rhcl https://everything-is-code.github.io/migration-toolkit-rhcl/
helm repo update
helm install migration-toolkit-rhcl migration-toolkit-rhcl/migration-toolkit-rhcl \
  -n migration-toolkit --create-namespace
oc -n migration-toolkit get route
```

イメージ: `quay.io/everythingascode/migration-toolkit-rhcl-{backend,frontend}:latest`

ローカル chart・GitOps・OpenShift S2I: **[documentation/deployment.md](../documentation/deployment.md)**（英語）

### ローカル開発

| ツール | バージョン |
|--------|------------|
| Java | 21 以上 |
| Maven | 3.9 以上 |
| Node.js | 22 |
| PostgreSQL | localhost:5432 |

```bash
cd backend && mvn quarkus:dev

cd frontend
npm install --legacy-peer-deps
VITE_API_URL=http://localhost:8080 npm run dev
```

セットアップ・テスト・PR: **[CONTRIBUTING.md](../CONTRIBUTING.md)**（英語）

---

## ドキュメント

| ドキュメント | 内容 |
|--------------|------|
| **[documentation/](../documentation/README.md)** | ユーザガイド、API、データモデル、デプロイ、**変換アーキテクチャ**（英語） |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | 開発環境・テスト |
| [AGENTS.md](../AGENTS.md) | AI 向けリポジトリマップ |
| [SECURITY.md](../SECURITY.md) | 脆弱性報告 |
| [README.md](../README.md) | 英語版フロントページ |

**変換アーキテクチャ** は本リポジトリ [`documentation/conversion-architecture.md`](../documentation/conversion-architecture.md) と [rhcl-sdd ストア](https://github.com/Everything-is-Code/rhcl-sdd/blob/main/docs/conversion-architecture.md) の **2 か所** にあります。ポリシー変換を拡張する際は両方を同期してください ([#40](https://github.com/Everything-is-Code/migration-toolkit-rhcl/issues/40))。

> UI 文字列は `frontend/src/locales/ja.json` で日本語化済みです。詳細ガイドの日本語版は未整備のため、上記 `documentation/` は英語が正本です。

---

## テスト（概要）

```bash
cd backend && mvn verify
cd frontend && npm run typecheck && npm test
```

カバレッジ・レポート・CI: [CONTRIBUTING.md](../CONTRIBUTING.md)

---

[Everything-is-Code/migration-toolkit-rhcl](https://github.com/Everything-is-Code/migration-toolkit-rhcl) — [CODEOWNERS](../.github/CODEOWNERS)
