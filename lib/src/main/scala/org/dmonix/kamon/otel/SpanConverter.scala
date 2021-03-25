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

import com.google.protobuf.ByteString
import io.opentelemetry.proto.common.v1.{AnyValue, InstrumentationLibrary, KeyValue}
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.{InstrumentationLibrarySpans, ResourceSpans, Status, Span => ProtoSpan}
import kamon.tag.Tag
import kamon.tag.Tag.unwrapValue
import kamon.trace.Span.Kind
import kamon.trace.{Identifier, Span}
import org.slf4j.LoggerFactory

import java.time.Instant
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Converts Kamon spans to OTel Spans
 */
private[otel] object SpanConverter {
  private val logger = LoggerFactory.getLogger(SpanConverter.getClass)

  private[otel] def anyKeyValue(key:String, value:AnyValue):KeyValue = KeyValue.newBuilder().setKey(key.replaceAll("-", ".")).setValue(value).build
  private[otel] def stringKeyValue(key:String, value:String):KeyValue = anyKeyValue(key, AnyValue.newBuilder().setStringValue(value).build())
  private[otel] def booleanKeyValue(key:String, value:Boolean):KeyValue = anyKeyValue(key, AnyValue.newBuilder().setBoolValue(value).build())
  private[otel] def longKeyValue(key:String, value:Long):KeyValue = anyKeyValue(key,AnyValue.newBuilder().setIntValue(value).build())

  private[otel] def toProtoResourceSpan(resource:Resource, instrumentationLibrary:InstrumentationLibrary)(spans:Seq[Span.Finished]):ResourceSpans = {

    import collection.JavaConverters._
    val protoSpans = spans.map(toProtoSpan)

    val instrumentationLibrarySpans = InstrumentationLibrarySpans.newBuilder()
      .setInstrumentationLibrary(instrumentationLibrary)
      .addAllSpans(protoSpans.asJava)
      .build()

    ResourceSpans.newBuilder()
      .setResource(resource)
      .addAllInstrumentationLibrarySpans(Collections.singletonList(instrumentationLibrarySpans))
      .build()
  }

  /**
   * Converts a Kamon span to a proto span
   * @param span
   * @return
   */
  private[otel] def toProtoSpan(span:Span.Finished):ProtoSpan = {
    val builder = ProtoSpan.newBuilder()
    ByteString.copyFrom(span.trace.id.bytes)

    //converts Kamon span tags to KeyValue attributes
    val attributes:List[KeyValue] = span.tags.iterator.map(toProtoKeyValue).toList

    //converts Kamon span links to proto links
    val links:Seq[ProtoSpan.Link] = span.links.map(toProtoLink)

    import collection.JavaConverters._
    builder
      .setTraceId(toByteString(span.trace.id))
      .setSpanId(toByteString(span.id))
      .setName(span.operationName)
      .setKind(toProtoKind(span.kind))
      .setStartTimeUnixNano(toEpocNano(span.from))
      .setEndTimeUnixNano(toEpocNano(span.to))
      .setStatus(getStatus(span))
      .addAllAttributes(attributes.asJava)
      .addAllLinks(links.asJava)


    //TODO add set traceState once we have something to set. Need w3c context
    //It is a trace_state in w3c-trace-context format: https://www.w3.org/TR/trace-context/#tracestate-header
    //See also https://github.com/w3c/distributed-tracing for more details about this field.
    //builder.setTraceState()

    //add optional parent
    val parentId = span.parentId
    if(!parentId.isEmpty) builder.setParentSpanId(ByteString.copyFrom(parentId.bytes))

    builder.build()
  }

  /**
   * Converts the instant to EPOC nanos
   * @param instant
   * @return
   */
  private[otel] def toEpocNano(instant:Instant):Long = TimeUnit.NANOSECONDS.convert(instant.getEpochSecond, TimeUnit.SECONDS) + instant.getNano

  private[otel] def getStatus(span:Span.Finished):Status = {
    //according to the spec the deprecate code needs to be set for backwards compatibility reasons
    //https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/trace/v1/trace.proto
    val (status, deprecatedStatus) = if(span.hasError) (Status.StatusCode.STATUS_CODE_ERROR_VALUE, Status.DeprecatedStatusCode.DEPRECATED_STATUS_CODE_UNKNOWN_ERROR_VALUE) else (Status.StatusCode.STATUS_CODE_OK_VALUE, Status.DeprecatedStatusCode.DEPRECATED_STATUS_CODE_OK_VALUE)
    Status.newBuilder()
      .setCodeValue(status)
      .setDeprecatedCodeValue(deprecatedStatus)
      //.setMessage(???) //TODO: Do we have status message we can use?
      .build()
  }

  /**
   * Converts a Kamon span kind to a proto span kind
   * @param kind
   * @return
   */
  private[otel] def toProtoKind(kind:Kind): ProtoSpan.SpanKind = {
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

  /**
   * Converts a Kamon tag to a proto KeyValue
   * @param tag
   * @return
   */
  private[otel] def toProtoKeyValue(tag: Tag):KeyValue = {
    tag match {
      case t: Tag.String  => stringKeyValue(tag.key, t.value)
      case t: Tag.Boolean => booleanKeyValue(tag.key, t.value)
      case t: Tag.Long    => longKeyValue(tag.key, t.value)
      case _ => { //this cannot happen unless new Tag types are added to Kamon core, the code is more of a safeguard for "just in case"
        logger.warn(s"Failed to map Tag [$tag] to a KeyValue type, will attempt to convert to a string")
        stringKeyValue(tag.key, unwrapValue(tag).toString) //last resort trying to create a string of this unknown Tag type
      }
    }
  }

  /**
   * Converts a Kamon span link to a proto span link
   * @param link
   * @return
   */
  private[otel] def toProtoLink(link:Span.Link):ProtoSpan.Link = {
    ProtoSpan.Link.newBuilder()
      .setTraceId(toByteString(link.trace.id))
      .setSpanId(toByteString(link.spanId))
      .build()
  }

  /**
   * Converts a Kamon identifier to a proto ByteString
   * @param id
   * @return
   */
  private[otel] def toByteString(id:Identifier):ByteString = ByteString.copyFrom(id.bytes)
}