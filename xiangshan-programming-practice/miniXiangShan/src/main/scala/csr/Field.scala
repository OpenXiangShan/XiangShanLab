package minixiangshan.csr

import chisel3._

abstract class Field(val hsb: Int, val lsb: Int) extends Bundle {
  require(hsb >= lsb, s"hsb ($hsb) must >= lsb ($lsb)")
  
  def len = hsb - lsb + 1
  val bits = UInt(len.W)

  // 提取32-bit中的对应字段赋值
  def bind(u: UInt): Unit = {
    bits := u(hsb, lsb)
  }

  // 使用'field := '替代  'field.bits :='
  // 依然可以使用field.bits := u
  // 没人想用 field.bits
  def := (u: UInt): Unit = { bits := u }

  // def apply(): UInt = bits
  def apply(i: Int): Bool = bits(i)
  def apply(h: Int, l: Int): UInt = bits(h, l)
}

object Field {
  /* 隐式类型转换
   * 有时需要显示指定类型, 以保证类型转换触发
   */
  import scala.language.implicitConversions
  implicit def fieldToUInt(f: Field): UInt = f.bits
}
