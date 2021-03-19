package org.dmonix.kamon.otlp

import com.google.protobuf.ByteString
import io.grpc.Attributes
import io.opentelemetry.proto.common.v1.{AnyValue, InstrumentationLibrary, KeyValue}
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.{InstrumentationLibrarySpans, ResourceSpans, Status, Span => ProtoSpan}
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

  private val instrumentationLibrary:InstrumentationLibrary = InstrumentationLibrary.newBuilder().setName("kamon").setVersion(kamonVersion).build()

  private val resource = Resource.newBuilder()
      .addAttributes(stringKeyValue("service.name", "???")) //TODO pick service name from config
      .addAttributes(stringKeyValue("service.version", "x.x.x")) //TODO pick service version from config...or somewhere
      .addAttributes(stringKeyValue("service.namespace", "???")) //TODO make it possible to configure the namespace (e.g kubernetes namespace)
      .addAttributes(stringKeyValue("service.instance.id", "???")) //TODO make it possible to configure the identity (e.g kubernetes pod id)
      .addAttributes(stringKeyValue("telemetry.sdk.name", "kamon"))
      .addAttributes(stringKeyValue("telemetry.sdk.language", "scala"))
      .addAttributes(stringKeyValue("telemetry.sdk.version", kamonVersion))
      .addAttributes(stringKeyValue("telemetry.sdk.version", kamonVersion))
      .addAttributes(stringKeyValue("telemetry.sdk.version", kamonVersion))
      .build()

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
  def toProtoResourceSpan(spans:Seq[Span.Finished]):ResourceSpans = {

    import collection.JavaConverters._
    val protoSpans = spans.map(toProtoSpan).asJava

    val instrumentationLibrarySpans:InstrumentationLibrarySpans = InstrumentationLibrarySpans.newBuilder().setInstrumentationLibrary(instrumentationLibrary).addAllSpans(protoSpans).build()
    val instrumentationLibrarySpansList = new util.ArrayList[InstrumentationLibrarySpans]()
    instrumentationLibrarySpansList.add(instrumentationLibrarySpans)

    ResourceSpans.newBuilder()
      .setResource(resource)
      .addAllInstrumentationLibrarySpans(instrumentationLibrarySpansList)
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

  private def toEpocNano(instant:Instant):Long = 0 //FIXME: Implement

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

  private def stringKeyValue(key:String, value:String):KeyValue = KeyValue.newBuilder().setKey("").setValue(AnyValue.newBuilder().setStringValue("").build()).build
  private def kamonVersion:String = "x.x.x" //TODO pick the proper version from...somewhere
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