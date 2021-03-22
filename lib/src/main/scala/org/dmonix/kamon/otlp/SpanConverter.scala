package org.dmonix.kamon.otlp

import com.google.protobuf.ByteString
import io.opentelemetry.proto.common.v1.{AnyValue, InstrumentationLibrary, KeyValue}
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.{InstrumentationLibrarySpans, ResourceSpans, Status, Span => ProtoSpan}
import kamon.Kamon
import kamon.tag.Tag
import kamon.trace.Span
import kamon.trace.Span.Kind

import java.time.Instant
import java.util.Collections
import java.util.concurrent.TimeUnit
object SpanConverter {
  implicit class RichResourceBuilder(underlying: Resource.Builder) {
    def addAttributes(name:String, value:Option[String]):Resource.Builder = {
      value.map(stringKeyValue(name, _)).map(underlying.addAttributes) getOrElse underlying
    }
  }

  private def anyKeyValue(key:String, value:AnyValue):KeyValue = KeyValue.newBuilder().setKey(key).setValue(value).build
  private def stringKeyValue(key:String, value:String):KeyValue = anyKeyValue(key, AnyValue.newBuilder().setStringValue(value).build())
  private def booleanKeyValue(key:String, value:Boolean):KeyValue = anyKeyValue(key, AnyValue.newBuilder().setBoolValue(value).build())
  private def longKeyValue(key:String, value:Long):KeyValue = anyKeyValue(key,AnyValue.newBuilder().setIntValue(value).build())

  private val kamonVersion = Kamon.status().settings().version
}

import SpanConverter._
/**
 * Converts Kamon spans to OTLP Spans
 */
class SpanConverter(serviceName:String, serviceVersion:Option[String], serviceNamespace:Option[String], serviceInstanceId:Option[String]) {

  private val instrumentationLibrary:InstrumentationLibrary = InstrumentationLibrary.newBuilder().setName("kamon").setVersion(kamonVersion).build()

  private val resource = Resource.newBuilder()
      .addAttributes(stringKeyValue("service.name", serviceName)) //TODO pick service name from config
      .addAttributes("service.version", serviceVersion) //TODO pick service version from config...or somewhere
      .addAttributes("service.namespace", serviceNamespace) //TODO make it possible to configure the namespace (e.g kubernetes namespace)
      .addAttributes("service.instance.id", serviceInstanceId) //TODO make it possible to configure the identity (e.g kubernetes pod id)
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

    import collection.JavaConverters._
    val attributes = span.tags.iterator.map(toProtoKeyValue).toList.asJava

    builder
      .setTraceId(ByteString.copyFrom(span.trace.id.bytes))
      .setSpanId(ByteString.copyFrom(span.id.bytes))
      .setName(span.operationName)
      .setKind(toProtoKind(span.kind))
      .setStartTimeUnixNano(toEpocNano(span.from))
      .setEndTimeUnixNano(toEpocNano(span.to))
      .setStatus(getStatus(span))
      .addAllAttributes(attributes) //TODO add all tags

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

  private def toProtoKeyValue(tag: Tag):KeyValue = {
    tag match {
      case t: Tag.String  => stringKeyValue(tag.key, t.value)
      case t: Tag.Boolean => booleanKeyValue(tag.key, t.value)
      case t: Tag.Long    => longKeyValue(tag.key, t.value)
    }
  }

}