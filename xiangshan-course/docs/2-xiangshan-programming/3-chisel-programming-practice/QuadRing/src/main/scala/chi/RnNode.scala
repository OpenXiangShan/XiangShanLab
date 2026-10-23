package chi

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.nodes._

case class RnParams(nodeId: Int = 0, hnId: Int = 1, coherent: Boolean = false,
  cacheLines: Int = 8) {
  require(nodeId >= 0 && hnId >= 0, "RN and HN node IDs must be nonnegative")
  require(cacheLines >= 2 && (cacheLines & (cacheLines - 1)) == 0,
    "RN cacheLines must be a power of two greater than one")
}

/** One local command at a time; coherent mode adds a small direct-mapped line cache. */
class RnNode(val params: RnParams)(implicit p: Parameters) extends LazyModule {
  val node = new SourceNode(ChiNodeImp)(Seq(ChiEndpointParams(params.nodeId, ChiRole.RN)))
  lazy val module = new RnNodeModule(this)
}

class RnNodeModule(outer: RnNode) extends LazyModuleImp(outer) {
  private val (port, edge) = outer.node.out.head
  private val params = edge.network.chi
  require(BigInt(outer.params.hnId) < (BigInt(1) << params.nodeIdWidth),
    "HN target does not fit the negotiated node ID width")
  val io = IO(new RnPort(params))

  private val cacheIndexWidth = if (outer.params.coherent) log2Ceil(outer.params.cacheLines) else 1
  private val cacheTagWidth = if (outer.params.coherent) {
    params.addrWidth - 6 - cacheIndexWidth
  } else 1
  if (outer.params.coherent) {
    require(cacheTagWidth > 0, "RN cache must leave at least one tag bit")
  }

  val (idle :: sendReq :: waitData :: waitDbId :: waitComp :: sendWriteData :: sendCompAck ::
    returnResult :: Nil) = Enum(8)
  val state = RegInit(idle)
  private val request = Reg(new RnRequest(params))
  private val result = Reg(new RnResponse(params))
  private val requestOpcode = RegInit(ChiOpcode.Req.ReadNoSnp)
  private val victimAddress = Reg(UInt(params.addrWidth.W))
  private val victimData = Reg(UInt(512.W))
  private val victimDirty = RegInit(false.B)
  private val writeDbId = Reg(UInt(params.txnIdWidth.W))
  private val completionDbId = Reg(UInt(params.txnIdWidth.W))
  private val cacheValid = RegInit(VecInit(Seq.fill(outer.params.cacheLines)(false.B)))
  private val cacheTags = Reg(Vec(outer.params.cacheLines, UInt(cacheTagWidth.W)))
  private val cacheStates = RegInit(VecInit(Seq.fill(outer.params.cacheLines)(RnCacheState.I)))
  private val cacheData = Reg(Vec(outer.params.cacheLines, UInt(512.W)))
  private val retryAckSeen = RegInit(false.B)
  private val creditHeld = RegInit(false.B)
  private val retried = RegInit(false.B)
  private val waitingForReply = state === waitData || state === waitDbId || state === waitComp
  private val retryReady = retryAckSeen && creditHeld
  private val mainDatTx = Wire(Decoupled(new DatFlit(params)))
  private val mainRspTx = Wire(Decoupled(new RspFlit(params)))

  private val localCacheIndex: UInt = if (outer.params.coherent) {
    io.request.bits.addr(6 + cacheIndexWidth - 1, 6)
  } else 0.U(cacheIndexWidth.W)
  private val localCacheTag: UInt = if (outer.params.coherent) {
    io.request.bits.addr(params.addrWidth - 1, 6 + cacheIndexWidth)
  } else 0.U(cacheTagWidth.W)
  private val localCacheHit = cacheValid(localCacheIndex) &&
    cacheTags(localCacheIndex) === localCacheTag && cacheStates(localCacheIndex) =/= RnCacheState.I

