package org.dmonix.kamon.otlp

import com.typesafe.config.ConfigFactory
import kamon.Kamon

import scala.util.Random

object Main extends App {
  Kamon.init(ConfigFactory
    .defaultApplication()
    .withFallback(ConfigFactory.defaultOverrides())
    .withFallback(ConfigFactory.defaultReference())
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
