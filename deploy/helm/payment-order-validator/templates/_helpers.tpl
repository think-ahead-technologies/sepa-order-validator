{{/*
ABOUTME: Shared template helpers: object name, recommended app.kubernetes.io labels and the selector labels.
ABOUTME: Selector labels are name and instance only, because a Deployment selector cannot change after creation.
*/}}

{{- define "pov.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Object name: the release name, or release and chart name when the release name does not contain it. */}}
{{- define "pov.fullname" -}}
{{- if contains .Chart.Name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "pov.selectorLabels" -}}
app.kubernetes.io/name: {{ include "pov.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "pov.labels" -}}
{{ include "pov.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/component: backend
app.kubernetes.io/part-of: sepa-order-validator
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}
