// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/cache/mmu/MptChecker.scala#L245-L264
class MptData(implicit p: Parameters) extends XSBundle with MPTCacheParam {
  val data = UInt(perms16Len.W)
  def apply(data: UInt): Unit = this.data := data // zero extended
  def getPPN: UInt = this.data(ppnLen - 1, 0)
  def getAddr(offset: UInt): UInt = Cat(this.getPPN, Cat(offset, 0.U(3.W))) // 2|3 byte = 64bit
  def extractPerm(select: UInt): (UInt) = (this.data >> (select * 3.U))(2, 0) // extract XWR using 4bit offset
  // cal start end and extract
}

class MptEntry(implicit p: Parameters) extends XSBundle with MPTCacheParam {
  val N    = Bool()
  val data = new MptData()
  val L    = Bool()
  val V    = Bool()
  def apply(sMEMResp: UInt): Unit = {
    this.V := (sMEMResp(0) === 1.U)
    this.L := (sMEMResp(1) === 1.U)
    this.N := (sMEMResp(63) === 1.U)
    this.data.apply(sMEMResp(57, 10)) // xiangshan only support 48bit PA, so PPN only needs 36
  }
