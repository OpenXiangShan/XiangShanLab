package minixiangshan.mem.dcache

import chisel3._
import chisel3.util.log2Ceil
import chiseltest._
import minixiangshan.backend.decode.LsuOp
import minixiangshan.config._
import minixiangshan.mem.L2cache.L2WriteKind
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MSHREntryNativeSpec
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  behavior of "DCache MSHREntry native L2 path"

  private implicit val p: Parameters =
    new Parameters(Map(DebugConfigKeys.EnableDifftest -> true))

  private def init(dut: MSHREntry): Unit = {
    dut.io.id.poke(1.U)
    dut.io.req.valid.poke(false.B)
    dut.io.refillWriteAck.poke(false.B)
    dut.io.release.poke(false.B)
    dut.io.l2.read.req.ready.poke(false.B)
    dut.io.l2.read.resp.valid.poke(false.B)
    dut.io.l2.write.req.ready.poke(false.B)
    dut.io.l2.write.done.valid.poke(false.B)
  }

  private def request(
      dut: MSHREntry,
      paddr: BigInt,
      reqType: UInt,
      victimDirty: Boolean = false,
      victimTag: BigInt = 0,
      victimData: BigInt = 0,
      storeData: BigInt = 0,
      lsuOp: UInt = LsuOp.lw
  ): Unit = {
    dut.io.req.bits.paddr.poke(paddr.U)
    dut.io.req.bits.reqType.poke(reqType)
    dut.io.req.bits.victimWay.poke(1.U)
    dut.io.req.bits.victimDirty.poke(victimDirty.B)
    dut.io.req.bits.victimTag.poke(victimTag.U)
    dut.io.req.bits.victimData.poke(victimData.U)
    dut.io.req.bits.storeData.poke(storeData.U)
    dut.io.req.bits.lsuOp.poke(lsuOp)
    dut.io.req.valid.poke(true.B)
    dut.io.req.ready.expect(true.B)
    dut.clock.step()
    dut.io.req.valid.poke(false.B)
  }

  it should "read a clean cacheable miss and accept a complete line response" in {
    test(new MSHREntry) { dut =>
      init(dut)
      val line = BigInt("0123456789abcdef" * 8, 16)
      request(dut, 0x80000124L, MshrReqType.cacheable)

      dut.io.l2.read.req.valid.expect(true.B)
      dut.io.l2.read.req.bits.id.expect(1.U)
      dut.io.l2.read.req.bits.addr.expect(0x80000100L.U)
      dut.io.l2.read.req.bits.size.expect(2.U)
      dut.io.l2.read.req.bits.uncache.expect(false.B)
      dut.io.l2.write.req.valid.expect(false.B)

      dut.io.l2.read.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2.read.req.ready.poke(false.B)
      dut.io.l2.read.resp.valid.poke(true.B)
      dut.io.l2.read.resp.bits.id.poke(1.U)
      dut.io.l2.read.resp.bits.data.poke(line.U)
      dut.io.l2.read.resp.bits.fullLine.poke(true.B)
      dut.io.l2.read.resp.bits.last.poke(true.B)
      dut.io.l2.read.resp.ready.expect(true.B)
      dut.clock.step()
      dut.io.l2.read.resp.valid.poke(false.B)

      dut.io.refillWriteReq.expect(true.B)
      dut.io.refillData.expect(line.U)
      dut.io.refillWriteAck.poke(true.B)
      dut.clock.step()
      dut.io.done.expect(true.B)
    }
  }

  it should "hand off a dirty victim as putLine before issuing the refill read" in {
    test(new MSHREntry) { dut =>
      init(dut)
      val victim = BigInt("89abcdef01234567" * 8, 16)
      val victimTag = BigInt("12345", 16)
      val missAddr = BigInt("80015558", 16)
      request(dut, missAddr, MshrReqType.cacheable,
        victimDirty = true, victimTag = victimTag, victimData = victim)

      val idxBits = log2Ceil(p(CoreConfigKeys.NSetsD))
      val blockOffBits = log2Ceil(p(CoreConfigKeys.BlockBytes))
      val tagBits = 32 - idxBits - blockOffBits
      val tagMask = (BigInt(1) << tagBits) - 1
      val set = (missAddr >> blockOffBits) & ((BigInt(1) << idxBits) - 1)
      val victimAddr = ((victimTag & tagMask) << (idxBits + blockOffBits)) | (set << blockOffBits)
      dut.io.l2.write.req.valid.expect(true.B)
      dut.io.l2.write.req.bits.id.expect(1.U)
      dut.io.l2.write.req.bits.addr.expect(victimAddr.U)
      dut.io.l2.write.req.bits.kind.expect(L2WriteKind.putLine)
      dut.io.l2.write.req.bits.data.expect(victim.U)

      dut.io.l2.write.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2.write.req.ready.poke(false.B)
      dut.io.l2.write.done.valid.poke(false.B)
      dut.io.l2.read.req.valid.expect(true.B)
      dut.io.l2.read.req.bits.addr.expect((missAddr & ~BigInt(63)).U)
    }
  }

  it should "assemble sixteen low words when L2 returns a beat stream" in {
    test(new MSHREntry) { dut =>
      init(dut)
      request(dut, 0x80000240L, MshrReqType.cacheable)
      dut.io.l2.read.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2.read.req.ready.poke(false.B)

      var expected = BigInt(0)
      for (i <- 0 until 16) {
        val word = BigInt("a5000000", 16) + i
        expected |= word << (32 * i)
        dut.io.l2.read.resp.valid.poke(true.B)
        dut.io.l2.read.resp.bits.id.poke(1.U)
        dut.io.l2.read.resp.bits.data.poke(word.U)
        dut.io.l2.read.resp.bits.fullLine.poke(false.B)
        dut.io.l2.read.resp.bits.last.poke((i == 15).B)
        dut.io.l2.read.resp.ready.expect(true.B)
        dut.clock.step()
      }
      dut.io.l2.read.resp.valid.poke(false.B)
      dut.io.refillWriteReq.expect(true.B)
      dut.io.refillData.expect(expected.U)
    }
  }

  it should "complete uncache traffic only on its native response or write done" in {
    test(new MSHREntry) { dut =>
      init(dut)
      request(dut, 0x1f000003L, MshrReqType.uncacheRead, lsuOp = LsuOp.lbu)
      dut.io.l2.read.req.bits.addr.expect(0x1f000003L.U)
      dut.io.l2.read.req.bits.size.expect(0.U)
      dut.io.l2.read.req.bits.uncache.expect(true.B)
      dut.io.l2.read.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2.read.req.ready.poke(false.B)
      dut.io.l2.read.resp.valid.poke(true.B)
      dut.io.l2.read.resp.bits.id.poke(1.U)
      dut.io.l2.read.resp.bits.data.poke(0x44332211L.U)
      dut.io.l2.read.resp.bits.fullLine.poke(false.B)
      dut.io.l2.read.resp.bits.last.poke(true.B)
      dut.clock.step()
      dut.io.l2.read.resp.valid.poke(false.B)
      dut.io.done.expect(true.B)
      dut.io.uncacheData.expect(0x44332211L.U)
      dut.io.release.poke(true.B)
      dut.clock.step()

      dut.io.release.poke(false.B)
      request(dut, 0x1f000002L, MshrReqType.uncacheWrite,
        storeData = 0xabcd, lsuOp = LsuOp.sh)
      dut.io.l2.write.req.bits.kind.expect(L2WriteKind.uncache)
      dut.io.l2.write.req.bits.size.expect(1.U)
      dut.io.l2.write.req.bits.data.expect(0xabcd0000L.U)
      dut.io.l2.write.req.bits.strb.expect("b1100".U)
      dut.io.l2.write.req.ready.poke(true.B)
      dut.clock.step()
      dut.io.l2.write.req.ready.poke(false.B)
      dut.io.done.expect(false.B)
      dut.clock.step(2)
      dut.io.done.expect(false.B)
      dut.io.l2.write.done.valid.poke(true.B)
      dut.io.l2.write.done.bits.id.poke(1.U)
      dut.clock.step()
      dut.io.done.expect(true.B)
    }
  }
}
