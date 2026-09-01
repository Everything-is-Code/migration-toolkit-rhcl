# API reference

Base path: `/api` (JSON). Interactive docs: `https://<backend-route>/q/swagger-ui`.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/connection/test` | Test 3scale connection |
| GET | `/api/services` | List API services |
| GET | `/api/services/{id}` | Get service details |
| GET | `/api/services/{id}/compatibility` | Run compatibility check |
| POST | `/api/convert` | Generate YAML (run conversion) |
| POST | `/api/validate` | Validate generated YAML |
| POST | `/api/download/zip` | Download generated YAML as ZIP |
| GET | `/api/download/history/{historyId}` | Re-download ZIP from a history entry |
| POST | `/api/apply` | Apply to cluster (Server-Side Apply, auto RBAC, history save) |
| POST | `/api/import/zip` | Upload and parse ZIP |
| GET | `/api/history` | List conversion history (lightweight, excludes exportedYaml) |
| GET | `/api/history/{id}` | Get conversion history entry |
| GET | `/api/history/{id}/download` | Download applied YAML as ZIP |
| DELETE | `/api/history` | Bulk delete history entries by ID list |
| GET | `/api/history/projects` | List projects |
| GET | `/api/gateway/info` | Get Gateway resource info from cluster |
| GET | `/api/cluster/domain` | Auto-detect cluster base domain (Conversion DNS hostname prefill) |
| GET | `/api/cluster/versions` | Cluster / operator version snapshot for Settings UI |
| GET | `/api/defaults` | Default conversion options for the UI |
| GET | `/api/settings/{key}` | Read app setting (e.g. supported policies profile) |
| PUT | `/api/settings/{key}` | Update app setting |
| POST | `/api/setup/namespace` | Apply Namespace setup resources |
| GET | `/api/setup/status` | Check Namespace setup status |

## Authentication

3scale export calls use `Authorization: Bearer <token>` (never query `accessToken` / `access_token`). Some POST bodies may still include `accessToken` for backward compatibility.

## Frontend client

The SPA uses bake-time `VITE_API_URL` (`frontend/src/api/client.ts`). There is no runtime `REACT_APP_*` env var.
