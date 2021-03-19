package org.dmonix.kamon.otlp

import com.typesafe.config.Config
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc.TraceServiceFutureStub
import io.opentelemetry.proto.collector.trace.v1.{ExportTraceServiceRequest, TraceServiceGrpc}
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.trace.Span

import java.net.URI
import java.util.Collections

object OtlpGrpcReporter {

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module =
      new OtlpGrpcReporter()
  }

}

class OtlpGrpcReporter extends SpanReporter {
  private var traceService:TraceServiceFutureStub = ???
  private var channel:ManagedChannel = ???

  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
    //TODO: Should perhaps be done asynchronously not to steal the reporter thread
    val resources = Collections.singletonList(SpanConverter.toProtoResourceSpan(spans)) //all spans should belong to the same single resource
    val exportTraceServiceRequest = ExportTraceServiceRequest.newBuilder()
      .addAllResourceSpans(resources)
      .build();

    traceService.export(exportTraceServiceRequest)
  }

  override def reconfigure(newConfig: Config): Unit = {
    //inspiration from https://github.com/open-telemetry/opentelemetry-java/blob/main/exporters/otlp/trace/src/main/java/io/opentelemetry/exporter/otlp/trace/OtlpGrpcSpanExporterBuilder.java
    val endPoint = URI.create(newConfig.getString("url"))
    val managedChannelBuilder = ManagedChannelBuilder.forTarget(endPoint.getAuthority())
    //TODO : stuff with TLS and possibly time-out settings...and other things I've missed
    channel = managedChannelBuilder.build()
    this.traceService = TraceServiceGrpc.newFutureStub(channel);
  }

  override def stop(): Unit = {
    //TODO: close underlying channel and make sure all traces are flushed
    channel.shutdown()
    /*
        final CompletableResultCode result = new CompletableResultCode();
    managedChannel.notifyWhenStateChanged(ConnectivityState.SHUTDOWN, result::succeed);
    managedChannel.shutdown();
    this.spansSeen.unbind();
    this.spansExportedSuccess.unbind();
    this.spansExportedFailure.unbind();
    return result;
     */
  }
}

