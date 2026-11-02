package chi

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.nodes._
import org.scalatest.matchers.should.Matchers

/** Direct diplomatic peers let endpoint tests block each send independently of the ring. */
private[chi] object ChiNodeFixtures {
  class Rn(val config: ChiNoCParams, val params: RnParams)(implicit p: Parameters) extends LazyModule {
    val rn = LazyModule(new RnNode(params))
    val peer = new SinkNode(ChiNodeImp)(Seq(config))
    peer := rn.node
    lazy val module = new RnModule(this)
  }
  class RnModule(outer: Rn) extends LazyModuleImp(outer) {
    val io = IO(new Bundle {
      val local = new RnPort(outer.config.chi)
      val network = new ChiPort(outer.config.chi)
    })
    io.local <> outer.rn.module.io
    outer.peer.in.head._1 <> io.network
  }

  /** Real coherent RN; the HN port is driven externally to exercise snoops over the rings. */
  class RnOnNoC(val config: ChiNoCParams, val ids: Seq[Int])(implicit p: Parameters) extends LazyModule {
    require(config.ring.nodeCount == 4 && ids.size == 4, "RN fixture requires four ring stops")
    val network = LazyModule(new ChiNoC(config))
    val rn = LazyModule(new RnNode(RnParams(ids.head, ids(1), coherent = true, cacheLines = 2)))
    val drivers = Seq(ChiRole.HN, ChiRole.RN, ChiRole.SN).zip(ids.tail).map { case (role, id) =>
      new SourceNode(ChiNodeImp)(Seq(ChiEndpointParams(id, role)))
    }
    network.node := rn.node
    drivers.foreach { driver => network.node := driver }
    lazy val module = new RnOnNoCModule(this)
  }
  class RnOnNoCModule(outer: RnOnNoC) extends LazyModuleImp(outer) {
    val io = IO(new Bundle {
      val local = new RnPort(outer.config.chi)
      val hn = Flipped(new ChiPort(outer.config.chi))
    })
    io.local <> outer.rn.module.io
    outer.drivers.head.out.head._1 <> io.hn
    for (driver <- outer.drivers.tail) {
      val port = driver.out.head._1
      Seq(port.reqTx, port.rspTx, port.snpTx, port.datTx).foreach { tx =>
        tx.valid := false.B
        tx.bits := 0.U.asTypeOf(tx.bits)
      }
      Seq(port.reqRx, port.rspRx, port.snpRx, port.datRx).foreach(_.ready := true.B)
    }
  }

  class Hn(val config: ChiNoCParams, val params: HnParams)(implicit p: Parameters) extends LazyModule {
    val hn = LazyModule(new HnNode(params))
    val peer = new SinkNode(ChiNodeImp)(Seq(config))
    peer := hn.node
    lazy val module = new HnModule(this)
  }
  class HnModule(outer: Hn) extends LazyModuleImp(outer) {
    val io = IO(new ChiPort(outer.config.chi))
    outer.peer.in.head._1 <> io
  }

  class Sn(val config: ChiNoCParams, val params: SnParams)(implicit p: Parameters) extends LazyModule {
    val sn = LazyModule(new SnNode(params))
    val peer = new SinkNode(ChiNodeImp)(Seq(config))
    peer := sn.node
    lazy val module = new SnModule(this)
  }
  class SnModule(outer: Sn) extends LazyModuleImp(outer) {
    val io = IO(new ChiPort(outer.config.chi))
    outer.peer.in.head._1 <> io
  }

  /** SN transport fixture with an externally driven HN port. */
  class SnOnNoC(val config: ChiNoCParams, val ids: Seq[Int], val initialLines: Seq[BigInt])
    (implicit p: Parameters) extends LazyModule {
    require(config.ring.nodeCount == 4 && ids.size == 4, "SN fixture requires four ring stops")
    val network = LazyModule(new ChiNoC(config))
    val sn = LazyModule(new SnNode(SnParams(ids(3), initialLines)))
    val drivers = Seq(ChiRole.RN, ChiRole.HN, ChiRole.RN).zip(ids).map { case (role, id) =>
      new SourceNode(ChiNodeImp)(Seq(ChiEndpointParams(id, role)))
    }
    drivers.foreach { driver => network.node := driver }
    network.node := sn.node
    lazy val module = new SnOnNoCModule(this)
  }
  class SnOnNoCModule(outer: SnOnNoC) extends LazyModuleImp(outer) {
    val io = IO(Flipped(new ChiPort(outer.config.chi)))
    outer.drivers(1).out.head._1 <> io
    for (driver <- Seq(outer.drivers.head, outer.drivers(2))) {
      val port = driver.out.head._1
      Seq(port.reqTx, port.rspTx, port.snpTx, port.datTx).foreach { tx =>
        tx.valid := false.B
        tx.bits := 0.U.asTypeOf(tx.bits)
      }
      Seq(port.reqRx, port.rspRx, port.snpRx, port.datRx).foreach(_.ready := true.B)
    }
  }

  /** Real HN and SN; the two RN ports supply flits from the testbench. */
  class HnSnOnNoC(val config: ChiNoCParams, val ids: Seq[Int], val initialLines: Seq[BigInt],
    coherent: Boolean = false, retrySlots: Int = 2)
    (implicit p: Parameters) extends LazyModule {
    require(config.ring.nodeCount == 4 && ids.size == 4, "HN/SN fixture requires four ring stops")
    val network = LazyModule(new ChiNoC(config))
    val hn = LazyModule(new HnNode(HnParams(ids(1), ids(3), coherent = coherent, retrySlots = retrySlots)))
    val sn = LazyModule(new SnNode(SnParams(ids(3), initialLines)))
    val drivers = Seq(ids.head, ids(2)).map { id =>
      new SourceNode(ChiNodeImp)(Seq(ChiEndpointParams(id, ChiRole.RN)))
    }
    network.node := drivers.head
    network.node := hn.node
    network.node := drivers(1)
    network.node := sn.node
    lazy val module = new HnSnOnNoCModule(this)
  }
  class HnSnOnNoCModule(outer: HnSnOnNoC) extends LazyModuleImp(outer) {
    val io = IO(Vec(2, Flipped(new ChiPort(outer.config.chi))))
    outer.drivers.zip(io).foreach { case (driver, port) => driver.out.head._1 <> port }
  }

  /** Test-only handshake outputs, created inside the HN's module boundary. */
  class ObservedHn(params: HnParams)(implicit p: Parameters) extends HnNode(params) {
    val transfers = InModuleBody {
      val io = IO(new Bundle {
        val cancelledWriteback = Output(Bool())
        val memoryWrite = Output(Bool())
        val memoryRead = Output(Bool())
      })
      val port = node.out.head._1
      io.cancelledWriteback := port.datRx.fire &&
        port.datRx.bits.opcode === ChiOpcode.Dat.CopyBackWrData && port.datRx.bits.resp === ChiResp.I
      io.memoryWrite := port.reqTx.fire && port.reqTx.bits.opcode === ChiOpcode.Req.WriteNoSnpFull
      io.memoryRead := port.reqTx.fire && port.reqTx.bits.opcode === ChiOpcode.Req.ReadNoSnp
      io
    }
  }

  /** Coherent integration fixture with local RN interfaces and test-only transfer observations. */
  class CoherentSystem(val config: ChiNoCParams, val ids: Seq[Int], val initialLines: Seq[BigInt],
    cacheLines: Int)(implicit p: Parameters) extends LazyModule {
    require(config.ring.nodeCount == 4 && ids.size == 4, "coherent fixture requires four ring stops")
    val network = LazyModule(new ChiNoC(config))
    val rn0 = LazyModule(new RnNode(RnParams(ids.head, ids(1), coherent = true, cacheLines = cacheLines)))
    val hn = LazyModule(new ObservedHn(HnParams(ids(1), ids(3), coherent = true,
      directoryLines = initialLines.size)))
    val rn1 = LazyModule(new RnNode(RnParams(ids(2), ids(1), coherent = true, cacheLines = cacheLines)))
    val sn = LazyModule(new SnNode(SnParams(ids(3), initialLines)))
    network.node := rn0.node
    network.node := hn.node
    network.node := rn1.node
    network.node := sn.node
    lazy val module = new CoherentSystemModule(this)
  }
  class CoherentSystemModule(outer: CoherentSystem) extends LazyModuleImp(outer) {
    val io = IO(Vec(2, new RnPort(outer.config.chi)))
    io.head <> outer.rn0.module.io
    io(1) <> outer.rn1.module.io
    // Handshake observations for end-to-end loss/duplication and cancellation checks.
    val cancelledWriteback = IO(Output(Bool()))
    val memoryWrite = IO(Output(Bool()))
    val memoryRead = IO(Output(Bool()))
    cancelledWriteback := outer.hn.transfers.cancelledWriteback
    memoryWrite := outer.hn.transfers.memoryWrite
    memoryRead := outer.hn.transfers.memoryRead
  }
}

