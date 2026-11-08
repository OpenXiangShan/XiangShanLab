package chi

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.nodes._

case class HnParams(nodeId: Int = 1, snId: Int = 3, retrySlots: Int = 2,
  coherent: Boolean = false, directoryLines: Int = 64) {
  require(nodeId >= 0 && snId >= 0, "HN and SN node IDs must be nonnegative")
  require(retrySlots > 0, "HN must have at least one retry slot")
  require(directoryLines >= 2 && (directoryLines & (directoryLines - 1)) == 0,
    "HN directoryLines must be a power of two greater than one")
}

/** One upstream transaction; retain its identity while issuing a downstream request. */
class HnNode(val params: HnParams)(implicit p: Parameters) extends LazyModule {
  val node = new SourceNode(ChiNodeImp)(Seq(ChiEndpointParams(params.nodeId, ChiRole.HN)))
  lazy val module = new HnNodeModule(this)
}

class HnNodeModule(outer: HnNode) extends LazyModuleImp(outer) {
  private val (port, edge) = outer.node.out.head
  private val params = edge.network.chi
  require(BigInt(outer.params.snId) < (BigInt(1) << params.nodeIdWidth),
    "SN target does not fit the negotiated node ID width")

  private val directoryIndexWidth = if (outer.params.coherent) log2Ceil(outer.params.directoryLines) else 1
  if (outer.params.coherent) {
    require(params.addrWidth >= 6 + directoryIndexWidth,
      "HN directory must fit the negotiated address width")
  }

  val (idle :: sendSnReq :: waitSnData :: sendRnData :: sendRnRsp ::
    waitRnData :: waitSnDbId :: sendSnData :: waitSnComp :: sendGrant :: waitRetryReq ::
    waitRnCompAck :: sendSnp :: waitSnpResponse :: Nil) = Enum(14)
  val state = RegInit(idle)
  private val request = Reg(new ReqFlit(params))
  private val data = Reg(UInt(512.W))
  private val completionResp = RegInit(ChiResp.I)
  private val snoopTarget = Reg(UInt(params.nodeIdWidth.W))
  private val snoopHolderSlot = Reg(UInt(1.W))
  private val snoopRequesterSlot = Reg(UInt(1.W))
  private val snoopWrite = RegInit(false.B)
  private val uniqueRead = request.opcode === ChiOpcode.Req.ReadUnique
  private val directoryHolderValid = RegInit(VecInit(Seq.fill(outer.params.directoryLines) {
    VecInit(Seq.fill(2)(false.B))
  }))
  private val directoryHolderIds = Reg(Vec(outer.params.directoryLines,
    Vec(2, UInt(params.nodeIdWidth.W))))
  private val directoryHolderStates = RegInit(VecInit(Seq.fill(outer.params.directoryLines) {
    VecInit(Seq.fill(2)(RnCacheState.I))
  }))
  // One downstream request is active, so its ID can remain zero.
  private val downstreamTxnId = 0.U(params.txnIdWidth.W)
  private val dbId = 0.U(params.txnIdWidth.W)
  private val snoopTxnId = 0.U(params.txnIdWidth.W)
  private val snDbId = Reg(UInt(params.txnIdWidth.W))
  private val retryAckSeen = RegInit(false.B)
  private val creditHeld = RegInit(false.B)
  private val retried = RegInit(false.B)
  private val retryWaiting = RegInit(VecInit(Seq.fill(outer.params.retrySlots)(false.B)))
  private val retryRequests = Reg(Vec(outer.params.retrySlots, new ReqFlit(params)))
  private val retryAckPending = RegInit(false.B)
  private val retryAckTarget = Reg(UInt(params.nodeIdWidth.W))
  private val retryAckTxn = Reg(UInt(params.txnIdWidth.W))
  private val slotWidth = log2Ceil(outer.params.retrySlots max 2)
  private val rrSlot = RegInit(0.U(slotWidth.W))
  private val waitingForSnReply = state === waitSnData || state === waitSnDbId
  private val retryReady = retryAckSeen && creditHeld
  private val hasRetryWaiting = retryWaiting.asUInt.orR
  private val retrySlotFree = retryWaiting.map(slot => !slot).reduce(_ || _)
  private val duplicateRetry = retryWaiting.zip(retryRequests).map { case (valid, saved) =>
    valid && saved.srcID === port.reqRx.bits.srcID
  }.reduce(_ || _)
  private val activeState = state =/= idle && state =/= sendGrant && state =/= waitRetryReq
  private val selectedRetrySlot: UInt = {
    var found: Bool = false.B
    var selected: UInt = 0.U(slotWidth.W)
    for (start <- 0 until outer.params.retrySlots; distance <- 0 until outer.params.retrySlots) {
      val index = (start + distance) % outer.params.retrySlots
      val take = !found && rrSlot === start.U && retryWaiting(index)
      selected = Mux(take, index.U(slotWidth.W), selected)
      found = found || (rrSlot === start.U && retryWaiting(index))
    }
    selected
  }

