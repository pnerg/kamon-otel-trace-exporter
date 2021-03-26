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
 */package org.dmonix.kamon.otel

import com.typesafe.config.ConfigFactory
import kamon.Kamon

import scala.util.Random

object Main extends App {
  Kamon.init(ConfigFactory
    .defaultApplication()
    .withFallback(ConfigFactory.defaultOverrides())
    .withFallback(ConfigFactory.defaultReference())
    .resolve()
  )
  private val rand = new Random()
  private def randomPause:Unit = Thread.sleep(rand.nextInt(300)+rand.nextInt(300).longValue)

  //faking a root span from a incoming request
  Kamon.runWithSpan(Kamon.serverSpanBuilder("/purchase-item/{id}", "akka-http").tag("method", "POST").start(), true) {
    Kamon.runWithSpan(Kamon.clientSpanBuilder("check-credits", "some-database").tag("method", "query").start(), true) {
      //simulates some work
      randomPause
    }
    Kamon.runWithSpan(Kamon.clientSpanBuilder("add-order", "some-queue").tag("method", "put").start(), true) {
      //simulates some work
      randomPause
    }
  }
  Thread.sleep(10000)
  System.exit(0)
}
