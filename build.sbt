name := "dahu"

lazy val commonSettings = Seq(
  organization := "com.github.arthur-bit-monnot",
  scalaVersion := "2.12.4",
  crossPaths := true,
  // To sync with Maven central
  publishMavenStyle := true,
  // POM settings for Sonatype
  homepage := Some(url("https://github.com/arthur-bit-monnot/copla")),
  scmInfo := Some(
    ScmInfo(url("https://github.com/arthur-bit-monnot/copla"),
            "git@github.com:arthur-bit-monnot/fape.git")),
  developers += Developer("abitmonn",
                          "Arthur Bit-Monnot",
                          "arthur.bit-monnot@laas.fr",
                          url("https://github.com/arthur-bit-monnot")),
  licenses += ("BSD-2-Clause", url("https://opensource.org/licenses/BSD-2-Clause")),
  pomIncludeRepository := (_ => false),
  scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-encoding",
    "UTF-8",
    "-unchecked",
    "-deprecation",
    //  "-Xfuture",
    "-Ypartial-unification",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    //  "-Ywarn-unused",
    "-feature",
    "-language:higherKinds",
    "-language:existentials"
  ),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),
  addCompilerPlugin("io.tryp" % "splain" % "0.2.7" cross CrossVersion.patch),
)
lazy val utestSettings = Seq(
  libraryDependencies += "com.lihaoyi" %% "utest" % "0.5.4" % "test",
  testFrameworks += new TestFramework("utest.runner.Framework")
)

lazy val root = project
  .in(file("."))
  .aggregate(utils, recursion, model, solvers, benchmarks, anml)
  .settings(
    publish := {},
    publishLocal := {}
  )

lazy val anml = project
  .in(file("copla-lang"))
  .settings(name := "copla-lang")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Seq(
    "com.lihaoyi" %% "fastparse" % "0.4.4",
    "com.github.scopt" %% "scopt" % "3.7.0",
    "com.github.arthur-bit-monnot" %% "landscaper" % "0.1.1",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  ))

lazy val utils = project
  .in(file("dahu-utils"))
  .settings(name := "dahu-utils")
  .settings(commonSettings ++ utestSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.spire-math" %% "debox" % "0.8.0"
    ))

lazy val recursion = project
  .in(file("recursion"))
  .settings(name := "recursion")
  .settings(commonSettings ++ utestSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "1.0.1",
      "org.typelevel" %% "cats-free" % "1.0.1",
    ))

lazy val model = project
  .in(file("dahu-model"))
  .dependsOn(utils, recursion)
  .settings(name := "dahu-model")
  .settings(commonSettings ++ utestSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % "2.3.3",
    ))

lazy val solvers = project
  .in(file("dahu-solvers"))
  .dependsOn(utils, model)
  .settings(name := "dahu-solvers")
  .settings(commonSettings ++ utestSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % "1.13.5" % "test",
    ))

lazy val z3 = project
  .in(file("dahu-z3"))
  .dependsOn(utils, model, solvers)
  .settings(name := "dahu-z3")
  .settings(commonSettings ++ utestSettings: _*)

lazy val benchmarks = project
  .in(file("dahu-benchmarks"))
  .dependsOn(utils, solvers, z3)
  .settings(commonSettings ++ utestSettings: _*)

resolvers += Resolver.sonatypeRepo("releases")

exportJars := true
