name := """digitalisiertedrucke"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs,
  "org.culturegraph" % "metafacture-core" % "4.0.0-HBZ-SNAPSHOT",
  "org.elasticsearch" % "elasticsearch" % "2.3.4",
  "net.java.dev.jna" % "jna" % "4.1.0",
  "com.github.spullara.mustache.java" % "compiler" % "0.8.13",
  "com.github.jsonld-java" % "jsonld-java" % "0.3",
  "com.github.jsonld-java" % "jsonld-java-jena" % "0.3",
  "org.apache.jena" % "jena-arq" % "2.9.3"
)

// Java project. Don't expect Scala IDE:
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java
// Use .class files instead of generated .scala files for views and routes:
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)

resolvers += Resolver.mavenLocal