  // Other RNs may reach the REQ head while the granted requester is still reissuing.
  private val reservedRetry = state === waitRetryReq &&
    port.reqRx.bits.srcID === request.srcID
  private val requestDirectoryIndex: UInt = if (outer.params.coherent) {
    port.reqRx.bits.addr(6 + directoryIndexWidth - 1, 6)
  } else 0.U(directoryIndexWidth.W)
  private val requestInDirectoryRange = (port.reqRx.bits.addr >> 6) < outer.params.directoryLines.U
  private val directoryOccupied = requestInDirectoryRange &&
    directoryHolderValid(requestDirectoryIndex).asUInt.orR
  private val requestHolder0 = directoryHolderValid(requestDirectoryIndex)(0) &&
    directoryHolderIds(requestDirectoryIndex)(0) === port.reqRx.bits.srcID
  private val requestHolder1 = directoryHolderValid(requestDirectoryIndex)(1) &&
    directoryHolderIds(requestDirectoryIndex)(1) === port.reqRx.bits.srcID
  private val requestHasHolder = requestHolder0 || requestHolder1
  private val requestHasOtherHolder =
    ((directoryHolderValid(requestDirectoryIndex)(0) && !requestHolder0) ||
      (directoryHolderValid(requestDirectoryIndex)(1) && !requestHolder1))
  private val readNeedsSnoop = (if (outer.params.coherent) {
    (port.reqRx.bits.opcode === ChiOpcode.Req.ReadShared ||
      port.reqRx.bits.opcode === ChiOpcode.Req.ReadUnique) && requestHasOtherHolder
  } else false.B)

