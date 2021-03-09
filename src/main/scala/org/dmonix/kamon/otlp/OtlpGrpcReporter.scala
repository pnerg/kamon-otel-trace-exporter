package org.dmonix.kamon.otlp

import com.typesafe.config.Config
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import kamon.module.SpanReporter
import kamon.trace.Span

import java.util

class OtlpGrpcReporter extends SpanReporter {
  private var otlpExporter = OtlpGrpcSpanExporter.getDefault()

  override def reportSpans(spans: Seq[Span.Finished]): Unit = {
    val oltpSpans = new util.ArrayList[SpanData](spans.size)
    spans.foreach(span => oltpSpans.add(FinishedSpanData(span)))
    otlpExporter.`export`(oltpSpans)
  }

  override def reconfigure(newConfig: Config): Unit = {
    //TODO: Should probably use the factory here to create the exporter instance...not just the default
    otlpExporter = OtlpGrpcSpanExporter.getDefault()
  }

  override def stop(): Unit = {
    //TODO : should probably block on the result
    otlpExporter.shutdown()
  }
}

