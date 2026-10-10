package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._
import minixiangshan.config._

class L2Replacer(implicit p: Parameters) extends NSModule {
  require(Seq(2, 4, 8, 16).contains(l2Ways), "tree-PLRU requires 2/4/8/16 ways")

  val io = IO(new Bundle {
    val lookup = Input(new L2ReplacerLookup)
    val touch = Flipped(Valid(new L2ReplacerTouch))
    val victim = Output(UInt(l2WayBits.W))
  })

  // N路二叉tree-PLRU每个set需要N-1位，节点按heap顺序编号。
  val treeState = RegInit(VecInit(Seq.fill(l2Sets)(
    VecInit(Seq.fill(l2Ways - 1)(false.B))
  )))
  val lookupTree = treeState(io.lookup.set)

  def victimFrom(node: Int, ways: Int): UInt = {
    val direction = lookupTree(node)
    if (ways == 2) {
      direction.asUInt
    } else {
      val left = victimFrom(node * 2 + 1, ways / 2)
      val right = victimFrom(node * 2 + 2, ways / 2)
      Cat(direction, Mux(direction, right, left))
    }
  }
  val plruVictim = victimFrom(0, l2Ways)

  // 只要存在 invalid way，就优先选择最低编号 invalid way。
  val firstInvalid = PriorityEncoder(~io.lookup.validMask)
  io.victim := Mux(io.lookup.validMask.andR, plruVictim, firstInvalid)

  when(io.touch.valid) {
    val oldTree = treeState(io.touch.bits.set)
    val nextTree = WireInit(oldTree)
    // 为每个way静态展开更新路径，避免动态Vec索引形成深选择链。
    for (way <- 0 until l2Ways) {
      when(io.touch.bits.way === way.U) {
        var node = 0
        for (level <- 0 until log2Ceil(l2Ways)) {
          val direction = ((way >> (log2Ceil(l2Ways) - 1 - level)) & 1) == 1
          nextTree(node) := (!direction).B
          if (level != log2Ceil(l2Ways) - 1) {
            node = node * 2 + 1 + (if (direction) 1 else 0)
          }
        }
      }
    }

    treeState(io.touch.bits.set) := nextTree
  }
}
