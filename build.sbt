name := "fogspark"

version := "0.1"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.apache.spark" % "spark-core_2.11" % "2.2.0" exclude("org.apache.hadoop", "hadoop-yarn-server-web-proxy"),
  "org.apache.spark" % "spark-graphx_2.11" % "2.2.0" exclude("org.apache.hadoop", "hadoop-yarn-server-web-proxy"),
  "com.typesafe.akka" %% "akka-http" % "10.1.3",
  "com.typesafe.akka" %% "akka-stream" % "2.5.12", // or whatever the latest version is
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.3"
)