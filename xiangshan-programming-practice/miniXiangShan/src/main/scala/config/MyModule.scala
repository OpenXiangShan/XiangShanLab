package minixiangshan.config

import chisel3._
import chisel3.util._

abstract class NSModule(implicit val p: Parameters) extends Module
  with HasCoreParameters
  with HasCsrParameters
  

abstract class NSRawModule(implicit val p: Parameters) extends RawModule
  with HasCoreParameters
  with HasCsrParameters

abstract class NSBundle(implicit val p: Parameters) extends Bundle
  with HasCoreParameters
  with HasCsrParameters
