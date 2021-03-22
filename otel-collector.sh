docker run --rm -p 55690:55690 \
-v "${PWD}/otel-collector.yaml":/otel-local-config.yaml \
--name otelcol otel/opentelemetry-collector \
--config otel-local-config.yaml; \
