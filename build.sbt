name := "events"

version := "1.0"

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.1",
  "org.joda" % "joda-money" % "0.9",
  "com.github.nscala-time" %% "nscala-time" % "1.2.0",
  "org.mongodb" %% "casbah" % "2.7.3",
  "org.json4s" %% "json4s-jackson" % "3.2.10",
  "org.scalaj" %% "scalaj-http" % "0.3.16",
  "org.joda" % "joda-convert" % "1.2"
)


