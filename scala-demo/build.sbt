scalaVersion := "3.9.0"

name := "mcp2-scala-demo"

libraryDependencies ++= Seq(
  "com.jamesward" %% "zio-http-mcp" % "0.8.3",
  "org.slf4j" % "slf4j-simple" % "2.0.20",

  // Skills over MCP: a SkillsJar from Maven Central, served straight off the classpath
  "com.skillsjars" % "anthropics__skills__brand-guidelines" % "2026_02_25-3d59511",
)

scalacOptions ++= Seq("-deprecation", "-Werror")

fork := true

javaOptions ++= Seq(
  "--sun-misc-unsafe-memory-access=allow",
  "--enable-native-access=ALL-UNNAMED",
  "-Djava.net.preferIPv4Stack=true",
)
