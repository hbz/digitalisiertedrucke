name := """digitalisiertedrucke"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs,
  "org.culturegraph" % "metafacture-core" % "2.0.1-HBZ-SNAPSHOT",
  "org.apache.jena" % "jena-arq" % "2.10.1",
  "org.elasticsearch" % "elasticsearch" % "2.3.4",
  "net.java.dev.jna" % "jna" % "4.1.0",
  "com.github.spullara.mustache.java" % "compiler" % "0.8.13"
)

// Java project. Don't expect Scala IDE:
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java
// Use .class files instead of generated .scala files for views and routes:
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)

resolvers += Resolver.mavenLocal