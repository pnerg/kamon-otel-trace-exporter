package org.dmonix.kamon.otlp

import com.google.common.util.concurrent.{FutureCallback, Futures, MoreExecutors}
import com.typesafe.config.Config
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc.TraceServiceFutureStub
import io.opentelemetry.proto.collector.trace.v1.{ExportTraceServiceRequest, ExportTraceServiceResponse, TraceServiceGrpc}
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.trace.Span
import org.slf4j.LoggerFactory

import java.net.URI
import java.util.Collections

object OpenTelemetryTraceReporter {
  private val logger = LoggerFactory.getLogger(classOf[OpenTelemetryTraceReporter])

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module = {
      logger.info("Creating OpenTelemetry Trace Reporter")

      val module = new OpenTelemetryTraceReporter()
      module.reconfigure(settings.config)
      module
    }
  }

}

import OpenTelemetryTraceReporter._
class OpenTelemetryTraceReporter extends SpanReporter {
  private var traceService:TraceServiceFutureStub = null
  private var channel:ManagedChannel = null
  private var spanConverter:SpanConverter = null

  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
    if(!spans.isEmpty) {
      val resources = Collections.singletonList(spanConverter.toProtoResourceSpan(spans)) //all spans should belong to the same single resource
      val exportTraceServiceRequest = ExportTraceServiceRequest.newBuilder()
        .addAllResourceSpans(resources)
        .build();
      Futures.addCallback(traceService.export(exportTraceServiceRequest), x(), MoreExecutors.directExecutor())
    }
  }

  override def reconfigure(newConfig: Config): Unit = {

    val serviceName = newConfig.getString("kamon.environment.service")
    val serviceVersion:Option[String] = None //TODO make it possible to configure the service version
    val serviceNamespace:Option[String] = None //TODO make it possible to configure the namespace (e.g kubernetes namespace)
    val serviceInstanceId:Option[String]= None //TODO make it possible to configure the identity (e.g kubernetes pod id)
    this.spanConverter = new SpanConverter(serviceName, serviceVersion, serviceNamespace, serviceInstanceId)


    //inspiration from https://github.com/open-telemetry/opentelemetry-java/blob/main/exporters/otlp/trace/src/main/java/io/opentelemetry/exporter/otlp/trace/OtlpGrpcSpanExporterBuilder.java
    val configRoot = newConfig.getConfig("kamon.otlp.trace")
    val endPoint = URI.create(configRoot.getString("url"))
    val host = configRoot.getString("host")
    val port = configRoot.getInt("port")
    logger.info(s"Configured endpoint for OTLP trace reporting [$endPoint]")
    //TODO : stuff with TLS and possibly time-out settings...and other things I've missed
    val builder = ManagedChannelBuilder.forAddress(host, port)
    if (endPoint.getScheme().equals("https"))
      builder.useTransportSecurity()
    else
      builder.usePlaintext()

    channel = builder.build()
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

  //TODO : manage re-sending in case of failures
  private def x() = new FutureCallback[ExportTraceServiceResponse]() {
    override def onSuccess(result: ExportTraceServiceResponse): Unit = println("Success : "+result)

    override def onFailure(t: Throwable): Unit = t.printStackTrace()
  }
}

