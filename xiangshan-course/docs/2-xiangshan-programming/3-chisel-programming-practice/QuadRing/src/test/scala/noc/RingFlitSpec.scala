package noc

import chisel3._
import chisel3.reflect.DataMirror
import circt.stage.ChiselStage
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RingFlitSpec extends AnyFreeSpec with Matchers {
  private def elaborate[T <: Data](params: RingParams, payloadType: T)(
    check: RingPort[T] => Unit
  ): Unit = {
    ChiselStage.emitCHIRRTL(new Module {
      val io = IO(new RingPort(params, payloadType))
      io.eject <> io.inject
      check(io)
    })
  }

  "invalid ring sizes fail before hardware elaboration" in {
    for (nodeCount <- Seq(-1, 0, 1)) {
      an[IllegalArgumentException] should be thrownBy RingParams(nodeCount)
    }
  }

  "buffer depths that cannot preserve a bubble fail before hardware elaboration" in {
    for (bufferDepth <- Seq(-1, 0, 1)) {
      an[IllegalArgumentException] should be thrownBy RingParams(bufferDepth = bufferDepth)
    }
  }

  "destination width covers power-of-two and non-power-of-two ring sizes" in {
    for ((nodeCount, dstWidth) <- Seq(2 -> 1, 3 -> 2, 4 -> 2, 5 -> 3, 8 -> 3)) {
      elaborate(RingParams(nodeCount), UInt(8.W)) { port =>
        port.inject.bits.dst.getWidth shouldBe dstWidth
        port.eject.bits.getWidth shouldBe dstWidth + 8
      }
    }
  }

  "local ports have the network-side handshake directions" in {
    elaborate(RingParams(), UInt(32.W)) { port =>
      DataMirror.directionOf(port.inject.valid) shouldBe ActualDirection.Input
      DataMirror.directionOf(port.inject.ready) shouldBe ActualDirection.Output
      DataMirror.directionOf(port.inject.bits.payload) shouldBe ActualDirection.Input
      DataMirror.directionOf(port.eject.valid) shouldBe ActualDirection.Output
      DataMirror.directionOf(port.eject.ready) shouldBe ActualDirection.Input
      DataMirror.directionOf(port.eject.bits.payload) shouldBe ActualDirection.Output
    }
  }

  "structured payloads retain their fields through IO cloning" in {
    elaborate(RingParams(3), new Bundle {
      val tag = UInt(3.W)
      val words = Vec(2, UInt(8.W))
    }) { port =>
      port.inject.bits.payload.tag.getWidth shouldBe 3
      port.eject.bits.payload.words.length shouldBe 2
      port.eject.bits.payload.words(0).getWidth shouldBe 8
      port.eject.bits.getWidth shouldBe 21
    }
  }
}
