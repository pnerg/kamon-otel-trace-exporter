import sbt._


object Dependencies extends AutoPlugin {

  object autoImport {
    /**
     * ------------------------------
     * Compile/hard dependencies
     * ------------------------------
     */
    val `kamon-bundle` = "io.kamon" %% "kamon-bundle" % "2.1.12"
    val `exporters-otlp` = "io.opentelemetry" % "opentelemetry-proto" % "0.17.1"
    val `grpc-netty` = "io.grpc" % "grpc-netty" % "1.36.0"

    /**
     * ------------------------------
     * Test dependencies
     * ------------------------------
     */
    val `slf4j-simple` = "org.slf4j" % "slf4j-simple" % "1.7.30"
  }

}
