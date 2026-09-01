# Data model

Three Panache entities persisted to PostgreSQL via Flyway **`V1`–`V9`** (`backend/src/main/resources/db/migration/`).

## Project

```
Project
  ├── id (PK)
  ├── name
  ├── threescaleUrl
  ├── tenant
  ├── createdAt
  └── updatedAt
```

## ConversionHistory

```
ConversionHistory
  ├── id (PK)
  ├── project_id (FK → Project)
  ├── source          CONVERT | IMPORT
  ├── namespace       target Namespace
  ├── serviceId       (CONVERT only)
  ├── serviceName     (CONVERT only)
  ├── status          COMPLETED | PARTIAL | FAILED | IN_PROGRESS
  ├── compatibilityScore  (CONVERT only)
  ├── totalCount      total resources attempted
  ├── successCount    successfully applied resources
  ├── failureCount    failed resources
  ├── failureDetails  JSON: [{fileName, resourceKind, resourceName, error}]
  ├── exportedYaml    JSON: {filename → yaml} exported from cluster after apply
  ├── yamlContent     (CONVERT only: full generated YAML text)
  ├── packageName     (IMPORT: API package name)
  └── createdAt       (timestamptz, UTC)
```

## AppSettings

Key/value store (e.g. supported policies profile):

```
AppSettings
  ├── settings_key (PK)
  ├── value               TEXT (often JSON)
  └── updatedAt
```

## Flyway highlights

| Migration | Change |
|-----------|--------|
| V1–V3 | Core schema + import history fields |
| V4 | `package_name` on history |
| V5 | `created_at` with timezone |
| V6–V9 | `app_settings` table + supported-policy seeds/updates |

Entity source: `backend/src/main/java/com/redhat/migrationtoolkit/rhcl/entity/`.