  private val requestCacheIndex: UInt = if (outer.params.coherent) {
    request.addr(6 + cacheIndexWidth - 1, 6)
  } else 0.U(cacheIndexWidth.W)
  private val requestCacheTag: UInt = if (outer.params.coherent) {
    request.addr(params.addrWidth - 1, 6 + cacheIndexWidth)
  } else 0.U(cacheTagWidth.W)

  // Replacement and the original command are separate requests with independent retry credit.
  private def resumeRequest(): Unit = {
    requestOpcode := Mux(request.write, ChiOpcode.Req.ReadUnique, ChiOpcode.Req.ReadShared)
    retryAckSeen := false.B
    creditHeld := false.B
    retried := false.B
    state := sendReq
  }

  io.request.ready := state === idle && (if (outer.params.coherent) !port.snpRx.valid else true.B)
  when(io.request.fire) {
    assert(io.request.bits.addr(5, 0) === 0.U, "RN requires line-aligned addresses")
    if (outer.params.coherent) {
      when(!io.request.bits.write && localCacheHit) {
        result.txnID := io.request.bits.txnID
        result.data := cacheData(localCacheIndex)
        state := returnResult
      }.elsewhen(io.request.bits.write && localCacheHit &&
        (cacheStates(localCacheIndex) === RnCacheState.UC ||
          cacheStates(localCacheIndex) === RnCacheState.UD)) {
        cacheData(localCacheIndex) := io.request.bits.data
        cacheStates(localCacheIndex) := RnCacheState.UD
        result.txnID := io.request.bits.txnID
        result.data := 0.U
        state := returnResult
      }.otherwise {
        val replace = cacheValid(localCacheIndex) &&
          cacheStates(localCacheIndex) =/= RnCacheState.I && !localCacheHit
        request := io.request.bits
        requestOpcode := Mux(io.request.bits.write, ChiOpcode.Req.ReadUnique,
          ChiOpcode.Req.ReadShared)
        when(replace) {
          victimAddress := Cat(cacheTags(localCacheIndex), localCacheIndex, 0.U(6.W))
          victimData := cacheData(localCacheIndex)
          victimDirty := cacheStates(localCacheIndex) === RnCacheState.UD
          requestOpcode := Mux(cacheStates(localCacheIndex) === RnCacheState.UD,
            ChiOpcode.Req.WriteBackFull, ChiOpcode.Req.Evict)
          // Move the victim out of the cache; retain dirty data through retries and DAT backpressure.
          cacheValid(localCacheIndex) := false.B
          cacheStates(localCacheIndex) := RnCacheState.I
        }
        retryAckSeen := false.B
        creditHeld := false.B
        retried := false.B
        state := sendReq
      }
    } else {
      request := io.request.bits
      requestOpcode := Mux(io.request.bits.write, ChiOpcode.Req.WriteNoSnpFull,
        ChiOpcode.Req.ReadNoSnp)
      retryAckSeen := false.B
      creditHeld := false.B
      retried := false.B
      state := sendReq
    }
  }

  port.reqTx.valid := state === sendReq
  port.reqTx.bits := 0.U.asTypeOf(port.reqTx.bits)
  port.reqTx.bits.tgtID := outer.params.hnId.U
  port.reqTx.bits.srcID := outer.params.nodeId.U
  port.reqTx.bits.txnID := request.txnID
  port.reqTx.bits.opcode := requestOpcode
  port.reqTx.bits.addr := request.addr
  if (outer.params.coherent) {
    port.reqTx.bits.addr := Mux(requestOpcode === ChiOpcode.Req.Evict ||
      requestOpcode === ChiOpcode.Req.WriteBackFull, victimAddress, request.addr)
  }
  port.reqTx.bits.allowRetry := !retryReady
  when(port.reqTx.fire) {
    when(retryReady) {
      retryAckSeen := false.B
      creditHeld := false.B
      retried := true.B
    }
    if (outer.params.coherent) {
      state := Mux(requestOpcode === ChiOpcode.Req.Evict, waitComp,
        Mux(requestOpcode === ChiOpcode.Req.WriteBackFull, waitDbId, waitData))
    } else {
      state := Mux(request.write, waitDbId, waitData)
    }
  }