private[chi] trait ChiNodeTestSupport extends ChiselSim { this: org.scalatest.TestSuite with Matchers =>
  protected val lines: Seq[BigInt] = Seq.tabulate(3)(index =>
    (BigInt(1) << 511) | (BigInt(index + 1) << 256) | BigInt(0x12345678 + index))

  protected def initialize(port: ChiPort): Unit = {
    Seq(port.reqTx, port.rspTx, port.snpTx, port.datTx).foreach(_.ready.poke(true))
    Seq(port.reqRx, port.rspRx, port.snpRx, port.datRx).foreach(_.valid.poke(false))
    for (bits <- Seq(port.reqRx.bits, port.rspRx.bits, port.snpRx.bits, port.datRx.bits);
      field <- bits.elements.values) {
      field match {
        case value: Bool => value.poke(false)
        case value: UInt => value.poke(BigInt(0))
      }
    }
  }

  protected def initialize(port: RnPort): Unit = {
    port.request.valid.poke(false)
    port.request.bits.write.poke(false)
    port.request.bits.addr.poke(BigInt(0))
    port.request.bits.data.poke(BigInt(0))
    port.request.bits.txnID.poke(BigInt(0))
    port.response.ready.poke(false)
  }

  protected def initializeDriver(port: ChiPort): Unit = {
    Seq(port.reqTx, port.rspTx, port.snpTx, port.datTx).foreach(_.valid.poke(false))
    Seq(port.reqRx, port.rspRx, port.snpRx, port.datRx).foreach(_.ready.poke(false))
    for (bits <- Seq(port.reqTx.bits, port.rspTx.bits, port.snpTx.bits.payload, port.datTx.bits);
      field <- bits.elements.values) {
      field match {
        case value: Bool => value.poke(false)
        case value: UInt => value.poke(BigInt(0))
      }
    }
    port.snpTx.bits.dst.poke(BigInt(0))
  }

  protected def localRequest(port: RnPort, txn: Int, address: BigInt): Unit = {
    port.request.bits.write.poke(false)
    port.request.bits.addr.poke(address)
    port.request.bits.txnID.poke(BigInt(txn))
    port.request.bits.data.poke(BigInt(0))
    port.request.valid.poke(true)
  }

  protected def localWriteRequest(port: RnPort, txn: Int, address: BigInt, value: BigInt): Unit = {
    port.request.bits.write.poke(true)
    port.request.bits.addr.poke(address)
    port.request.bits.txnID.poke(BigInt(txn))
    port.request.bits.data.poke(value)
    port.request.valid.poke(true)
  }

  protected def request(req: ReqFlit, target: Int, src: Int, txn: Int, address: BigInt): Unit = {
    req.tgtID.poke(BigInt(target))
    req.srcID.poke(BigInt(src))
    req.txnID.poke(BigInt(txn))
    req.opcode.poke(ChiOpcode.Req.ReadNoSnp)
    req.addr.poke(address)
    req.allowRetry.poke(true)
    req.pCrdType.poke(BigInt(0))
  }

  protected def expectRequest(req: ReqFlit, target: Int, src: Int, txn: Int, address: BigInt,
    opcode: UInt = ChiOpcode.Req.ReadNoSnp, allowRetry: Boolean = true): Unit = {
    req.tgtID.expect(target.U)
    req.srcID.expect(src.U)
    req.txnID.expect(txn.U)
    req.opcode.expect(opcode)
    req.addr.expect(address.U)
    req.allowRetry.expect(allowRetry.B)
    req.pCrdType.expect(0.U)
  }

  protected def writeRequest(req: ReqFlit, target: Int, src: Int, txn: Int, address: BigInt): Unit = {
    request(req, target, src, txn, address)
    req.opcode.poke(ChiOpcode.Req.WriteNoSnpFull)
  }

  protected def response(rsp: RspFlit, target: Int, src: Int, txn: Int, opcode: UInt, dbId: Int = 0): Unit = {
    rsp.tgtID.poke(BigInt(target))
    rsp.srcID.poke(BigInt(src))
    rsp.txnID.poke(BigInt(txn))
    rsp.opcode.poke(opcode)
    rsp.resp.poke(ChiResp.I)
    rsp.dbID.poke(BigInt(dbId))
    rsp.pCrdType.poke(BigInt(0))
  }

  protected def expectResponse(rsp: RspFlit, target: Int, src: Int, txn: Int, opcode: UInt,
    dbId: Int = 0): Unit = {
    rsp.tgtID.expect(target.U)
    rsp.srcID.expect(src.U)
    rsp.txnID.expect(txn.U)
    rsp.opcode.expect(opcode)
    rsp.resp.expect(ChiResp.I)
    rsp.dbID.expect(dbId.U)
    rsp.pCrdType.expect(0.U)
  }

  protected def expectSnoopRequest(port: ChiPort, target: Int, src: Int, address: BigInt,
    opcode: UInt = ChiOpcode.Snp.SnpUnique): Unit = {
    port.snpTx.valid.expect(true.B)
    port.snpTx.bits.dst.expect(target.U)
    port.snpTx.bits.payload.srcID.expect(src.U)
    port.snpTx.bits.payload.txnID.expect(0.U)
    port.snpTx.bits.payload.opcode.expect(opcode)
    port.snpTx.bits.payload.addr.expect(address.U)
    port.reqTx.valid.expect(false.B)
    port.datTx.valid.expect(false.B)
  }

  protected def data(dat: DatFlit, target: Int, src: Int, txn: Int, value: BigInt): Unit = {
    dat.tgtID.poke(BigInt(target))
    dat.srcID.poke(BigInt(src))
    dat.txnID.poke(BigInt(txn))
    dat.opcode.poke(ChiOpcode.Dat.CompData)
    dat.resp.poke(ChiResp.I)
    dat.dbID.poke(BigInt(0))
    dat.data.poke(value)
  }

  protected def expectData(dat: DatFlit, target: Int, src: Int, txn: Int, value: BigInt,
    opcode: UInt = ChiOpcode.Dat.CompData): Unit = {
    dat.tgtID.expect(target.U)
    dat.srcID.expect(src.U)
    dat.txnID.expect(txn.U)
    dat.opcode.expect(opcode)
    dat.resp.expect(ChiResp.I)
    dat.dbID.expect(0.U)
    dat.data.expect(value.U)
  }

  protected def writeData(dat: DatFlit, target: Int, src: Int, dbId: Int, value: BigInt): Unit = {
    data(dat, target, src, dbId, value)
    dat.opcode.poke(ChiOpcode.Dat.NonCopyBackWrData)
  }
}
