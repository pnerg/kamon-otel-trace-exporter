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

    /**
     * ------------------------------
     * Test dependencies
     * ------------------------------
     */
  }

}
