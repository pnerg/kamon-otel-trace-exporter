/**
 *  Copyright 2021 Peter Nerg
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.dmonix.kamon.otlp

import com.google.common.util.concurrent.{FutureCallback, Futures}
import com.typesafe.config.Config
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc.TraceServiceFutureStub
import io.opentelemetry.proto.collector.trace.v1.{ExportTraceServiceRequest, ExportTraceServiceResponse, TraceServiceGrpc}
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.trace.Span
import org.slf4j.LoggerFactory

import java.net.URI
import java.util.Collections
import java.util.concurrent.{Executors, ThreadFactory, TimeUnit}

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

import org.dmonix.kamon.otlp.OpenTelemetryTraceReporter._
class OpenTelemetryTraceReporter extends SpanReporter {
  private val executor = Executors.newSingleThreadExecutor(new ThreadFactory {
    override def newThread(r: Runnable): Thread = new Thread(r, "OpenTelemetryTraceReporterRemote")
  })

  private var traceService:TraceServiceFutureStub = null
  private var channel:ManagedChannel = null
  private var spanConverter:SpanConverter = null

  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
    if(!spans.isEmpty) {
      val resources = Collections.singletonList(spanConverter.toProtoResourceSpan(spans)) //all spans should belong to the same single resource
      val exportTraceServiceRequest = ExportTraceServiceRequest.newBuilder()
        .addAllResourceSpans(resources)
        .build()
      Futures.addCallback(traceService.export(exportTraceServiceRequest), exportCallback(), executor)
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
    channel.awaitTermination(5, TimeUnit.SECONDS)
  }

  private def exportCallback():FutureCallback[ExportTraceServiceResponse] = new FutureCallback[ExportTraceServiceResponse]() {
    override def onSuccess(result: ExportTraceServiceResponse): Unit = logger.debug("Successfully exported traces")

    //TODO is there result for which a retry is relevant? Perhaps a glitch in the receiving service
    override def onFailure(t: Throwable): Unit = logger.error("Failed to export traces", t)
  }
}