  // RSP takes priority if data and a retry response arrive together.
  port.datRx.ready := state === waitData && !retryAckSeen && !creditHeld && !port.rspRx.valid &&
    (if (outer.params.coherent) !port.snpRx.valid else true.B)
  when(port.datRx.fire) {
    val dat = port.datRx.bits
    if (outer.params.coherent) {
      assert(dat.tgtID === outer.params.nodeId.U && dat.srcID === outer.params.hnId.U &&
        dat.txnID === request.txnID, "RN received data for another transaction")
      assert(dat.opcode === ChiOpcode.Dat.CompData &&
        (dat.resp === ChiResp.SC || dat.resp === ChiResp.UC || dat.resp === ChiResp.UD_PD),
        "RN expects coherent CompData")
      assert(!request.write || dat.resp === ChiResp.UC || dat.resp === ChiResp.UD_PD,
        "RN ReadUnique requires unique permission")
      completionDbId := dat.dbID
      cacheValid(requestCacheIndex) := true.B
      cacheTags(requestCacheIndex) := requestCacheTag
      when(request.write) {
        cacheData(requestCacheIndex) := request.data
        cacheStates(requestCacheIndex) := RnCacheState.UD
        result.data := 0.U
      }.otherwise {
        cacheData(requestCacheIndex) := dat.data
        cacheStates(requestCacheIndex) := Mux(dat.resp === ChiResp.UD_PD, RnCacheState.UD,
          Mux(dat.resp === ChiResp.SC, RnCacheState.SC, RnCacheState.UC))
        result.data := dat.data
      }
      result.txnID := request.txnID
      state := sendCompAck
    } else {
      assert(dat.tgtID === outer.params.nodeId.U && dat.srcID === outer.params.hnId.U &&
        dat.txnID === request.txnID, "RN received data for another transaction")
      assert(dat.opcode === ChiOpcode.Dat.CompData && dat.resp === ChiResp.I && dat.dbID === 0.U,
        "RN expects non-coherent CompData")
      result.txnID := request.txnID
      result.data := dat.data
      state := returnResult
    }
  }

  port.rspRx.ready := waitingForReply && !retryReady
  when(port.rspRx.fire) {
    val rsp = port.rspRx.bits
    assert(rsp.tgtID === outer.params.nodeId.U && rsp.srcID === outer.params.hnId.U,
      "RN received a response for another transaction")
    when(rsp.opcode === ChiOpcode.Rsp.RetryAck || rsp.opcode === ChiOpcode.Rsp.PCrdGrant) {
      assert(!retried, "RN cannot retry an already credited request")
      assert(rsp.resp === ChiResp.I && rsp.pCrdType === 0.U, "RN expects retry credit type zero")
      when(rsp.opcode === ChiOpcode.Rsp.RetryAck) {
        assert(rsp.txnID === request.txnID, "RN received RetryAck for another transaction")
        assert(rsp.dbID === 0.U, "RN expects zero RetryAck DBID")
        assert(!retryAckSeen, "RN received duplicate RetryAck")
        retryAckSeen := true.B
      }.otherwise {
        assert(rsp.txnID === 0.U && rsp.dbID === 0.U, "RN expects zero PCrdGrant IDs")
        assert(!creditHeld, "RN received duplicate PCrdGrant")
        creditHeld := true.B
      }
    }.otherwise {
      if (outer.params.coherent) {
        assert(rsp.txnID === request.txnID, "RN received a response for another transaction")
        when(state === waitDbId) {
          assert(!retryAckSeen && !creditHeld, "RN cannot complete a request awaiting retry")
          assert(rsp.opcode === ChiOpcode.Rsp.CompDBIDResp && rsp.resp === ChiResp.I &&
            rsp.pCrdType === 0.U, "RN expects WriteBackFull CompDBIDResp")
          writeDbId := rsp.dbID
          state := sendWriteData
        }.otherwise {
          assert(state === waitComp && !retryAckSeen && !creditHeld,
            "RN expects Evict completion without a pending retry")
          assert(rsp.opcode === ChiOpcode.Rsp.Comp && rsp.resp === ChiResp.I &&
            rsp.dbID === 0.U && rsp.pCrdType === 0.U, "RN expects Evict Comp with zero response fields")
          resumeRequest()
        }
      } else {
      assert(rsp.txnID === request.txnID, "RN received a response for another transaction")
      assert(state === waitDbId && !retryAckSeen && !creditHeld,
        "RN cannot complete a request awaiting retry")
      assert(rsp.opcode === ChiOpcode.Rsp.CompDBIDResp && rsp.resp === ChiResp.I &&
        rsp.pCrdType === 0.U, "RN expects non-coherent CompDBIDResp")
      writeDbId := rsp.dbID
      state := sendWriteData
      }
    }
  }
  when(waitingForReply && retryReady) { state := sendReq }

