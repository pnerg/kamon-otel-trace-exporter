[![Build & Test](https://github.com/pnerg/kamon-otlp-exporter/actions/workflows/scala.yml/badge.svg)](https://github.com/pnerg/kamon-otlp-exporter/actions/workflows/scala.yml)
# Kamon OpenTelemetry Trace Exporter
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
The run the test application which generates one single trace with three spans (incoming/request and two client spans)
```
sbt app/run
```

This would log something like
```
[main] INFO org.dmonix.kamon.otel.OpenTelemetryTraceReporter -  Creating OpenTelemetry Trace Reporter
[main] INFO org.dmonix.kamon.otel.OpenTelemetryTraceReporter - Configured endpoint for OTLP trace reporting [http://localhost:55690]
```
and in the collector the received spans are logged
```
2021-03-23T08:32:53.365Z        INFO    loggingexporter/logging_exporter.go:327 TracesExporter  {"#spans": 3}
2021-03-23T08:32:53.365Z        DEBUG   loggingexporter/logging_exporter.go:366 ResourceSpans #0
Resource labels:
     -> service.name: STRING(otlp-test-app)
     -> telemetry.sdk.name: STRING(kamon)
     -> telemetry.sdk.language: STRING(scala)
     -> telemetry.sdk.version: STRING(2.1.12)
InstrumentationLibrarySpans #0
InstrumentationLibrary kamon 2.1.12
Span #0
    Trace ID       : c2f8723d98431a6867ec0f4c416da008
    Parent ID      : 
    ID             : 00c76be115219aa8
    Name           : /purchase-item/{id}
    Kind           : SPAN_KIND_PRODUCER
    Start time     : 2021-03-23 08:32:48.218734795 +0000 UTC
    End time       : 2021-03-23 08:32:48.972185679 +0000 UTC
    Status code    : STATUS_CODE_OK
    Status message : 
Attributes:
     -> method: STRING(POST)
Span #1
    Trace ID       : c2f8723d98431a6867ec0f4c416da008
    Parent ID      : 00c76be115219aa8
    ID             : 1fd240d8d4635441
    Name           : add-order
    Kind           : SPAN_KIND_CLIENT
    Start time     : 2021-03-23 08:32:48.726453188 +0000 UTC
    End time       : 2021-03-23 08:32:48.971912872 +0000 UTC
    Status code    : STATUS_CODE_OK
    Status message : 
Attributes:
     -> method: STRING(put)
Span #2
    Trace ID       : c2f8723d98431a6867ec0f4c416da008
    Parent ID      : 00c76be115219aa8
    ID             : 5cc1fc597e1d7841
    Name           : check-credits
    Kind           : SPAN_KIND_CLIENT
    Start time     : 2021-03-23 08:32:48.231882663 +0000 UTC
    End time       : 2021-03-23 08:32:48.720293579 +0000 UTC
    Status code    : STATUS_CODE_OK
    Status message : 
Attributes:
     -> method: STRING(query)

```