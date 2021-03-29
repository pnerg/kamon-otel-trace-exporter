import sbt.Keys.{javacOptions, scalaVersion}

name := "kamon-otlp"

publishArtifact := false

val baseSettings = Seq(
  organization  := "org.dmonix.kamon",
  version := "0.5.0-SNAPSHOT",
  scalaVersion := "2.13.5",
  crossScalaVersions := Seq("2.11.12", "2.12.12", "2.13.5"),
)

lazy val lib = (project in file("lib"))
  .settings(baseSettings)
  .settings(
        name := "kamon-otel-trace-exporter",
          libraryDependencies ++= Seq(
          `kamon-bundle`,
          `exporters-otlp`,
          `grpc-netty`,
          `slf4j-simple` % Test,
          scalatest % Test
        )
  )

lazy val app = (project in file("app"))
  .settings(baseSettings)
  .settings(
    name := "kamon-otel-testapp",
    publishArtifact := false,
    fork := true, //make sure we fork before running or else java options won't be set
    mainClass in (Compile, run) := Some("org.dmonix.kamon.otel.Main"),
    run / javaOptions ++= Seq(
      "-Xmx256m",
      "-Xss256k",
      "-XX:+CrashOnOutOfMemoryError",
      "-XX:MaxMetaspaceSize=128m",
      "-Dkanela.show-banner=false",
      "-DOTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:55690"
    ),
    libraryDependencies ++= Seq(
      `slf4j-simple`
    ),
    coverageExcludedPackages := "<empty>;.*otel.*"
  )
  .dependsOn(lib)