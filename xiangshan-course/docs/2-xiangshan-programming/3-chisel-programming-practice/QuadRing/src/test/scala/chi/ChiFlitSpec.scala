package chi

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import circt.stage.ChiselStage
import noc.{QuadRing, RingParams, RingPort}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ChiFlitSpec extends AnyFreeSpec with Matchers with ChiselSim {
  "invalid field widths fail before elaboration" in {
    for (width <- Seq(-1, 0)) {
      an[IllegalArgumentException] should be thrownBy ChiParams(nodeIdWidth = width)
      an[IllegalArgumentException] should be thrownBy ChiParams(txnIdWidth = width)
    }
    for (width <- Seq(-1, 0, 5)) {
      an[IllegalArgumentException] should be thrownBy ChiParams(addrWidth = width)
    }
  }

  "payload widths match the subset with default and changed parameters" in {
    val configs = Seq(
      (ChiParams(), Seq(67, 23, 59, 530)),
      (ChiParams(3, 7, 32), Seq(56, 31, 47, 538)),
      (ChiParams(1, 1, 6), Seq(20, 15, 13, 522))
    )
    for ((params, expected) <- configs) {
      ChiselStage.emitCHIRRTL(new Module {
        val req = IO(Input(new ReqFlit(params)))
        val rsp = IO(Input(new RspFlit(params)))
        val snp = IO(Input(new SnpFlit(params)))
        val dat = IO(Input(new DatFlit(params)))
        Seq(req.getWidth, rsp.getWidth, snp.getWidth, dat.getWidth) shouldBe expected
        req.allowRetry.getWidth shouldBe 1
        req.pCrdType.getWidth shouldBe 4
        rsp.pCrdType.getWidth shouldBe 4
        rsp.dbID.getWidth shouldBe params.txnIdWidth
        dat.data.getWidth shouldBe 512
        snp.elements.keySet shouldBe Set("srcID", "txnID", "opcode", "addr")
      })
    }
  }

  "supported opcode and Resp literals match Arm IHI 0050B" in {
    def check(values: Seq[UInt], expected: Seq[Int], width: Int): Unit = {
      values.map(_.litValue) shouldBe expected.map(BigInt(_))
      values.map(_.getWidth) shouldBe Seq.fill(expected.size)(width)
    }
    import ChiOpcode._
    check(Seq(Req.ReadShared, Req.ReadNoSnp, Req.ReadUnique, Req.Evict,
      Req.WriteBackFull, Req.WriteNoSnpFull), Seq(0x01, 0x04, 0x07, 0x0d, 0x1b, 0x1d), 6)
    check(Seq(Rsp.SnpResp, Rsp.CompAck, Rsp.RetryAck, Rsp.Comp, Rsp.CompDBIDResp,
      Rsp.DBIDResp, Rsp.PCrdGrant), Seq(1, 2, 3, 4, 5, 6, 7), 4)
    check(Seq(Snp.SnpShared, Snp.SnpUnique), Seq(1, 7), 5)
    check(Seq(Dat.SnpRespData, Dat.CopyBackWrData, Dat.NonCopyBackWrData, Dat.CompData),
      Seq(1, 2, 3, 4), 3)
    check(Seq(ChiResp.I, ChiResp.SC, ChiResp.UC, ChiResp.I_PD, ChiResp.SC_PD, ChiResp.UD_PD),
      Seq(0, 1, 2, 4, 5, 6), 3)
  }

  // Named fields make failures identify the corrupt field, including wide DAT data.
  private def pokeFields(payload: Bundle, values: Map[String, BigInt]): Unit = {
    payload.elements.keySet shouldBe values.keySet
    for ((name, value) <- values) {
      payload.elements(name) match {
        case field: Bool => field.poke((value != 0).B)
        case field: UInt => field.poke(value.U)
      }
    }
  }

  private def expectFields(payload: Bundle, values: Map[String, BigInt]): Unit = {
    for ((name, value) <- values) {
      withClue(s"field $name: ") {
        payload.elements(name) match {
          case field: Bool => field.expect((value != 0).B)
          case field: UInt => field.expect(value.U)
        }
      }
    }
  }

  for ((nodeCount, params) <- Seq(4 -> ChiParams(), 5 -> ChiParams(3, 7, 32))) {
    s"all CHI fields survive four-ring transport and backpressure with $nodeCount nodes" in {
      simulate(new QuadRing(RingParams(nodeCount), new ReqFlit(params), new RspFlit(params),
        new SnpFlit(params), new DatFlit(params))) { dut =>
        val dst = nodeCount - 1
        val txn = (BigInt(1) << params.txnIdWidth) - 2
        val db = (BigInt(1) << params.txnIdWidth) - 1
        val addr = (BigInt(1) << (params.addrWidth - 1)) | 0x80
        val data = (BigInt(1) << 511) | (BigInt(1) << 257) | BigInt("fedcba9876543210", 16)
        // Nonzero credit types test opaque transport, not protocol acceptance.
        val creditType = if (nodeCount == 4) 0 else 15
        val allowRetry = if (nodeCount == 4) 1 else 0
        val req: Map[String, BigInt] = Map("tgtID" -> dst, "srcID" -> 0, "txnID" -> txn,
          "opcode" -> 1, "addr" -> addr, "allowRetry" -> allowRetry, "pCrdType" -> creditType)
        val rsp: Map[String, BigInt] = Map("tgtID" -> dst, "srcID" -> 0, "txnID" -> txn,
          "opcode" -> 3, "resp" -> 0, "dbID" -> db, "pCrdType" -> creditType)
        val snp: Map[String, BigInt] = Map("srcID" -> 0, "txnID" -> txn,
          "opcode" -> 7, "addr" -> addr)
        val dat: Map[String, BigInt] = Map("tgtID" -> dst, "srcID" -> 0, "txnID" -> txn,
          "opcode" -> 4, "resp" -> 6, "dbID" -> db, "data" -> data)
        val channels = Seq(dut.io.channel0, dut.io.channel1, dut.io.channel2, dut.io.channel3)

        def offer[T <: Bundle](ports: Vec[RingPort[T]], values: Map[String, BigInt]): Unit = {
          for (port <- ports) {
            port.inject.valid.poke(false.B)
            port.inject.bits.dst.poke(dst.U)
            pokeFields(port.inject.bits.payload, values)
            port.eject.ready.poke(false.B)
          }
          ports(0).inject.valid.poke(true.B)
          ports(0).inject.ready.expect(true.B)
        }
        offer(dut.io.channel0, req)
        offer(dut.io.channel1, rsp)
        offer(dut.io.channel2, snp)
        offer(dut.io.channel3, dat)
        dut.clock.step()
        channels.foreach(_(0).inject.valid.poke(false.B))

        // All four destination FIFOs hold their messages while ejection is blocked.
        dut.clock.step(nodeCount + 2)
        for (_ <- 0 until 6) {
          for (ports <- channels; (port, node) <- ports.zipWithIndex) {
            port.eject.valid.expect((node == dst).B)
            if (node == dst) port.eject.bits.dst.expect(dst.U)
          }
          expectFields(dut.io.channel0(dst).eject.bits.payload, req)
          expectFields(dut.io.channel1(dst).eject.bits.payload, rsp)
          expectFields(dut.io.channel2(dst).eject.bits.payload, snp)
          expectFields(dut.io.channel3(dst).eject.bits.payload, dat)
          dut.clock.step()
        }

        channels.foreach(_.foreach(_.eject.ready.poke(true.B)))
        val delivered = Array.fill(4)(0)
        for (_ <- 0 until 2 * nodeCount + 4) {
          for ((ports, channel) <- channels.zipWithIndex; (port, node) <- ports.zipWithIndex) {
            if (port.eject.valid.peek().litToBoolean) {
              node shouldBe dst
              delivered(channel) += 1
            }
          }
          dut.clock.step()
        }
        delivered.toSeq shouldBe Seq(1, 1, 1, 1)
      }
    }
  }
}
