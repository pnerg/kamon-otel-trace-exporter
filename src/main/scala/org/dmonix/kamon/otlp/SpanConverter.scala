package org.dmonix.kamon.otlp

import com.google.protobuf.ByteString
import io.grpc.Attributes
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.{InstrumentationLibrarySpans, ResourceSpans, Status, Span => OtlpSpan}
import kamon.trace.Span
import kamon.trace.Span.Kind
import kamon.util.Clock

import java.time.Instant
import java.util
import java.util.concurrent.TimeUnit
/**
 * Converts Kamon spans to OTLP Spans
 */
object SpanConverter {
  Attributes.builder()
    .put(TELEMETRY_SDK_NAME, "opentelemetry")
    .put(TELEMETRY_SDK_LANGUAGE, "java")
    .put(TELEMETRY_SDK_VERSION, readVersion())
    .build()
  val x = new AutoValue_Resource(???)

  // https://github.com/open-telemetry/opentelemetry-java/blob/main/exporters/otlp/common/src/main/java/io/opentelemetry/exporter/otlp/internal/SpanAdapter.java

  /*
    public static List<ResourceSpans> toProtoResourceSpans(Collection<SpanData> spanDataList) {
    Map<Resource, Map<InstrumentationLibraryInfo, List<Span>>> resourceAndLibraryMap =
        groupByResourceAndLibrary(spanDataList);
    List<ResourceSpans> resourceSpans = new ArrayList<>(resourceAndLibraryMap.size());
    for (Map.Entry<Resource, Map<InstrumentationLibraryInfo, List<Span>>> entryResource :
        resourceAndLibraryMap.entrySet()) {
      List<InstrumentationLibrarySpans> instrumentationLibrarySpans =
          new ArrayList<>(entryResource.getValue().size());
      for (Map.Entry<InstrumentationLibraryInfo, List<Span>> entryLibrary :
          entryResource.getValue().entrySet()) {
        instrumentationLibrarySpans.add(
            InstrumentationLibrarySpans.newBuilder()
                .setInstrumentationLibrary(
                    CommonAdapter.toProtoInstrumentationLibrary(entryLibrary.getKey()))
                .addAllSpans(entryLibrary.getValue())
                .build());
      }
      resourceSpans.add(
          ResourceSpans.newBuilder()
              .setResource(ResourceAdapter.toProtoResource(entryResource.getKey()))
              .addAllInstrumentationLibrarySpans(instrumentationLibrarySpans)
              .build());
    }
    return resourceSpans;
  }
   */
  def toProtoResourceSpan(span:Span.Finished):ResourceSpans = {

    val instrumentationLibrarySpans = new util.ArrayList[InstrumentationLibrarySpans]()
    val resource: Resource = ??? //ResourceAdapter.toProtoResource(entryResource.getKey())


    ResourceSpans.newBuilder()
      .setResource(resource)
      .addAllInstrumentationLibrarySpans(instrumentationLibrarySpans)
      .build()
  }

  /*
   private static Map<Resource, Map<InstrumentationLibraryInfo, List<Span>>>
      groupByResourceAndLibrary(Collection<SpanData> spanDataList) {
    Map<Resource, Map<InstrumentationLibraryInfo, List<Span>>> result = new HashMap<>();
    for (SpanData spanData : spanDataList) {
      Resource resource = spanData.getResource();
      Map<InstrumentationLibraryInfo, List<Span>> libraryInfoListMap =
          result.get(spanData.getResource());
      if (libraryInfoListMap == null) {
        libraryInfoListMap = new HashMap<>();
        result.put(resource, libraryInfoListMap);
      }
      List<Span> spanList = libraryInfoListMap.get(spanData.getInstrumentationLibraryInfo());
      if (spanList == null) {
        spanList = new ArrayList<>();
        libraryInfoListMap.put(spanData.getInstrumentationLibraryInfo(), spanList);
      }
      spanList.add(toProtoSpan(spanData));
    }
    return result;
  }

   */

  private def toProtoSpan(span:Span.Finished):OtlpSpan = {

    /*
    ResourceSpans.newBuilder()
      .setResource(ResourceAdapter.toProtoResource(entryResource.getKey()))
      .addAllInstrumentationLibrarySpans(instrumentationLibrarySpans)
      .build())
*/

    val builder = OtlpSpan.newBuilder()
    ByteString.copyFrom(span.trace.id.bytes)

    builder
      .setTraceId(ByteString.copyFrom(span.trace.id.bytes))
      .setSpanId(ByteString.copyFrom(span.id.bytes))
      .setName(span.operationName)
      .setKind(toProtoKind(span.kind))
      .setStartTimeUnixNano(toEpocNano(span.from))
      .setEndTimeUnixNano(toEpocNano(span.to))
      .setStatus(getStatus(span))

    //builder.setTraceState()

    //add optional parent
    val parentId = span.parentId
    if(!parentId.isEmpty) builder.setParentSpanId(ByteString.copyFrom(parentId.bytes))

    builder.build()
  }

  private def toEpocNano(instant:Instant):Long = 0 //FIXME: Implement

  private def getStatus(span:Span.Finished):Status = {
    val status = if(span.hasError) Status.StatusCode.STATUS_CODE_UNKNOWN_ERROR_VALUE else Status.StatusCode.STATUS_CODE_OK_VALUE
    Status.newBuilder()
      .setCodeValue(status)
      //.setMessage(???) //TODO: Do we have status message we can use?
      .build()
  }

  private def toProtoKind(kind:Kind): OtlpSpan.SpanKind = {
    kind match {
      case Kind.Client => OtlpSpan.SpanKind.SPAN_KIND_CLIENT
      case Kind.Consumer => OtlpSpan.SpanKind.SPAN_KIND_CONSUMER
      case Kind.Internal => OtlpSpan.SpanKind.SPAN_KIND_INTERNAL
      case Kind.Producer => OtlpSpan.SpanKind.SPAN_KIND_PRODUCER
      case Kind.Server => OtlpSpan.SpanKind.SPAN_KIND_PRODUCER
      case Kind.Unknown => OtlpSpan.SpanKind.SPAN_KIND_UNSPECIFIED
      case _ => OtlpSpan.SpanKind.UNRECOGNIZED
    }
  }
}

/*
static Span toProtoSpan(SpanData spanData) {
    final Span.Builder builder = Span.newBuilder();
    spanData
        .getAttributes()
        .forEach((key, value) -> builder.addAttributes(CommonAdapter.toProtoAttribute(key, value)));
    builder.setDroppedAttributesCount(
        spanData.getTotalAttributeCount() - spanData.getAttributes().size());
    for (EventData event : spanData.getEvents()) {
      builder.addEvents(toProtoSpanEvent(event));
    }
    builder.setDroppedEventsCount(spanData.getTotalRecordedEvents() - spanData.getEvents().size());
    for (LinkData link : spanData.getLinks()) {
      builder.addLinks(toProtoSpanLink(link));
    }
    builder.setDroppedLinksCount(spanData.getTotalRecordedLinks() - spanData.getLinks().size());
    builder.setStatus(toStatusProto(spanData.getStatus()));
    return builder.build();
  }
 */