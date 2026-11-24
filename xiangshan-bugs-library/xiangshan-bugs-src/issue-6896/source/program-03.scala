package xiangshan.cache
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import xiangshan._

// Real DCache LazyModule (DefaultConfig: numMemChannels=2, channelSelByAddr=true, enMissTrack=true)
// -> real CustomL1L2Fabric (constructed exactly as in L2Top.scala:79-84)
// -> 4 TL manager ports with the address sets BankBinder(4, 64) produces for 4 L2 slices.
// A third "icache" Get client stands in for the other fabric clients (L1I/PTW).
class CmoLM(implicit p: Parameters) extends LazyModule with HasDCacheParameters {
  val dcache = LazyModule(new DCache())
  val fabric = LazyModule(new CustomL1L2Fabric(
    specChannels = numMemChannels, specOwners = LoadPipelineWidth,
    specIdBits = log2Up(cfg.nMissEntries), specBlockBytes = cfg.blockBytes))
  val other = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(name = "icache", sourceId = IdRange(0, 4))))))
  dcache.clientNodes.foreach(c => fabric.node := c)
  fabric.node := other
  fabric.specSink.get := dcache.specFabricSource.get
  val ctrlClient = dcache.cacheCtrlOpt.map { u =>
    val c = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(name = "ctrl")))))
    u.node := TLFragmenter(8, 64) := TLWidthWidget(32) := c; c }
  val nBanks = 4
  val slices = Seq.tabulate(nBanks) { i =>
    val addr = AddressSet(0x80000000L, 0x7fffffffL).intersect(AddressSet(i * 64, ~BigInt((nBanks - 1) * 64))).toSeq
    TLManagerNode(Seq(TLSlavePortParameters.v1(Seq(TLSlaveParameters.v1(
      address = addr, regionType = RegionType.CACHED, executable = true,
      supportsAcquireT = TransferSizes(1, 64), supportsAcquireB = TransferSizes(1, 64),
      supportsGet = TransferSizes(1, 64), supportsPutFull = TransferSizes(1, 64), supportsPutPartial = TransferSizes(1, 64),
      fifoId = None)), beatBytes = 32, endSinkId = 16)))
  }
  slices.foreach(_ := fabric.node)

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val cmoValid = Input(Bool()); val cmoReady = Output(Bool())
      val cmoOp = Input(UInt(3.W)); val cmoAddr = Input(UInt(64.W))
      val cmoRespValid = Output(Bool()); val cmoRespAddr = Output(UInt(64.W))
      val sliceReady = Input(Vec(nBanks, Bool()))
      val sliceAValid = Output(Vec(nBanks, Bool())); val sliceAOp = Output(Vec(nBanks, UInt(3.W)))
      val sliceAAddr = Output(Vec(nBanks, UInt(48.W))); val sliceASrc = Output(Vec(nBanks, UInt(16.W)))
      val dValid = Input(Vec(nBanks, Bool())); val dSrc = Input(Vec(nBanks, UInt(16.W)))
      val otherValid = Input(Bool()); val otherReady = Output(Bool()); val otherAddr = Input(UInt(48.W)); val otherSrc = Input(UInt(2.W))
      val dcAValid = Output(Vec(numMemChannels, Bool())); val dcAReady = Output(Vec(numMemChannels, Bool()))
      val dcAAddr = Output(Vec(numMemChannels, UInt(48.W)))
    })
    def tieIn(x: Data): Unit = x match {
      case r: Record => r.elements.foreach { case (_, e) => tieIn(e) }
      case v: Vec[_] => v.foreach(e => tieIn(e))
      case e => if (chisel3.reflect.DataMirror.directionOf(e) == ActualDirection.Input) e := 0.U.asTypeOf(e)
    }
    val dio = dcache.module.io
    chisel3.reflect.DataMirror.modulePorts(dcache.module).foreach { case (n, x) => if (n == "io") tieIn(x) }
    dio.cmoOpReq.valid := io.cmoValid; dio.cmoOpReq.bits.opcode := io.cmoOp; dio.cmoOpReq.bits.address := io.cmoAddr
    io.cmoReady := dio.cmoOpReq.ready
    dio.cmoOpResp.ready := true.B
    io.cmoRespValid := dio.cmoOpResp.valid; io.cmoRespAddr := dio.cmoOpResp.bits.address
    for (i <- 0 until numMemChannels) {
      val b = fabric.node.in(i)._1
      io.dcAValid(i) := chisel3.util.experimental.BoringUtils.tapAndRead(b.a.valid)
      io.dcAReady(i) := chisel3.util.experimental.BoringUtils.tapAndRead(b.a.ready)
      io.dcAAddr(i) := chisel3.util.experimental.BoringUtils.tapAndRead(b.a.bits.address)
    }
    val (ob, oe) = other.out.head
    ob.a.valid := io.otherValid; io.otherReady := ob.a.ready
    ob.a.bits := oe.Get(io.otherSrc, io.otherAddr, 6.U)._2
    ob.d.ready := true.B; ob.b.ready := true.B; ob.c.valid := false.B; ob.c.bits := DontCare; ob.e.valid := false.B; ob.e.bits := DontCare
    ctrlClient.foreach { c => val (cb, _) = c.out.head
      cb.a.valid := false.B; cb.a.bits := DontCare; cb.d.ready := true.B
      cb.b.ready := true.B; cb.c.valid := false.B; cb.c.bits := DontCare; cb.e.valid := false.B; cb.e.bits := DontCare }
    slices.zipWithIndex.foreach { case (s, i) =>
      val (sb, se) = s.in.head
      sb.a.ready := io.sliceReady(i)
      io.sliceAValid(i) := sb.a.valid; io.sliceAOp(i) := sb.a.bits.opcode
      io.sliceAAddr(i) := sb.a.bits.address; io.sliceASrc(i) := sb.a.bits.source
      sb.d.valid := io.dValid(i)
      sb.d.bits := 0.U.asTypeOf(sb.d.bits)
      sb.d.bits.opcode := TLMessages.CBOAck
      sb.d.bits.size := 6.U
      sb.d.bits.source := io.dSrc(i)
      sb.b.valid := false.B; sb.b.bits := DontCare
      sb.c.ready := true.B; sb.e.ready := true.B
    }
  }
}
object CmoMain extends App {
  implicit val p: Parameters = audit.AuditCfg()
  val lm = LazyModule(new CmoLM)
  print(_root_.circt.stage.ChiselStage.emitSystemVerilog(lm.module, firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")))
}
