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
  private def randomPause:Unit = Thread.sleep(rand.nextInt(300).longValue)

  Kamon.runWithSpan(Kamon.clientSpanBuilder("funky-query", "cassandra").tagMetrics("method", "query").tagMetrics("private", "true").start(), true) {
    //simulates some work
    randomPause
  }

  println("STARTED!")
  Thread.sleep(10000)
  System.exit(0)
}