  mainDatTx.valid := state === sendWriteData
  mainDatTx.bits := 0.U.asTypeOf(mainDatTx.bits)
  mainDatTx.bits.tgtID := outer.params.hnId.U
  mainDatTx.bits.srcID := outer.params.nodeId.U
  mainDatTx.bits.txnID := writeDbId
  mainDatTx.bits.opcode := ChiOpcode.Dat.NonCopyBackWrData
  mainDatTx.bits.resp := ChiResp.I
  mainDatTx.bits.data := request.data
  if (outer.params.coherent) {
    mainDatTx.bits.opcode := ChiOpcode.Dat.CopyBackWrData
    mainDatTx.bits.resp := Mux(victimDirty, ChiResp.UD_PD, ChiResp.I)
    mainDatTx.bits.data := Mux(victimDirty, victimData, 0.U)
  }
  when(mainDatTx.fire) {
    if (outer.params.coherent) {
      victimDirty := false.B
      resumeRequest()
    } else {
      result.txnID := request.txnID
      result.data := 0.U
      state := returnResult
    }
  }

  mainRspTx.valid := (if (outer.params.coherent) state === sendCompAck else false.B)
  mainRspTx.bits := 0.U.asTypeOf(mainRspTx.bits)
  mainRspTx.bits.tgtID := outer.params.hnId.U
  mainRspTx.bits.srcID := outer.params.nodeId.U
  mainRspTx.bits.txnID := completionDbId
  mainRspTx.bits.opcode := ChiOpcode.Rsp.CompAck
  mainRspTx.bits.resp := ChiResp.I
  when(mainRspTx.fire) {
    state := returnResult
  }

  io.response.valid := state === returnResult
  io.response.bits := result
  when(io.response.fire) { state := idle }

