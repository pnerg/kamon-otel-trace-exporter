import sbt.Keys.{javacOptions, scalaVersion}

organization  := "org.dmonix.kamon"
version := "0.1.0"
name := "kamon-otlp"

scalaVersion := "2.13.4"
crossScalaVersions := Seq("2.11.12", "2.12.12", "2.13.4")

libraryDependencies ++= Seq(
      `kamon-bundle`,
      `exporters-otlp`
)