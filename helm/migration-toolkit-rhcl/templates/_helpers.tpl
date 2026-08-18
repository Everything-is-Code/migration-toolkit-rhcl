{{- define "migration-toolkit-rhcl.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "migration-toolkit-rhcl.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- printf "%s" $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "migration-toolkit-rhcl.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: migration-toolkit-rhcl
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{- define "migration-toolkit-rhcl.backend" -}}
{{ include "migration-toolkit-rhcl.fullname" . }}-backend
{{- end }}

{{- define "migration-toolkit-rhcl.frontend" -}}
{{ include "migration-toolkit-rhcl.fullname" . }}-frontend
{{- end }}

{{- define "migration-toolkit-rhcl.postgresql" -}}
{{ include "migration-toolkit-rhcl.fullname" . }}-postgresql
{{- end }}

{{- define "migration-toolkit-rhcl.dbHost" -}}
{{- if .Values.postgresql.enabled }}
{{- include "migration-toolkit-rhcl.postgresql" . }}
{{- else }}
{{- required "postgresql.host is required when postgresql.enabled is false" .Values.postgresql.host }}
{{- end }}
{{- end }}

{{- define "migration-toolkit-rhcl.backendUpstream" -}}
{{ include "migration-toolkit-rhcl.backend" . }}.{{ .Release.Namespace }}.svc:8080
{{- end }}

{{/*
Secret that holds DB_USER / DB_PASSWORD / DB_NAME for backend and embedded Postgres.
*/}}
{{- define "migration-toolkit-rhcl.dbSecretName" -}}
{{- if .Values.postgresql.existingSecret }}
{{- .Values.postgresql.existingSecret }}
{{- else }}
{{- include "migration-toolkit-rhcl.fullname" . }}-config
{{- end }}
{{- end }}

{{/*
Fail fast when neither password nor existingSecret is provided.
*/}}
{{- define "migration-toolkit-rhcl.requireDbCredentials" -}}
{{- if not .Values.postgresql.existingSecret }}
{{- required "postgresql.password is required (or set postgresql.existingSecret)" .Values.postgresql.password }}
{{- end }}
{{- end }}

{{/*
PostgreSQL image reference: prefer digest pin over floating tag.
*/}}
{{- define "migration-toolkit-rhcl.postgresqlImage" -}}
{{- $repo := .Values.postgresql.image.repository -}}
{{- if .Values.postgresql.image.digest -}}
{{- printf "%s@%s" $repo .Values.postgresql.image.digest -}}
{{- else -}}
{{- printf "%s:%s" $repo (.Values.postgresql.image.tag | default "latest") -}}
{{- end -}}
{{- end }}
