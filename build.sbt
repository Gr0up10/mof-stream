
name := "mof-stream"

version := "0.1"

scalaVersion := "2.13.2"

scalacOptions ++= Seq("-Ymacro-annotations")

libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.6.4"
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.6.4" % Test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime
libraryDependencies += "org.kurento" % "kurento-client" % "6.13.1"

val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies += "io.circe" %% "circe-generic-extras" % "0.12.2"

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

// (optional) If you need scalapb/scalapb.proto or anything from
// google/protobuf/*.proto
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}