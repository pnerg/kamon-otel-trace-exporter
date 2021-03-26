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

import io.opentelemetry.proto.trace.v1.{InstrumentationLibrarySpans, ResourceSpans, Status, Span => ProtoSpan}
import org.scalatest.{Matchers, WordSpec}
import SpanConverter._
import kamon.trace.Identifier.Factory
import kamon.trace.{Span, Trace}
import kamon.trace.Span.{Kind, Link}
import kamon.trace.Trace.SamplingDecision

/**
 * Tests for [[SpanConverter]]
 */
class SpanConverterSpec extends WordSpec with Matchers {

  private val spanIDFactory = Factory.EightBytesIdentifier
  private val traceIDFactory = Factory.SixteenBytesIdentifier

  "The span converter" when {
    "using value creator functions" should {
      "create boolean/false value" in {
        val v = booleanKeyValue("key.bool.false", false)
        v.getKey shouldBe "key.bool.false"
        v.getValue.getBoolValue shouldBe false
      }
      "create boolean/true value" in {
        val v = booleanKeyValue("key.bool.true", true)
        v.getKey shouldBe "key.bool.true"
        v.getValue.getBoolValue shouldBe true
      }
      "create long value" in {
        val v = longKeyValue("key.long", 69)
        v.getKey shouldBe "key.long"
        v.getValue.getIntValue shouldBe 69
      }
      "create string value" in {
        val v = stringKeyValue("key.string", "hello world!")
        v.getKey shouldBe "key.string"
        v.getValue.getStringValue shouldBe "hello world!"
      }
    }

    "converting Kamon span kind to proto counterpart" should {
      "map Kamon.Client to ProtoSpan.SPAN_KIND_CLIENT" in {
        toProtoKind(Kind.Client) shouldBe ProtoSpan.SpanKind.SPAN_KIND_CLIENT
      }
      "map Kamon.Consumer to ProtoSpan.SPAN_KIND_CONSUMER" in {
        toProtoKind(Kind.Consumer) shouldBe ProtoSpan.SpanKind.SPAN_KIND_CONSUMER
      }
      "map Kamon.Internal to ProtoSpan.SPAN_KIND_INTERNAL" in {
        toProtoKind(Kind.Internal) shouldBe ProtoSpan.SpanKind.SPAN_KIND_INTERNAL
      }
      "map Kamon.Producer to ProtoSpan.SPAN_KIND_PRODUCER" in {
        toProtoKind(Kind.Producer) shouldBe ProtoSpan.SpanKind.SPAN_KIND_PRODUCER
      }
      "map Kamon.Server to ProtoSpan.SPAN_KIND_SERVER" in {
        toProtoKind(Kind.Server) shouldBe ProtoSpan.SpanKind.SPAN_KIND_SERVER
      }
      "map Kamon.Unknown to ProtoSpan.SPAN_KIND_UNSPECIFIED" in {
        toProtoKind(Kind.Unknown) shouldBe ProtoSpan.SpanKind.SPAN_KIND_UNSPECIFIED
      }
      "map undefined value to ProtoSpan.UNRECOGNIZED" in {
        toProtoKind(null) shouldBe ProtoSpan.SpanKind.UNRECOGNIZED
      }
    }
  }

  "should convert a Kamon link to the proto counterpart" in {
    val spanId = spanIDFactory.generate()
    val traceId = traceIDFactory.generate()
    val trace = Trace(traceId, SamplingDecision.Sample)
    val link = Span.Link(Link.Kind.FollowsFrom, trace, spanId)
    val protoLink = toProtoLink(link)
    protoLink.getTraceId shouldEqual toByteString(traceId)
    protoLink.getSpanId shouldEqual toByteString(spanId)
  }
}
