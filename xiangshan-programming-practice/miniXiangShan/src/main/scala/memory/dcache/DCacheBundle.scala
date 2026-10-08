package minixiangshan.mem.dcache
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.rename._
 
object MshrReqType {
  val width = 2
  val cacheable    = 0.U(width.W)
  val uncacheRead  = 1.U(width.W)
  val uncacheWrite = 2.U(width.W)
}
 
class DCacheMetaEntry(implicit p: Parameters) extends NSBundle {
  val valid = Bool()
  val dirty = Bool()
  val tag   = UInt(tagBitsD.W)
  def toUInt: UInt = Cat(valid, dirty, tag)
  def fromUInt(value: UInt): DCacheMetaEntry = {
    val result = Wire(new DCacheMetaEntry)
    result.valid := value(tagBitsD + 1)
    result.dirty := value(tagBitsD)
    result.tag   := value(tagBitsD - 1, 0)
    result
  }
  def metaWidth: Int = tagBitsD + 2
}
 
class DCacheArrayReadData(implicit p: Parameters) extends NSBundle {
  val ways = Vec(nWaysD, new Bundle {
    val valid = Bool()
    val dirty = Bool()
    val tag   = UInt(tagBitsD.W)
    val data  = UInt((blockBytes * 8).W)
  })
}