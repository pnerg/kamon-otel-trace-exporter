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
import io.opentelemetry.proto.common.v1.KeyValue
import kamon.trace.Identifier
import org.scalatest.matchers.{MatchResult, Matcher}

object CustomMatchers {
  /**
   * Converts the provided byte array to a hex string
   * @param buf
   * @return
   */
  def asHex(buf: Array[Byte]): String = buf.map("%02X" format _).mkString

  trait ByteStringMatchers {
    def equal(identifier: Identifier):Matcher[ByteString] = new Matcher[ByteString] {
      def apply(left: ByteString) = {
        MatchResult(
          left.toByteArray.sameElements(identifier.bytes),
          s"The identifiers don't match [${asHex(left.toByteArray)}] != [${identifier.string}]",
          "The identifiers match"
        )
      }
    }
    def be8Bytes:Matcher[ByteString] = beOfLength(8)
    def be16Bytes:Matcher[ByteString] = beOfLength(16)
    private def beOfLength(expectedLength:Int):Matcher[ByteString] = new Matcher[ByteString] {
      def apply(left: ByteString) = {
        MatchResult(
          left.toByteArray.size == expectedLength,
          s"The identifiers did not have expected length [${left.toByteArray.length}] != [$expectedLength]",
          "The identifier is of correct length"
        )
      }
    }
  }

  trait KeyValueMatchers {
    import java.util.{List => JList}
    import collection.JavaConverters._
    def containStringKV(key:String, expectedValue:String):Matcher[JList[KeyValue]] = new Matcher[JList[KeyValue]] {
      def apply(left: JList[KeyValue]) = {
        left.asScala.find(_.getKey.equals(key)).map(_.getValue.getStringValue) match {
          case None => MatchResult(
            false,
            s"No such Key [$key] found",
            "..."
          )
          case Some(value) => MatchResult(
            value.equals(expectedValue),
            s"The KeyValue [$key] did not have expected value [$expectedValue] != [$value]",
            "The identifier is of correct length"
          )
        }
      }
    }
  }
}
