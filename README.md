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
[main] INFO org.dmonix.kamon.otlp.OtlpGrpcReporter - Creating OTLP Trace Reporter
[main] INFO org.dmonix.kamon.otlp.OtlpGrpcReporter - Configured endpoint for OTLP trace reporting [http://localhost:55690]
```
and in the collector the received trace is logged
```
2021-03-22T14:15:59.267Z        INFO    loggingexporter/logging_exporter.go:327 TracesExporter  {"#spans": 1}
2021-03-22T14:15:59.267Z        DEBUG   loggingexporter/logging_exporter.go:366 ResourceSpans #0
Resource labels:
     -> service.name: STRING(???)
     -> service.version: STRING(x.x.x)
     -> service.namespace: STRING(???)
     -> service.instance.id: STRING(???)
     -> telemetry.sdk.name: STRING(kamon)
     -> telemetry.sdk.language: STRING(scala)
     -> telemetry.sdk.version: STRING(x.x.x)
InstrumentationLibrarySpans #0
InstrumentationLibrary kamon x.x.x
Span #0
    Trace ID       : a5733dc6abc75acac17cf1313710dfa9
    Parent ID      : 
    ID             : 39f5e8c32a24c411
    Name           : funky-query
    Kind           : SPAN_KIND_CLIENT
    Start time     : 2021-03-22 14:15:54.234082437 +0000 UTC
    End time       : 2021-03-22 14:15:54.447066076 +0000 UTC
    Status code    : STATUS_CODE_OK
    Status message : 
```