package minixiangshan.mem.L2cache

import chisel3._
import chiseltest._
import minixiangshan.config._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class L2CacheArraySpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "L2CacheArray"

  private implicit val p: Parameters =
    new Parameters(Map(
      DebugConfigKeys.EnableDifftest -> true,
      CoreConfigKeys.L2Ways -> 8))

  private def idle(dut: L2CacheArray): Unit = {
    dut.io.read.req.valid.poke(false.B)
    dut.io.read.req.bits.set.poke(0.U)
    dut.io.write.valid.poke(false.B)
    dut.io.write.bits.set.poke(0.U)
    dut.io.write.bits.way.poke(0.U)
    dut.io.write.bits.valid.poke(false.B)
    dut.io.write.bits.dirty.poke(false.B)
    dut.io.write.bits.tag.poke(0.U)
    dut.io.write.bits.data.poke(0.U)
    dut.io.write.bits.dataWen.poke(false.B)
  }

  private def writeLine(dut: L2CacheArray, set: Int, way: Int,
                        tag: Int, data: BigInt, dirty: Boolean): Unit = {
    dut.io.write.valid.poke(true.B)
    dut.io.write.bits.set.poke(set.U)
    dut.io.write.bits.way.poke(way.U)
    dut.io.write.bits.valid.poke(true.B)
    dut.io.write.bits.dirty.poke(dirty.B)
    dut.io.write.bits.tag.poke(tag.U)
    dut.io.write.bits.data.poke(data.U)
    dut.io.write.bits.dataWen.poke(true.B)
    dut.clock.step()
    dut.io.write.valid.poke(false.B)
  }

  it should "block reads for all 512 metadata scrub cycles" in {
    test(new L2CacheArray) { dut =>
      idle(dut)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.read.req.valid.poke(true.B)
      dut.io.read.req.bits.set.poke(0.U)
      for (_ <- 0 until 512) {
        dut.io.read.req.ready.expect(false.B)
        dut.clock.step()
      }
      dut.io.read.req.ready.expect(true.B)
    }
  }

  it should "return each accepted read exactly two cycles later at II one" in {
    test(new L2CacheArray) { dut =>
      idle(dut)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.step(512)

      writeLine(dut, 7, 3, 0x1234, BigInt("11" * 64, 16), dirty = true)
      writeLine(dut, 8, 6, 0x4321, BigInt("22" * 64, 16), dirty = false)

      dut.io.read.req.valid.poke(true.B)
      dut.io.read.req.bits.set.poke(7.U)
      dut.io.read.req.ready.expect(true.B)
      dut.clock.step()
      dut.io.read.resp.valid.expect(false.B)

      dut.io.read.req.bits.set.poke(8.U)
      dut.io.read.req.ready.expect(true.B)
      dut.clock.step()
      dut.io.read.resp.valid.expect(true.B)
      dut.io.read.resp.bits.set.expect(7.U)
      dut.io.read.resp.bits.ways(3).valid.expect(true.B)
      dut.io.read.resp.bits.ways(3).dirty.expect(true.B)
      dut.io.read.resp.bits.ways(3).tag.expect(0x1234.U)
      dut.io.read.resp.bits.ways(3).data.expect(BigInt("11" * 64, 16).U)

      dut.io.read.req.valid.poke(false.B)
      dut.clock.step()
      dut.io.read.resp.valid.expect(true.B)
      dut.io.read.resp.bits.set.expect(8.U)
      dut.io.read.resp.bits.ways(6).valid.expect(true.B)
      dut.io.read.resp.bits.ways(6).dirty.expect(false.B)
      dut.io.read.resp.bits.ways(6).tag.expect(0x4321.U)
      dut.io.read.resp.bits.ways(6).data.expect(BigInt("22" * 64, 16).U)
      dut.clock.step()
      dut.io.read.resp.valid.expect(false.B)
    }
  }

  it should "reset all valid bits without depending on RAM contents" in {
    test(new L2CacheArray) { dut =>
      idle(dut)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.step(512)
      writeLine(dut, 511, 7, 0x55, BigInt(1), dirty = true)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.step(512)
      dut.io.read.req.valid.poke(true.B)
      dut.io.read.req.bits.set.poke(511.U)
      dut.clock.step()
      dut.io.read.req.valid.poke(false.B)
      dut.clock.step()
      dut.io.read.resp.valid.expect(true.B)
      for (way <- 0 until 8) {
        dut.io.read.resp.bits.ways(way).valid.expect(false.B)
      }
    }
  }

  it should "block a same-set read and preserve data on a metadata-only write" in {
    test(new L2CacheArray) { dut =>
      idle(dut)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.step(512)

      val oldData = BigInt("a5" * 64, 16)
      writeLine(dut, 9, 2, 0x66, oldData, dirty = false)

      // 同set写入时，本拍lookup不能被接受，避免依赖BRAM同址读写模式。
      dut.io.read.req.valid.poke(true.B)
      dut.io.read.req.bits.set.poke(12.U)
      dut.io.write.valid.poke(true.B)
      dut.io.write.bits.set.poke(12.U)
      dut.io.write.bits.way.poke(1.U)
      dut.io.write.bits.valid.poke(true.B)
      dut.io.write.bits.dirty.poke(true.B)
      dut.io.write.bits.tag.poke(0x77.U)
      dut.io.write.bits.data.poke(BigInt("3c" * 64, 16).U)
      dut.io.write.bits.dataWen.poke(true.B)
      dut.io.read.req.ready.expect(false.B)
      dut.clock.step()

      // 写入结束后的下一拍，同一个请求才能真正fire。
      dut.io.write.valid.poke(false.B)
      dut.io.read.req.ready.expect(true.B)
      dut.clock.step()
      dut.io.read.req.valid.poke(false.B)
      dut.clock.step()
      dut.io.read.resp.valid.expect(true.B)
      dut.io.read.resp.bits.set.expect(12.U)
      dut.io.read.resp.bits.ways(1).valid.expect(true.B)
      dut.io.read.resp.bits.ways(1).dirty.expect(true.B)
      dut.io.read.resp.bits.ways(1).tag.expect(0x77.U)
      dut.io.read.resp.bits.ways(1).data.expect(BigInt("3c" * 64, 16).U)
      dut.clock.step()

      // 只更新metadata时，原来的512-bit数据不能被覆盖。
      dut.io.write.valid.poke(true.B)
      dut.io.write.bits.set.poke(9.U)
      dut.io.write.bits.way.poke(2.U)
      dut.io.write.bits.valid.poke(true.B)
      dut.io.write.bits.dirty.poke(true.B)
      dut.io.write.bits.tag.poke(0x68.U)
      dut.io.write.bits.data.poke(0.U)
      dut.io.write.bits.dataWen.poke(false.B)
      dut.clock.step()
      dut.io.write.valid.poke(false.B)

      dut.io.read.req.valid.poke(true.B)
      dut.io.read.req.bits.set.poke(9.U)
      dut.io.read.req.ready.expect(true.B)
      dut.clock.step()
      dut.io.read.req.valid.poke(false.B)
      dut.clock.step()
      dut.io.read.resp.valid.expect(true.B)
      dut.io.read.resp.bits.ways(2).valid.expect(true.B)
      dut.io.read.resp.bits.ways(2).dirty.expect(true.B)
      dut.io.read.resp.bits.ways(2).tag.expect(0x68.U)
      dut.io.read.resp.bits.ways(2).data.expect(oldData.U)
    }
  }

  it should "allow one read and one write to different sets in the same cycle" in {
    test(new L2CacheArray) { dut =>
      idle(dut)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.step(512)
      writeLine(dut, 3, 0, 0x11, BigInt("12" * 64, 16), dirty = false)

      dut.io.read.req.valid.poke(true.B)
      dut.io.read.req.bits.set.poke(3.U)
      dut.io.write.valid.poke(true.B)
      dut.io.write.bits.set.poke(4.U)
      dut.io.write.bits.way.poke(7.U)
      dut.io.write.bits.valid.poke(true.B)
      dut.io.write.bits.dirty.poke(false.B)
      dut.io.write.bits.tag.poke(0x22.U)
      dut.io.write.bits.data.poke(BigInt("34" * 64, 16).U)
      dut.io.write.bits.dataWen.poke(true.B)
      dut.io.read.req.ready.expect(true.B)
      dut.clock.step()
      idle(dut)
      dut.clock.step()
      dut.io.read.resp.valid.expect(true.B)
      dut.io.read.resp.bits.set.expect(3.U)
      dut.io.read.resp.bits.ways(0).data.expect(BigInt("12" * 64, 16).U)
    }
  }

  it should "return one atomic metadata snapshot when a later write targets the same set" in {
    test(new L2CacheArray) { dut =>
      idle(dut)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.step(512)
      val oldData = BigInt("5a" * 64, 16)
      writeLine(dut, 21, 4, 0x31, oldData, dirty = false)

      // N：lookup已经被Array接收。
      dut.io.read.req.valid.poke(true.B)
      dut.io.read.req.bits.set.poke(21.U)
      dut.io.read.req.ready.expect(true.B)
      dut.clock.step()

      // N+1：控制器之后可能安排同set metadata写；在途响应仍必须是N拍快照。
      dut.io.read.req.valid.poke(false.B)
      dut.io.write.valid.poke(true.B)
      dut.io.write.bits.set.poke(21.U)
      dut.io.write.bits.way.poke(4.U)
      dut.io.write.bits.valid.poke(true.B)
      dut.io.write.bits.dirty.poke(true.B)
      dut.io.write.bits.tag.poke(0x32.U)
      dut.io.write.bits.data.poke(0.U)
      dut.io.write.bits.dataWen.poke(false.B)
      dut.clock.step()

      dut.io.read.resp.valid.expect(true.B)
      dut.io.read.resp.bits.ways(4).valid.expect(true.B)
      dut.io.read.resp.bits.ways(4).dirty.expect(false.B)
      dut.io.read.resp.bits.ways(4).tag.expect(0x31.U)
      dut.io.read.resp.bits.ways(4).data.expect(oldData.U)

      // 下一次lookup才能看到N+1拍的新metadata。
      dut.io.write.valid.poke(false.B)
      dut.io.read.req.valid.poke(true.B)
      dut.io.read.req.bits.set.poke(21.U)
      dut.clock.step()
      dut.io.read.req.valid.poke(false.B)
      dut.clock.step()
      dut.io.read.resp.valid.expect(true.B)
      dut.io.read.resp.bits.ways(4).dirty.expect(true.B)
      dut.io.read.resp.bits.ways(4).tag.expect(0x32.U)
      dut.io.read.resp.bits.ways(4).data.expect(oldData.U)
    }
  }
}
