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
package org.dmonix.kamon.otel

import com.google.common.util.concurrent.{FutureCallback, Futures}
import com.typesafe.config.Config
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc.TraceServiceFutureStub
import io.opentelemetry.proto.collector.trace.v1.{ExportTraceServiceRequest, ExportTraceServiceResponse, TraceServiceGrpc}
import io.opentelemetry.proto.common.v1.InstrumentationLibrary
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.ResourceSpans
import kamon.Kamon
import kamon.module.{Module, ModuleFactory, SpanReporter}
import kamon.trace.Span
import org.slf4j.LoggerFactory

import java.util.Collections
import java.util.concurrent.{Executors, ThreadFactory, TimeUnit}
import SpanConverter._

object OpenTelemetryTraceReporter {
  private val logger = LoggerFactory.getLogger(classOf[OpenTelemetryTraceReporter])
  private val kamonVersion = Kamon.status().settings().version

  class Factory extends ModuleFactory {
    override def create(settings: ModuleFactory.Settings): Module = {
      logger.info("Creating OpenTelemetry Trace Reporter")

      val module = new OpenTelemetryTraceReporter()
      module.reconfigure(settings.config)
      module
    }
  }

}

import org.dmonix.kamon.otel.OpenTelemetryTraceReporter._
class OpenTelemetryTraceReporter extends SpanReporter {
  private val executor = Executors.newSingleThreadExecutor(new ThreadFactory {
    override def newThread(r: Runnable): Thread = new Thread(r, "OpenTelemetryTraceReporterRemote")
  })

  private var traceService:TraceServiceFutureStub = null
  private var channel:ManagedChannel = null
  private var spanConverterFunc:Seq[Span.Finished]=>ResourceSpans = null

  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
    if(!spans.isEmpty) {
      val resources = Collections.singletonList(spanConverterFunc(spans)) //all spans should belong to the same single resource
      val exportTraceServiceRequest = ExportTraceServiceRequest.newBuilder()
        .addAllResourceSpans(resources)
        .build()
      Futures.addCallback(traceService.export(exportTraceServiceRequest), exportCallback(), executor)
    }
  }

  override def reconfigure(newConfig: Config): Unit = {
    //inspiration from https://github.com/open-telemetry/opentelemetry-java/blob/main/exporters/otlp/trace/src/main/java/io/opentelemetry/exporter/otlp/trace/OtlpGrpcSpanExporterBuilder.java
    val otelExporterConfig = newConfig.getConfig("kamon.otel.trace")
    val host = otelExporterConfig.getString("host")
    val port = otelExporterConfig.getInt("port")
    val protocol = otelExporterConfig.getString("protocol")

    //pre-generate the function for converting Kamon span to proto span
    val instrumentationLibrary:InstrumentationLibrary = InstrumentationLibrary.newBuilder().setName("kamon").setVersion(kamonVersion).build()
    val resource:Resource = buildResource(otelExporterConfig.getBoolean("include-environment-tags"))
    this.spanConverterFunc =  SpanConverter.toProtoResourceSpan(resource, instrumentationLibrary)

    logger.info(s"Configured endpoint for OTLP trace reporting [$host:$port]")
    //TODO : stuff with TLS and possibly time-out settings...and other things I've missed
    val builder = ManagedChannelBuilder.forAddress(host, port)
    if (protocol.equals("https"))
      builder.useTransportSecurity()
    else
      builder.usePlaintext()

    this.channel = builder.build()
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

  private def buildResource(includeEnvTags:Boolean):Resource = {
    val env = Kamon.environment
    val builder = Resource.newBuilder()
      .addAttributes(stringKeyValue("service.name", env.service))
      .addAttributes(stringKeyValue("telemetry.sdk.name", "kamon"))
      .addAttributes(stringKeyValue("telemetry.sdk.language", "scala"))
      .addAttributes(stringKeyValue("telemetry.sdk.version", kamonVersion))

    //add all kamon.environment.tags as KeyValues to the Resource object
    if(includeEnvTags) {
      env.tags.iterator().map(toProtoKeyValue).foreach(builder.addAttributes)
    }

    builder.build()
  }
}

