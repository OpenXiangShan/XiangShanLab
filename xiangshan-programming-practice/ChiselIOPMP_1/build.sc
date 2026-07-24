import mill._
import scalalib._
import scalafmt._
import $packages._
import $file.`rocket-chip`.common
import $file.`rocket-chip`.cde.common
import $file.`rocket-chip`.hardfloat.common

val defaultScalaVersion = "2.13.15"
val pwd = os.Path(sys.env("MILL_WORKSPACE_ROOT"))

def defaultVersions = Map(
  "chisel"        -> ivy"org.chipsalliance::chisel:6.7.0",
  "chisel-plugin" -> ivy"org.chipsalliance:::chisel-plugin:6.7.0",
  "chiseltest"    -> ivy"edu.berkeley.cs::chiseltest:6.0.0"
)
/* resolve firtool dependency */
import $ivy.`org.chipsalliance::chisel:6.7.0`
import $ivy.`org.chipsalliance::firtool-resolver:1.3.0`

trait HasChisel extends SbtModule {
  def chiselModule: Option[ScalaModule] = None

  def chiselPluginJar: T[Option[PathRef]] = None

  def chiselIvy: Option[Dep] = Some(defaultVersions("chisel"))

  def chiselPluginIvy: Option[Dep] = Some(defaultVersions("chisel-plugin"))

  override def scalaVersion = defaultScalaVersion

  override def scalacOptions = super.scalacOptions() ++
    Agg("-language:reflectiveCalls", "-Ymacro-annotations", "-Ytasty-reader")

  override def ivyDeps = super.ivyDeps() ++ Agg(chiselIvy.get)

  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(chiselPluginIvy.get)

  def resolveFirtoolDeps = T {
    firtoolresolver.Resolve(chisel3.BuildInfo.firtoolVersion.get, true) match {
      case Right(bin) => bin.path.getAbsolutePath
      case Left(err) => err
    }
  }
}

object rocketchip
  extends $file.`rocket-chip`.common.RocketChipModule
    with HasChisel {
  def scalaVersion: T[String] = T(defaultScalaVersion)

  override def millSourcePath = pwd / "rocket-chip"

  def macrosModule = macros

  def hardfloatModule = hardfloat

  def cdeModule = cde

  def mainargsIvy = ivy"com.lihaoyi::mainargs:0.7.0"

  def json4sJacksonIvy = ivy"org.json4s::json4s-jackson:4.0.7"

  object macros extends Macros

  trait Macros
    extends $file.`rocket-chip`.common.MacrosModule
      with SbtModule {

    def scalaVersion: T[String] = T(defaultScalaVersion)

    def scalaReflectIvy = ivy"org.scala-lang:scala-reflect:${defaultScalaVersion}"
  }

  object hardfloat
    extends $file.`rocket-chip`.hardfloat.common.HardfloatModule with HasChisel {

    def scalaVersion: T[String] = T(defaultScalaVersion)

    override def millSourcePath = pwd / "rocket-chip" / "hardfloat" / "hardfloat"

  }

  object cde
    extends $file.`rocket-chip`.cde.common.CDEModule with ScalaModule {

    def scalaVersion: T[String] = T(defaultScalaVersion)

    override def millSourcePath = pwd / "rocket-chip" / "cde" / "cde"
  }
}

object utility extends HasChisel {

  override def millSourcePath = pwd / "utility"

  override def moduleDeps = super.moduleDeps ++ Seq(
    rocketchip
  )

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::sourcecode:0.4.2",
  )

  object test extends SbtTests with TestModule.ScalaTest {
    override def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.2.7")
  }
}

trait ChiselIOPMPModule extends ScalaModule {

  def rocketModule: ScalaModule

  def utilityModule: ScalaModule

  override def moduleDeps = super.moduleDeps ++ Seq(rocketModule, utilityModule)

  val resourcesPATH = pwd.toString() + "/src/main/resources"
  val envPATH = sys.env("PATH") + ":" + resourcesPATH

  override def forkEnv = Map("PATH" -> envPATH)
}

object ChiselIOPMP extends ChiselIOPMPModule with HasChisel with ScalafmtModule {

  override def millSourcePath = pwd

  def rocketModule: ScalaModule = rocketchip

  def utilityModule: ScalaModule = utility

  override def ivyDeps = super.ivyDeps() ++ Agg(
    defaultVersions("chiseltest"),
    ivy"io.circe::circe-yaml:1.15.0",
    ivy"io.circe::circe-generic-extras:0.14.4"
  )

  override def scalacOptions = super.scalacOptions() ++ Agg("-deprecation", "-feature")
}
