package minixiangshan.csr

import chisel3._
import chisel3.util._
import minixiangshan.config.Parameters

/* ============================================================================
 *  带字段定义的 CSR 基类
 *
 *  fieldLayout 从高到低列出 (位段, 值)，toUInt 依次拼接；
 *  bindFrom 把 32 bit 写数据按字段位置拆开（仅 wen = true 的字段生效）。
 * ==========================================================================*/
class CsrField(h: Int, l: Int, val wen: Boolean = true)
  extends Field(h, l)

abstract class CsrBundle(implicit p: Parameters) extends BundleSkel {
  val writable: Boolean
  protected def fieldLayout: List[((Int, Int), UInt)]

  def toUInt: UInt = Cat(fieldLayout.map(_._2))

  def bindFrom(u: UInt): this.type = {
    if (writable) {
      this.getElements.collect {
        case f: CsrField if f.wen => f.bind(u)
      }
    }
    this
  }
}

object CsrBundles {
  /**
   * mstatus（RV32，含 S 模式）
   *   SIE[1] SPIE[5] SPP[8] MIE[3] MPIE[7] MPP[12:11]
   *   MPRV[17] SUM[18] MXR[19] TVM[20] TW[21] TSR[22]
   * 未实现的字段（FS/XS/SD 等）读恒为 0。
   */
  class MstatusBundle(implicit p: Parameters) extends CsrBundle {
    val writable: Boolean = true
    val sie  = new CsrField(1, 1)
    val mie  = new CsrField(3, 3)
    val spie = new CsrField(5, 5)
    val mpie = new CsrField(7, 7)
    val spp  = new CsrField(8, 8)
    val mpp  = new CsrField(12, 11)
    val mprv = new CsrField(17, 17)
    val sum  = new CsrField(18, 18)
    val mxr  = new CsrField(19, 19)
    val tvm  = new CsrField(20, 20)
    val tw   = new CsrField(21, 21)
    val tsr  = new CsrField(22, 22)

    protected val fieldLayout = List(
      (31, 23) -> 0.U(9.W),
      (22, 22) -> tsr.bits,
      (21, 21) -> tw.bits,
      (20, 20) -> tvm.bits,
      (19, 19) -> mxr.bits,
      (18, 18) -> sum.bits,
      (17, 17) -> mprv.bits,
      (16, 13) -> 0.U(4.W),
      (12, 11) -> mpp.bits,
      (10,  9) -> 0.U(2.W),
      (8,   8) -> spp.bits,
      (7,   7) -> mpie.bits,
      (6,   6) -> 0.U(1.W),
      (5,   5) -> spie.bits,
      (4,   4) -> 0.U(1.W),
      (3,   3) -> mie.bits,
      (2,   2) -> 0.U(1.W),
      (1,   1) -> sie.bits,
      (0,   0) -> 0.U(1.W)
    )
  }

  /** sstatus 是 mstatus 的子集视图 */
  class SstatusBundle(implicit p: Parameters) extends CsrBundle {
    val writable: Boolean = true
    val sie  = new CsrField(1, 1)
    val spie = new CsrField(5, 5)
    val spp  = new CsrField(8, 8)
    val sum  = new CsrField(18, 18)
    val mxr  = new CsrField(19, 19)

    protected val fieldLayout = List(
      (31, 20) -> 0.U(12.W),
      (19, 19) -> mxr.bits,
      (18, 18) -> sum.bits,
      (17,  9) -> 0.U(9.W),
      (8,   8) -> spp.bits,
      (7,   6) -> 0.U(2.W),
      (5,   5) -> spie.bits,
      (4,   2) -> 0.U(3.W),
      (1,   1) -> sie.bits,
      (0,   0) -> 0.U(1.W)
    )
  }
}
