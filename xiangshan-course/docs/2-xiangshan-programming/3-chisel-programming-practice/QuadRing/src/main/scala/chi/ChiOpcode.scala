package chi

import chisel3._

/** Supported encodings from Arm IHI 0050B, section 12.9.15. */
object ChiOpcode {
  object Req {
    def ReadShared = 0x01.U(6.W)
    def ReadNoSnp = 0x04.U(6.W)
    def ReadUnique = 0x07.U(6.W)
    def Evict = 0x0d.U(6.W)
    def WriteBackFull = 0x1b.U(6.W)
    def WriteNoSnpFull = 0x1d.U(6.W)
  }

  object Rsp {
    def SnpResp = 0x1.U(4.W)
    def CompAck = 0x2.U(4.W)
    def RetryAck = 0x3.U(4.W)
    def Comp = 0x4.U(4.W)
    def CompDBIDResp = 0x5.U(4.W)
    def DBIDResp = 0x6.U(4.W)
    def PCrdGrant = 0x7.U(4.W)
  }

  object Snp {
    def SnpShared = 0x01.U(5.W)
    def SnpUnique = 0x07.U(5.W)
  }

  object Dat {
    def SnpRespData = 0x1.U(3.W)
    def CopyBackWrData = 0x2.U(3.W)
    def NonCopyBackWrData = 0x3.U(3.W)
    def CompData = 0x4.U(3.W)
  }
}

/** Wire Resp encodings (section 12.9.36), separate from internal cache states. */
object ChiResp {
  def I = 0x0.U(3.W)
  def SC = 0x1.U(3.W)
  def UC = 0x2.U(3.W)
  def I_PD = 0x4.U(3.W)
  def SC_PD = 0x5.U(3.W)
  def UD_PD = 0x6.U(3.W)
}
