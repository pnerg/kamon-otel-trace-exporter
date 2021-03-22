[![Build & Test](https://github.com/pnerg/kamon-otlp-exporter/actions/workflows/scala.yml/badge.svg)](https://github.com/pnerg/kamon-otlp-exporter/actions/workflows/scala.yml)
# kamon-otlp-exporter
Provides a OpenTelemetry (OTLP) exporter for Kamon spans

The reporter relies on the [opentelemetry-proto](https://github.com/open-telemetry/opentelemetry-proto) library for the gRPC communication with an OpenTelemetry (OTLP) service.

## Local testing
To see the results of the exporter one can runt it against an instance of the [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/).  
The collector can be started in a container, configured to receive gRPC calls with trace data.
Run the script
```
./otel-collector.sh
```
If will start the OTEL Collector, configured for port `55690` and only to log all received traces.
The run the test application which generates one single trace
```
sbt app/run
```

This would log something like
```
[main] INFO org.dmonix.kamon.otlp.OpenTelemetryTraceReporter -  Creating OpenTelemetry Trace Reporter
[main] INFO org.dmonix.kamon.otlp.OpenTelemetryTraceReporter - Configured endpoint for OTLP trace reporting [http://localhost:55690]
```
and in the collector the received trace is logged
```
2021-03-22T19:37:19.675Z        INFO    loggingexporter/logging_exporter.go:327 TracesExporter  {"#spans": 1}
2021-03-22T19:37:19.676Z        DEBUG   loggingexporter/logging_exporter.go:366 ResourceSpans #0
Resource labels:
     -> service.name: STRING(otlp-test-app)
     -> telemetry.sdk.name: STRING(kamon)
     -> telemetry.sdk.language: STRING(scala)
     -> telemetry.sdk.version: STRING(2.1.12)
InstrumentationLibrarySpans #0
InstrumentationLibrary kamon 2.1.12
Span #0
    Trace ID       : cd44a103f4c4740385a62692087feb67
    Parent ID      : 
    ID             : 6fb5bee4fe5a8b83
    Name           : funky-query
    Kind           : SPAN_KIND_CLIENT
    Start time     : 2021-03-22 19:37:14.531087217 +0000 UTC
    End time       : 2021-03-22 19:37:14.930654722 +0000 UTC
    Status code    : STATUS_CODE_OK
    Status message : 
Attributes:
     -> method: STRING(query)
```