  // Resolve both holders from the saved request only after the snoop response arrives.
  private def finishSnoop(resp: UInt): Unit = {
    val index = request.addr(6 + directoryIndexWidth - 1, 6)
    val invalid = resp === ChiResp.I || resp === ChiResp.I_PD
    directoryHolderValid(index)(snoopHolderSlot) := !invalid
    directoryHolderStates(index)(snoopHolderSlot) := Mux(invalid, RnCacheState.I, RnCacheState.SC)
    directoryHolderValid(index)(snoopRequesterSlot) := true.B
    directoryHolderIds(index)(snoopRequesterSlot) := request.srcID
    directoryHolderStates(index)(snoopRequesterSlot) := Mux(invalid, RnCacheState.UC, RnCacheState.SC)
    completionResp := Mux(request.opcode === ChiOpcode.Req.ReadUnique && resp === ChiResp.I_PD,
      ChiResp.UD_PD, Mux(invalid, ChiResp.UC, ChiResp.SC))
  }
  if (outer.params.coherent) {
    val coherentRead = port.reqRx.bits.opcode === ChiOpcode.Req.ReadShared ||
      port.reqRx.bits.opcode === ChiOpcode.Req.ReadUnique
    val evict = port.reqRx.bits.opcode === ChiOpcode.Req.Evict
    val writeBack = port.reqRx.bits.opcode === ChiOpcode.Req.WriteBackFull
    when(port.reqRx.fire && (coherentRead || evict || writeBack)) {
      // This directory covers a bounded address space; entries are never replaced.
      assert(requestInDirectoryRange, "HN address exceeds directory range")
    }
    when(port.reqRx.fire && (state === idle || reservedRetry)) {
      when(coherentRead) {
        val requesterUnique = (0 until 2).map { slot =>
          directoryHolderValid(requestDirectoryIndex)(slot) &&
            directoryHolderIds(requestDirectoryIndex)(slot) === port.reqRx.bits.srcID &&
            directoryHolderStates(requestDirectoryIndex)(slot) === RnCacheState.UC
        }.reduce(_ || _)
        assert(!requesterUnique, "HN requester already holds unique permission")
        assert(requestHasHolder || !directoryHolderValid(requestDirectoryIndex).asUInt.andR,
          "HN directory has no free holder slot")

        val shared = port.reqRx.bits.opcode === ChiOpcode.Req.ReadShared && directoryOccupied
        completionResp := Mux(shared, ChiResp.SC, ChiResp.UC)
        when(readNeedsSnoop) {
          val otherSlot = Mux(directoryHolderValid(requestDirectoryIndex)(0) && !requestHolder0, 0.U, 1.U)
          snoopTarget := directoryHolderIds(requestDirectoryIndex)(otherSlot)
          snoopHolderSlot := otherSlot
          snoopRequesterSlot := Mux(requestHasHolder, Mux(requestHolder0, 0.U, 1.U), 1.U - otherSlot)
        }.elsewhen(!directoryOccupied) {
          directoryHolderValid(requestDirectoryIndex)(0) := true.B
          directoryHolderIds(requestDirectoryIndex)(0) := port.reqRx.bits.srcID
          directoryHolderStates(requestDirectoryIndex)(0) := RnCacheState.UC
        }.elsewhen(requestHasHolder) {
          when(requestHolder0) {
            directoryHolderStates(requestDirectoryIndex)(0) := Mux(shared, RnCacheState.SC, RnCacheState.UC)
          }.otherwise {
            directoryHolderStates(requestDirectoryIndex)(1) := Mux(shared, RnCacheState.SC, RnCacheState.UC)
          }
        }.otherwise {
          val slot = Mux(directoryHolderValid(requestDirectoryIndex)(0), 1.U, 0.U)
          directoryHolderValid(requestDirectoryIndex)(slot) := true.B
          directoryHolderIds(requestDirectoryIndex)(slot) := port.reqRx.bits.srcID
          directoryHolderStates(requestDirectoryIndex)(slot) := RnCacheState.SC
        }
      }.elsewhen(evict) {
        for (slot <- 0 until 2) {
          when(directoryHolderValid(requestDirectoryIndex)(slot) &&
            directoryHolderIds(requestDirectoryIndex)(slot) === port.reqRx.bits.srcID) {
            directoryHolderValid(requestDirectoryIndex)(slot) := false.B
            directoryHolderStates(requestDirectoryIndex)(slot) := RnCacheState.I
          }
        }
      }.elsewhen(!writeBack) {
        assert(!directoryOccupied, "HN non-coherent access requires an uncached line")
      }
    }
  }