  if (outer.params.coherent) {
    val snoopPending = RegInit(false.B)
    val snoopHasData = Reg(Bool())
    val snoopTxnId = Reg(UInt(params.txnIdWidth.W))
    val snoopResp = Reg(UInt(3.W))
    val snoopData = Reg(UInt(512.W))
    val snp = port.snpRx.bits
    val index = snp.addr(6 + cacheIndexWidth - 1, 6)
    val tag = snp.addr(params.addrWidth - 1, 6 + cacheIndexWidth)
    val cacheHit = cacheValid(index) && cacheStates(index) =/= RnCacheState.I && cacheTags(index) === tag
    val victimHit = victimDirty && requestOpcode === ChiOpcode.Req.WriteBackFull &&
      victimAddress === snp.addr

    port.snpRx.ready := !snoopPending
    when(port.snpRx.fire) {
      assert(snp.srcID === outer.params.hnId.U, "RN expects snoops from its HN")
      assert(snp.addr(5, 0) === 0.U, "RN requires line-aligned snoops")
      assert(snp.opcode === ChiOpcode.Snp.SnpShared || snp.opcode === ChiOpcode.Snp.SnpUnique,
        "RN supports only SnpShared and SnpUnique")
      assert(!victimHit || state =/= sendWriteData, "RN cannot snoop an authorized writeback")
      val unique = snp.opcode === ChiOpcode.Snp.SnpUnique
      val dirty = cacheHit && cacheStates(index) === RnCacheState.UD
      snoopPending := true.B
      snoopTxnId := snp.txnID
      snoopHasData := victimHit || dirty
      snoopData := Mux(victimHit, victimData, cacheData(index))
      snoopResp := Mux(victimHit, ChiResp.I_PD,
        Mux(dirty, Mux(unique, ChiResp.I_PD, ChiResp.SC_PD),
          Mux(cacheHit && !unique, ChiResp.SC, ChiResp.I)))
      when(victimHit) { victimDirty := false.B }
      when(cacheHit) {
        cacheValid(index) := !unique
        cacheStates(index) := Mux(unique, RnCacheState.I, RnCacheState.SC)
      }
    }

    val snoopRspTx = Wire(Decoupled(new RspFlit(params)))
    snoopRspTx.valid := snoopPending && !snoopHasData
    snoopRspTx.bits := 0.U.asTypeOf(snoopRspTx.bits)
    snoopRspTx.bits.tgtID := outer.params.hnId.U
    snoopRspTx.bits.srcID := outer.params.nodeId.U
    snoopRspTx.bits.txnID := snoopTxnId
    snoopRspTx.bits.opcode := ChiOpcode.Rsp.SnpResp
    snoopRspTx.bits.resp := snoopResp

    val snoopDatTx = Wire(Decoupled(new DatFlit(params)))
    snoopDatTx.valid := snoopPending && snoopHasData
    snoopDatTx.bits := 0.U.asTypeOf(snoopDatTx.bits)
    snoopDatTx.bits.tgtID := outer.params.hnId.U
    snoopDatTx.bits.srcID := outer.params.nodeId.U
    snoopDatTx.bits.txnID := snoopTxnId
    snoopDatTx.bits.opcode := ChiOpcode.Dat.SnpRespData
    snoopDatTx.bits.resp := snoopResp
    snoopDatTx.bits.data := snoopData
    when(snoopRspTx.fire || snoopDatTx.fire) { snoopPending := false.B }

    // Prefer snoop replies, but never replace a message already presented under backpressure.
    def arbitrate[T <: Data](main: DecoupledIO[T], snoop: DecoupledIO[T], out: DecoupledIO[T]): Unit = {
      val locked = RegInit(false.B)
      val heldSnoop = Reg(Bool())
      val chooseSnoop = Mux(locked, heldSnoop, snoop.valid)
      out.valid := Mux(chooseSnoop, snoop.valid, main.valid)
      out.bits := Mux(chooseSnoop, snoop.bits, main.bits)
      main.ready := out.ready && !chooseSnoop
      snoop.ready := out.ready && chooseSnoop
      when(out.valid) {
        locked := !out.ready
        heldSnoop := chooseSnoop
      }
    }
    arbitrate(mainRspTx, snoopRspTx, port.rspTx)
    arbitrate(mainDatTx, snoopDatTx, port.datTx)
  } else {
    port.rspTx <> mainRspTx
    port.datTx <> mainDatTx
    port.snpRx.ready := false.B
  }

  port.snpTx.valid := false.B
  port.snpTx.bits := 0.U.asTypeOf(port.snpTx.bits)
  port.reqRx.ready := false.B
}
