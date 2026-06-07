{{/*
OriginHub Helm helpers
*/}}
{{- define "originhub.namespace" -}}originhub{{- end -}}

{{- define "originhub.scheme" -}}
{{- if .Values.ingress.tls.enabled -}}https{{- else -}}http{{- end -}}
{{- end -}}


{{- define "originhub.corsOrigins" -}}
{{- $origins := list .Values.domain.frontendUrl -}}
{{- range .Values.domain.extraCorsOrigins }}
{{- $origins = append $origins . -}}
{{- end }}
{{- join "," $origins -}}
{{- end -}}