  port.reqRx.ready := (state === idle && !hasRetryWaiting) ||
    (activeState && retrySlotFree && !retryAckPending) ||
    // Drain a pending RetryAck before a credited write can present its DBID response.
    (state === waitRetryReq && !retryAckPending && (reservedRetry || retrySlotFree))
  when(port.reqRx.fire) {
    val req = port.reqRx.bits
    when(reservedRetry) {
      val saved = request
      assert(req.opcode === saved.opcode && req.addr === saved.addr && req.txnID === saved.txnID &&
        req.srcID === saved.srcID && req.tgtID === saved.tgtID,
        "HN received a retry for another request")
      assert(!req.allowRetry && req.pCrdType === 0.U, "HN expects a credited retry")
      request := req
      retryAckSeen := false.B
      creditHeld := false.B
      // Upstream credit does not count as a retry of the downstream SN request.
      retried := false.B
      snoopWrite := false.B
      if (outer.params.coherent) {
        state := Mux(req.opcode === ChiOpcode.Req.ReadNoSnp ||
          req.opcode === ChiOpcode.Req.ReadShared || req.opcode === ChiOpcode.Req.ReadUnique,
          Mux(readNeedsSnoop, sendSnp, sendSnReq), sendRnRsp)
      } else {
        state := Mux(req.opcode === ChiOpcode.Req.ReadNoSnp, sendSnReq, sendRnRsp)
      }
    }.elsewhen(state === idle) {
      if (outer.params.coherent) {
        assert(req.opcode === ChiOpcode.Req.ReadNoSnp || req.opcode === ChiOpcode.Req.WriteNoSnpFull ||
          req.opcode === ChiOpcode.Req.ReadShared || req.opcode === ChiOpcode.Req.ReadUnique ||
          req.opcode === ChiOpcode.Req.Evict || req.opcode === ChiOpcode.Req.WriteBackFull,
          "HN currently supports only non-coherent reads/writes, coherent reads, Evict and WriteBackFull")
      } else {
        assert(req.opcode === ChiOpcode.Req.ReadNoSnp || req.opcode === ChiOpcode.Req.WriteNoSnpFull,
          "HN currently supports only ReadNoSnp and WriteNoSnpFull")
      }
      assert(req.tgtID === outer.params.nodeId.U, "REQ target must be this HN")
      assert(req.addr(5, 0) === 0.U, "HN requires line-aligned addresses")
      assert(req.allowRetry && req.pCrdType === 0.U, "HN currently accepts only first attempts")
      request := req
      retryAckSeen := false.B
      creditHeld := false.B
      retried := false.B
      snoopWrite := false.B
      if (outer.params.coherent) {
        state := Mux(req.opcode === ChiOpcode.Req.ReadNoSnp ||
          req.opcode === ChiOpcode.Req.ReadShared || req.opcode === ChiOpcode.Req.ReadUnique,
          Mux(readNeedsSnoop, sendSnp, sendSnReq), sendRnRsp)
      } else {
        state := Mux(req.opcode === ChiOpcode.Req.ReadNoSnp, sendSnReq, sendRnRsp)
      }
    }.otherwise {
      if (outer.params.coherent) {
        assert(req.opcode === ChiOpcode.Req.ReadNoSnp || req.opcode === ChiOpcode.Req.WriteNoSnpFull ||
          req.opcode === ChiOpcode.Req.ReadShared || req.opcode === ChiOpcode.Req.ReadUnique ||
          req.opcode === ChiOpcode.Req.Evict || req.opcode === ChiOpcode.Req.WriteBackFull,
          "HN currently supports only non-coherent reads/writes, coherent reads, Evict and WriteBackFull")
      } else {
        assert(req.opcode === ChiOpcode.Req.ReadNoSnp || req.opcode === ChiOpcode.Req.WriteNoSnpFull,
          "HN currently supports only ReadNoSnp and WriteNoSnpFull")
      }
      assert(req.tgtID === outer.params.nodeId.U && req.addr(5, 0) === 0.U &&
        req.allowRetry && req.pCrdType === 0.U,
        "HN expects a retryable first attempt")
      assert(retrySlotFree && !retryAckPending, "HN retry table is full")
      assert(!duplicateRetry, "HN already has a retry waiting for this requester")
      var allEarlierOccupied: Bool = true.B
      for (slot <- 0 until outer.params.retrySlots) {
        val choose = allEarlierOccupied && !retryWaiting(slot)
        when(choose) {
          retryRequests(slot) := req
          retryWaiting(slot) := true.B
        }
        allEarlierOccupied = allEarlierOccupied && retryWaiting(slot)
      }
      retryAckTarget := req.srcID
      retryAckTxn := req.txnID
      retryAckPending := true.B
    }
  }

