package org.dmonix.kamon.otlp

import io.opentelemetry.common.AttributeKey.stringKey
import io.opentelemetry.common.{Attributes, ReadableAttributes}
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.{SpanContext, StatusCanonicalCode, TraceFlags, TraceState, Span => OtlpSpan}
import kamon.trace.Span
import kamon.trace.Span.{Kind, Link}

import java.util

object FinishedSpanData {
  val KamonTelemetrySdk =
    Resource.create(
      Attributes.newBuilder()
        .setAttribute(stringKey("telemetry.sdk.name"), "kamon")
        .setAttribute(stringKey("telemetry.sdk.language"), "scala")
        .setAttribute(stringKey("telemetry.sdk.version"), readVersion)
        .build());

  val KamonLibraryInfo = InstrumentationLibraryInfo.create("kamon", s"semver:$readVersion")

  def apply(span:Span.Finished):FinishedSpanData = new FinishedSpanData(span)

  private def readVersion:String = "x.x.x" //TODO read the proper Kamon version...from somewhere
}

class FinishedSpanData(span:Span.Finished) extends SpanData {

  override def getTraceId: String = span.trace.id.string

  override def getSpanId: String = span.id.string

  override def isSampled: Boolean = true // we wouldn't end up here in case the trace wasn't sampled

  override def getTraceState: TraceState = TraceState.getDefault //TODO here we might need to pass on a proper state, guess this requires using w3c context propagation

  override def getParentSpanId: String = span.parentId.string

  override def getResource: Resource = FinishedSpanData.KamonTelemetrySdk

  override def getInstrumentationLibraryInfo: InstrumentationLibraryInfo = FinishedSpanData.KamonLibraryInfo

  override def getName: String = span.operationName

  override def getKind: OtlpSpan.Kind = {
    span.kind match {
      case Kind.Client => OtlpSpan.Kind.CLIENT
      case Kind.Consumer => OtlpSpan.Kind.CONSUMER
      case Kind.Internal => OtlpSpan.Kind.INTERNAL
      case Kind.Producer => OtlpSpan.Kind.PRODUCER
      case Kind.Server => OtlpSpan.Kind.SERVER
      case _ => ??? //FIXME : What to do here, log and ignore?
    }
  }

  override def getAttributes: ReadableAttributes = {
    //span.tags.get()
    ???
  }

  override def getEvents: util.List[SpanData.Event] = ???

  override def getLinks: util.List[SpanData.Link] = {
    val links = new util.ArrayList[SpanData.Link](span.links.size)
    span.links.foreach(link => links.add(new LinkWrapper(link)))
    links
  }

  override def getStatus: SpanData.Status = {
    new SpanData.Status {
      override def getCanonicalCode: StatusCanonicalCode = if(span.hasError) StatusCanonicalCode.ERROR else StatusCanonicalCode.OK
      override def getDescription: String = "" //TODO : Do we need a description?
    }
  }

  override def getStartEpochNanos: Long = ???

  override def getEndEpochNanos: Long = ???

  override def getHasRemoteParent: Boolean = ???

  override def getHasEnded: Boolean = true // we wouldn't end up here in case the trace hasn't finished

  override def getTotalRecordedEvents: Int = ???

  override def getTotalRecordedLinks: Int = span.links.size

  override def getTotalAttributeCount: Int = ???

  class EventWrapper extends SpanData.Event {
    override def getName: String = ???
    override def getAttributes: Attributes = ???
    override def getEpochNanos: Long = ???
    override def getTotalAttributeCount: Int = ???
  }

  class LinkWrapper(link:Link) extends SpanData.Link {
    override def getContext: SpanContext = {
      SpanContext.create(
        link.trace.id.string,
        link.spanId.string,
        TraceFlags.getSampled, // we wouldn't end up here in case the trace wasn't sampled
        TraceState.getDefault //TODO here we might need to pass on a proper state, guess this requires using w3c context propagation
      )
    }

    override def getAttributes: Attributes = Attributes.empty() //currently there's no additional attributes we can provide

    override def getTotalAttributeCount: Int = 0 //currently there's no additional attributes we can provide
  }
}
