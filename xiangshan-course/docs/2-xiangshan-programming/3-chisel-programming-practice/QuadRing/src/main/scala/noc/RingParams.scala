package noc

import chisel3.util.log2Ceil

/** Shared parameters for one ring. */
case class RingParams(nodeCount: Int = 4, bufferDepth: Int = 3) {
  require(nodeCount >= 2, "a ring must contain at least two nodes")
  require(bufferDepth >= 2, "bubble flow control requires at least two buffer entries")

  val dstWidth: Int = log2Ceil(nodeCount)
}