  port.reqTx.valid := state === sendSnReq
  port.reqTx.bits := request
  port.reqTx.bits.tgtID := outer.params.snId.U
  port.reqTx.bits.srcID := outer.params.nodeId.U
  port.reqTx.bits.txnID := downstreamTxnId
  if (outer.params.coherent) {
    port.reqTx.bits.opcode := Mux(snoopWrite, ChiOpcode.Req.WriteNoSnpFull,
      Mux(request.opcode === ChiOpcode.Req.ReadShared ||
      request.opcode === ChiOpcode.Req.ReadUnique, ChiOpcode.Req.ReadNoSnp,
      Mux(request.opcode === ChiOpcode.Req.WriteBackFull, ChiOpcode.Req.WriteNoSnpFull, request.opcode)))
  }
  port.reqTx.bits.allowRetry := !retryReady
  port.reqTx.bits.pCrdType := 0.U
  when(port.reqTx.fire) {
    when(retryReady) {
      retryAckSeen := false.B
      creditHeld := false.B
      retried := true.B
    }
    if (outer.params.coherent) {
      state := Mux(!snoopWrite && (request.opcode === ChiOpcode.Req.ReadNoSnp ||
        request.opcode === ChiOpcode.Req.ReadShared || request.opcode === ChiOpcode.Req.ReadUnique),
        waitSnData, waitSnDbId)
    } else {
      state := Mux(request.opcode === ChiOpcode.Req.ReadNoSnp, waitSnData, waitSnDbId)
    }
  }

  port.rspTx.valid := retryAckPending || state === sendRnRsp || state === sendGrant
  port.rspTx.bits := 0.U.asTypeOf(port.rspTx.bits)
  port.rspTx.bits.tgtID := Mux(state === sendRnRsp, request.srcID,
    Mux(retryAckPending, retryAckTarget, request.srcID))
  port.rspTx.bits.srcID := outer.params.nodeId.U
  port.rspTx.bits.txnID := Mux(state === sendRnRsp, request.txnID,
    Mux(retryAckPending, retryAckTxn, 0.U))
  private val rnResponseOpcode = if (outer.params.coherent) {
    Mux(request.opcode === ChiOpcode.Req.Evict, ChiOpcode.Rsp.Comp, ChiOpcode.Rsp.CompDBIDResp)
  } else ChiOpcode.Rsp.CompDBIDResp
  port.rspTx.bits.opcode := Mux(state === sendRnRsp, rnResponseOpcode,
    Mux(retryAckPending, ChiOpcode.Rsp.RetryAck, ChiOpcode.Rsp.PCrdGrant))
  port.rspTx.bits.dbID := Mux(state === sendGrant, 0.U, dbId)
  when(port.rspTx.fire) {
    when(state === sendRnRsp) {
      if (outer.params.coherent) {
        state := Mux(request.opcode === ChiOpcode.Req.Evict, idle, waitRnData)
      } else {
        state := waitRnData
      }
    }.elsewhen(retryAckPending) {
      retryAckPending := false.B
    }.elsewhen(state === sendGrant) {
      state := waitRetryReq
    }.otherwise {
      state := waitRnData
    }
  }

