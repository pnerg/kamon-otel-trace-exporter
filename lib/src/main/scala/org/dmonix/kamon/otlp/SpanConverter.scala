package org.dmonix.kamon.otlp

import com.google.protobuf.ByteString
import io.opentelemetry.proto.common.v1.{AnyValue, InstrumentationLibrary, KeyValue}
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.{InstrumentationLibrarySpans, ResourceSpans, Status, Span => ProtoSpan}
import kamon.trace.Span
import kamon.trace.Span.Kind

import java.time.Instant
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Converts Kamon spans to OTLP Spans
 */
object SpanConverter {

  private val instrumentationLibrary:InstrumentationLibrary = InstrumentationLibrary.newBuilder().setName("kamon").setVersion(kamonVersion).build()

  private val resource = Resource.newBuilder()
      .addAttributes(stringKeyValue("service.name", "???")) //TODO pick service name from config
      .addAttributes(stringKeyValue("service.version", "x.x.x")) //TODO pick service version from config...or somewhere
      .addAttributes(stringKeyValue("service.namespace", "???")) //TODO make it possible to configure the namespace (e.g kubernetes namespace)
      .addAttributes(stringKeyValue("service.instance.id", "???")) //TODO make it possible to configure the identity (e.g kubernetes pod id)
      .addAttributes(stringKeyValue("telemetry.sdk.name", "kamon"))
      .addAttributes(stringKeyValue("telemetry.sdk.language", "scala"))
      .addAttributes(stringKeyValue("telemetry.sdk.version", kamonVersion))
      .build()

  // https://github.com/open-telemetry/opentelemetry-java/blob/main/exporters/otlp/common/src/main/java/io/opentelemetry/exporter/otlp/internal/SpanAdapter.java

  def toProtoResourceSpan(spans:Seq[Span.Finished]):ResourceSpans = {

    import collection.JavaConverters._
    val protoSpans = spans.map(toProtoSpan).asJava

    val instrumentationLibrarySpans = InstrumentationLibrarySpans.newBuilder().setInstrumentationLibrary(instrumentationLibrary).addAllSpans(protoSpans).build()

    ResourceSpans.newBuilder()
      .setResource(resource)
      .addAllInstrumentationLibrarySpans(Collections.singletonList(instrumentationLibrarySpans))
      .build()
  }

  private def toProtoSpan(span:Span.Finished):ProtoSpan = {
    val builder = ProtoSpan.newBuilder()
    ByteString.copyFrom(span.trace.id.bytes)

    builder
      .setTraceId(ByteString.copyFrom(span.trace.id.bytes))
      .setSpanId(ByteString.copyFrom(span.id.bytes))
      .setName(span.operationName)
      .setKind(toProtoKind(span.kind))
      .setStartTimeUnixNano(toEpocNano(span.from))
      .setEndTimeUnixNano(toEpocNano(span.to))
      .setStatus(getStatus(span))

    //TODO add links if such exists in the Kamon span

    //builder.setTraceState()

    //add optional parent
    val parentId = span.parentId
    if(!parentId.isEmpty) builder.setParentSpanId(ByteString.copyFrom(parentId.bytes))

    builder.build()
  }

  private def toEpocNano(instant:Instant):Long = TimeUnit.NANOSECONDS.convert(instant.getEpochSecond, TimeUnit.SECONDS) + instant.getNano

  private def getStatus(span:Span.Finished):Status = {
    //according to the spec the deprecate code needs to be set for backwards compatibility reasons
    //https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/trace/v1/trace.proto
    val (status, deprecatedStatus) = if(span.hasError) (Status.StatusCode.STATUS_CODE_ERROR_VALUE, Status.DeprecatedStatusCode.DEPRECATED_STATUS_CODE_UNKNOWN_ERROR_VALUE) else (Status.StatusCode.STATUS_CODE_OK_VALUE, Status.DeprecatedStatusCode.DEPRECATED_STATUS_CODE_OK_VALUE)
    Status.newBuilder()
      .setCodeValue(status)
      .setDeprecatedCodeValue(deprecatedStatus)
      //.setMessage(???) //TODO: Do we have status message we can use?
      .build()
  }

  private def toProtoKind(kind:Kind): ProtoSpan.SpanKind = {
    kind match {
      case Kind.Client => ProtoSpan.SpanKind.SPAN_KIND_CLIENT
      case Kind.Consumer => ProtoSpan.SpanKind.SPAN_KIND_CONSUMER
      case Kind.Internal => ProtoSpan.SpanKind.SPAN_KIND_INTERNAL
      case Kind.Producer => ProtoSpan.SpanKind.SPAN_KIND_PRODUCER
      case Kind.Server => ProtoSpan.SpanKind.SPAN_KIND_PRODUCER
      case Kind.Unknown => ProtoSpan.SpanKind.SPAN_KIND_UNSPECIFIED
      case _ => ProtoSpan.SpanKind.UNRECOGNIZED
    }
  }

  private def stringKeyValue(key:String, value:String):KeyValue = KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value).build()).build
  private def kamonVersion:String = "x.x.x" //TODO pick the proper version from...somewhere
}