  port.datRx.ready := state === waitRnData ||
    (state === waitSnData && !retryAckSeen && !creditHeld && !port.rspRx.valid) ||
    state === waitSnpResponse && !port.rspRx.valid
  when(port.datRx.fire) {
    val dat = port.datRx.bits
    when(state === waitSnpResponse) {
      assert(dat.tgtID === outer.params.nodeId.U && dat.srcID === snoopTarget &&
        dat.txnID === snoopTxnId, "HN received snoop data for another transaction")
      assert(dat.opcode === ChiOpcode.Dat.SnpRespData && dat.dbID === 0.U &&
        (dat.resp === ChiResp.I_PD || (!uniqueRead && dat.resp === ChiResp.SC_PD)),
        "HN expects snoop data with dirty responsibility")
      if (outer.params.coherent) { finishSnoop(dat.resp) }
      // Unique transfers dirty responsibility directly; shared first cleans the line in SN.
      snoopWrite := !uniqueRead
      state := Mux(uniqueRead, sendRnData, sendSnReq)
    }.elsewhen(state === waitSnData) {
      assert(dat.tgtID === outer.params.nodeId.U && dat.srcID === outer.params.snId.U &&
        dat.txnID === downstreamTxnId, "HN received data for another transaction")
      assert(dat.opcode === ChiOpcode.Dat.CompData && dat.resp === ChiResp.I && dat.dbID === 0.U,
        "HN expects non-coherent CompData")
      state := sendRnData
    }.otherwise {
      assert(dat.tgtID === outer.params.nodeId.U && dat.srcID === request.srcID && dat.txnID === dbId,
        "HN received write data for another transaction")
      if (outer.params.coherent) {
        when(request.opcode === ChiOpcode.Req.WriteBackFull) {
          assert(dat.opcode === ChiOpcode.Dat.CopyBackWrData && dat.dbID === 0.U &&
            (dat.resp === ChiResp.UD_PD || dat.resp === ChiResp.I),
            "HN expects CopyBackWrData with dirty data or cancellation")
          when(dat.resp === ChiResp.UD_PD) {
            // Use the saved address: the REQ input may already carry another requester.
            val index = request.addr(6 + directoryIndexWidth - 1, 6)
            val ownsLine = (0 until 2).map { slot =>
              directoryHolderValid(index)(slot) &&
                directoryHolderIds(index)(slot) === request.srcID &&
                directoryHolderStates(index)(slot) === RnCacheState.UC
            }.reduce(_ || _)
            val otherHolder = (0 until 2).map { slot =>
              directoryHolderValid(index)(slot) && directoryHolderIds(index)(slot) =/= request.srcID
            }.reduce(_ || _)
            assert(ownsLine && !otherHolder, "HN dirty writeback requires the unique holder")
            for (slot <- 0 until 2) {
              when(directoryHolderValid(index)(slot) && directoryHolderIds(index)(slot) === request.srcID) {
                directoryHolderValid(index)(slot) := false.B
                directoryHolderStates(index)(slot) := RnCacheState.I
              }
            }
            state := sendSnReq
          }.otherwise {
            // A snoop already transferred responsibility; discard the cancelled writeback.
            state := idle
          }
        }.otherwise {
          assert(dat.opcode === ChiOpcode.Dat.NonCopyBackWrData && dat.resp === ChiResp.I && dat.dbID === 0.U,
            "HN expects non-coherent NonCopyBackWrData")
          state := sendSnReq
        }
      } else {
        assert(dat.opcode === ChiOpcode.Dat.NonCopyBackWrData && dat.resp === ChiResp.I && dat.dbID === 0.U,
          "HN expects non-coherent NonCopyBackWrData")
        state := sendSnReq
      }
    }
    data := dat.data
  }

  port.rspRx.ready := (waitingForSnReply && !retryReady) || state === waitSnComp ||
    state === waitRnCompAck || state === waitSnpResponse
  when(port.rspRx.fire) {
    val rsp = port.rspRx.bits
    when(state === waitSnpResponse) {
      assert(rsp.tgtID === outer.params.nodeId.U && rsp.srcID === snoopTarget &&
        rsp.txnID === snoopTxnId, "HN received snoop response for another transaction")
      assert(rsp.opcode === ChiOpcode.Rsp.SnpResp &&
        (rsp.resp === ChiResp.I || (!uniqueRead && rsp.resp === ChiResp.SC)) &&
        rsp.dbID === 0.U && rsp.pCrdType === 0.U, "HN expects SnpResp with I, or SC for a shared read")
      if (outer.params.coherent) { finishSnoop(rsp.resp) }
      state := sendSnReq
    }.elsewhen(state === waitRnCompAck) {
      assert(rsp.tgtID === outer.params.nodeId.U && rsp.srcID === request.srcID &&
        rsp.txnID === dbId && rsp.opcode === ChiOpcode.Rsp.CompAck &&
        rsp.resp === ChiResp.I && rsp.dbID === 0.U && rsp.pCrdType === 0.U,
        "HN expects matching RN CompAck")
      state := idle
    }.otherwise {
      assert(rsp.tgtID === outer.params.nodeId.U && rsp.srcID === outer.params.snId.U,
        "HN received a response for another transaction")
      assert(rsp.resp === ChiResp.I && rsp.pCrdType === 0.U, "HN expects non-coherent SN responses")
      when(rsp.opcode === ChiOpcode.Rsp.RetryAck || rsp.opcode === ChiOpcode.Rsp.PCrdGrant) {
      assert(waitingForSnReply && !retried, "HN cannot retry an already accepted SN request")
      when(rsp.opcode === ChiOpcode.Rsp.RetryAck) {
        assert(rsp.txnID === downstreamTxnId, "HN received RetryAck for another transaction")
        assert(rsp.dbID === 0.U, "HN expects zero RetryAck DBID")
        assert(!retryAckSeen, "HN received duplicate RetryAck")
        retryAckSeen := true.B
      }.otherwise {
        assert(rsp.txnID === 0.U && rsp.dbID === 0.U, "HN expects zero PCrdGrant IDs")
        assert(!creditHeld, "HN received duplicate PCrdGrant")
        creditHeld := true.B
      }
      }.otherwise {
        assert(rsp.txnID === downstreamTxnId, "HN received a response for another transaction")
        assert(!retryAckSeen && !creditHeld, "HN cannot complete a request awaiting retry")
        when(state === waitSnDbId) {
          assert(rsp.opcode === ChiOpcode.Rsp.DBIDResp, "HN expects SN DBIDResp")
          snDbId := rsp.dbID
          state := sendSnData
        }.otherwise {
          assert(state === waitSnComp && rsp.opcode === ChiOpcode.Rsp.Comp,
            "HN expects SN write completion")
          assert(rsp.dbID === snDbId, "HN received completion for another data slot")
          state := (if (outer.params.coherent) Mux(snoopWrite, sendRnData, idle) else idle)
        }
      }
    }
  }
  when(waitingForSnReply && retryReady) { state := sendSnReq }

  port.datTx.valid := state === sendRnData || state === sendSnData
  port.datTx.bits := 0.U.asTypeOf(port.datTx.bits)
  port.datTx.bits.tgtID := Mux(state === sendSnData, outer.params.snId.U, request.srcID)
  port.datTx.bits.srcID := outer.params.nodeId.U
  port.datTx.bits.txnID := Mux(state === sendSnData, snDbId, request.txnID)
  port.datTx.bits.opcode := Mux(state === sendSnData, ChiOpcode.Dat.NonCopyBackWrData, ChiOpcode.Dat.CompData)
  port.datTx.bits.resp := (if (outer.params.coherent) {
    Mux(state === sendRnData && (request.opcode === ChiOpcode.Req.ReadShared ||
      request.opcode === ChiOpcode.Req.ReadUnique), completionResp, ChiResp.I)
  } else ChiResp.I)
  port.datTx.bits.data := data
  when(port.datTx.fire) {
    if (outer.params.coherent) {
      state := Mux(state === sendRnData &&
        (request.opcode === ChiOpcode.Req.ReadShared ||
          request.opcode === ChiOpcode.Req.ReadUnique), waitRnCompAck,
        Mux(state === sendRnData, idle, waitSnComp))
    } else {
      state := Mux(state === sendRnData, idle, waitSnComp)
    }
  }

  port.snpTx.valid := state === sendSnp
  port.snpTx.bits := 0.U.asTypeOf(port.snpTx.bits)
  port.snpTx.bits.dst := snoopTarget
  port.snpTx.bits.payload.srcID := outer.params.nodeId.U
  port.snpTx.bits.payload.txnID := snoopTxnId
  port.snpTx.bits.payload.opcode := Mux(uniqueRead, ChiOpcode.Snp.SnpUnique, ChiOpcode.Snp.SnpShared)
  port.snpTx.bits.payload.addr := request.addr
  when(port.snpTx.fire) { state := waitSnpResponse }
  port.snpRx.ready := false.B

  when(state === idle && hasRetryWaiting) {
    // Reserve the idle transaction context, freeing the waiting slot before granting credit.
    // Otherwise a new REQ at the ring FIFO head can block the credited retry behind it.
    request := (if (outer.params.retrySlots == 1) retryRequests.head else retryRequests(selectedRetrySlot))
    for (slot <- 0 until outer.params.retrySlots) {
      when(selectedRetrySlot === slot.U) { retryWaiting(slot) := false.B }
    }
    rrSlot := Mux(selectedRetrySlot === (outer.params.retrySlots - 1).U, 0.U, selectedRetrySlot + 1.U)
    state := sendGrant
  }
}
