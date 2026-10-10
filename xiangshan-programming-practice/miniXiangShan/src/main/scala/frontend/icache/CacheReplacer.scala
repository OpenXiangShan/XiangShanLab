package minixiangshan.frontend.icache

import chisel3._
import chisel3.util._
import minixiangshan.config.Parameters
import minixiangshan.config.NSModule

/**
 * ═══════════════════════════════════════════════════════════════
 *  Cache 替换策略模块（ICache / DCache 共用）
 *
 *  根据 nWays 自动选择最优算法：
 *    nWays = 2 → 1-bit 真 LRU  （每 set 1 bit，记录最近访问路号）
 *    nWays = 4 → 3-bit PLRU 树 （每 set 3 bit，伪二叉树择路）
 *    nWays = 8 → 7-bit PLRU 树 （每 set 7 bit，伪二叉树择路）
 * ═══════════════════════════════════════════════════════════════
 */
class CacheReplacerI(implicit p: Parameters) extends NSModule {

  val io = IO(new Bundle {
    val touch  = Flipped(new victimChange)
    val victim = Flipped(new victimRead)
    val flush  = new Bundle {
      val valid = Input(Bool())
      val idx   = Input(UInt(idxBitsI.W))
    }
  })

  // Victim read is intentionally split into two stages:
  // 1) latch req/idx, 2) read replacement state combinationally from the
  //    registered index so the response is still available in the next cycle.
  val victimReqReg = RegInit(false.B)
  val victimIdxReg = RegInit(0.U(idxBitsI.W))
  val victimResp = WireDefault(0.U(wayBitsI.W))

  victimReqReg := io.victim.req
  when(io.victim.req) {
    victimIdxReg := io.victim.idx
  }

  if (nWaysI == 2) {
    // ════════════════════════════════════════════════════════
    //  2路组相联：1-bit 真 LRU
    // ════════════════════════════════════════════════════════
    val lastUsed = RegInit(VecInit(Seq.fill(nSetsI)(0.U(1.W))))

    when(io.touch.valid) {
      lastUsed(io.touch.idx) := io.touch.way
    }

    victimResp := ~lastUsed(victimIdxReg)

    when(io.flush.valid) {
      lastUsed(io.flush.idx) := 0.U(1.W)
    }

  } else if (nWaysI == 4) {
    // ════════════════════════════════════════════════════════
    //  4路组相联：3-bit PLRU 伪二叉树
    // ════════════════════════════════════════════════════════
    val plruTree = RegInit(VecInit(Seq.fill(nSetsI)(0.U(3.W))))

    def updatePLRU(oldPLRU: UInt, way: UInt): UInt = {
      val newPLRU = WireDefault(0.U(3.W))
      switch(way) {
        is(0.U) { newPLRU := Cat(oldPLRU(2), 1.U(1.W), 1.U(1.W)) }
        is(1.U) { newPLRU := Cat(oldPLRU(2), 0.U(1.W), 1.U(1.W)) }
        is(2.U) { newPLRU := Cat(1.U(1.W), oldPLRU(1), 0.U(1.W)) }
        is(3.U) { newPLRU := Cat(0.U(1.W), oldPLRU(1), 0.U(1.W)) }
      }
      newPLRU
    }

    def getVictim(plru: UInt): UInt = {
      val plru0 = plru(0)
      val plru1 = plru(1)
      val plru2 = plru(2)
      Mux(!plru0, Mux(!plru1, 0.U, 1.U), Mux(!plru2, 2.U, 3.U))
    }

    when(io.touch.valid) {
      plruTree(io.touch.idx) := updatePLRU(plruTree(io.touch.idx), io.touch.way)
    }

    victimResp := getVictim(plruTree(victimIdxReg))

    when(io.flush.valid) {
      plruTree(io.flush.idx) := 0.U(3.W)
    }

  } else if (nWaysI == 8) {
    // ════════════════════════════════════════════════════════
    //  8路组相联：7-bit PLRU 伪二叉树
    //
    //  树结构（7个节点，控制8个叶子）：
    //                       bit0
    //                    /        \
    //               bit1            bit2
    //              /    \          /    \
    //          bit3      bit4  bit5      bit6
    //          / \       / \   / \       / \
    //         w0 w1     w2 w3 w4 w5     w6 w7
    //
    //  规则：
    //  - bit = 0 表示指针朝向左子树（左侧更老）
    //  - bit = 1 表示指针朝向右子树（右侧更老）
    //  - Victim：沿着指针一路向下，走到哪里就是被替换的路。
    //  - Touch：访问某路时，该路径上的所有节点必须反转，指向**远离**该路的分支。
    // ════════════════════════════════════════════════════════
    val plruTree = RegInit(VecInit(Seq.fill(nSetsI)(0.U(7.W))))

    def updatePLRU(oldPLRU: UInt, way: UInt): UInt = {
      val newPLRU = WireDefault(0.U(7.W))
      // 提取旧状态的各个 bit，方便重新组合
      val b6 = oldPLRU(6)
      val b5 = oldPLRU(5)
      val b4 = oldPLRU(4)
      val b3 = oldPLRU(3)
      val b2 = oldPLRU(2)
      val b1 = oldPLRU(1)
      val b0 = oldPLRU(0)

      // Cat 拼接顺序：Cat(MSB, ..., LSB) -> Cat(b6, b5, b4, b3, b2, b1, b0)
      switch(way) {
        // 访问 Way0 (左-左-左): 保护左侧，将 b0, b1, b3 指向右侧(1)
        is(0.U) { newPLRU := Cat(b6, b5, b4, 1.U(1.W), b2, 1.U(1.W), 1.U(1.W)) }
        // 访问 Way1 (左-左-右): 保护 Way1，将 b0, b1 指向右侧(1)，b3 指向左侧(0)
        is(1.U) { newPLRU := Cat(b6, b5, b4, 0.U(1.W), b2, 1.U(1.W), 1.U(1.W)) }
        // 访问 Way2 (左-右-左): 将 b0 指向右侧(1)，b1 指向左侧(0)，b4 指向右侧(1)
        is(2.U) { newPLRU := Cat(b6, b5, 1.U(1.W), b3, b2, 0.U(1.W), 1.U(1.W)) }
        // 访问 Way3 (左-右-右): 将 b0 指向右侧(1)，b1 指向左侧(0)，b4 指向左侧(0)
        is(3.U) { newPLRU := Cat(b6, b5, 0.U(1.W), b3, b2, 0.U(1.W), 1.U(1.W)) }
        // 访问 Way4 (右-左-左): 将 b0 指向左侧(0)，b2 指向右侧(1)，b5 指向右侧(1)
        is(4.U) { newPLRU := Cat(b6, 1.U(1.W), b4, b3, 1.U(1.W), b1, 0.U(1.W)) }
        // 访问 Way5 (右-左-右): 将 b0 指向左侧(0)，b2 指向右侧(1)，b5 指向左侧(0)
        is(5.U) { newPLRU := Cat(b6, 0.U(1.W), b4, b3, 1.U(1.W), b1, 0.U(1.W)) }
        // 访问 Way6 (右-右-左): 将 b0 指向左侧(0)，b2 指向左侧(0)，b6 指向右侧(1)
        is(6.U) { newPLRU := Cat(1.U(1.W), b5, b4, b3, 0.U(1.W), b1, 0.U(1.W)) }
        // 访问 Way7 (右-右-右): 将 b0 指向左侧(0)，b2 指向左侧(0)，b6 指向左侧(0)
        is(7.U) { newPLRU := Cat(0.U(1.W), b5, b4, b3, 0.U(1.W), b1, 0.U(1.W)) }
      }
      newPLRU
    }

    def getVictim(plru: UInt): UInt = {
      val b0 = plru(0)
      val b1 = plru(1)
      val b2 = plru(2)
      val b3 = plru(3)
      val b4 = plru(4)
      val b5 = plru(5)
      val b6 = plru(6)

      // 树形路由逻辑：0 走上面/左边分支，1 走下面/右边分支
      Mux(!b0,
        Mux(!b1,
          Mux(!b3, 0.U, 1.U),
          Mux(!b4, 2.U, 3.U)
        ),
        Mux(!b2,
          Mux(!b5, 4.U, 5.U),
          Mux(!b6, 6.U, 7.U)
        )
      )
    }

    when(io.touch.valid) {
      plruTree(io.touch.idx) := updatePLRU(plruTree(io.touch.idx), io.touch.way)
    }

    victimResp := getVictim(plruTree(victimIdxReg))

    when(io.flush.valid) {
      plruTree(io.flush.idx) := 0.U(7.W)
    }
  }

  io.victim.resp := victimResp //Mux(victimReqReg, victimResp, 0.U)
}




class CacheReplacerD(implicit p: Parameters) extends NSModule {

  val io = IO(new Bundle {
    val touch  = Flipped(new victimChange)
    val victim = Flipped(new victimRead)
    val flush  = new Bundle {
      val valid = Input(Bool())
      val idx   = Input(UInt(idxBitsD.W))
    }
  })

  // Victim read is intentionally split into two stages:
  // 1) latch req/idx, 2) read replacement state combinationally from the
  //    registered index so the response is still available in the next cycle.
  val victimReqReg = RegInit(false.B)
  val victimIdxReg = RegInit(0.U(idxBitsD.W))
  val victimResp = WireDefault(0.U(wayBitsD.W))

  victimReqReg := io.victim.req
  when(io.victim.req) {
    victimIdxReg := io.victim.idx
  }

  if (nWaysD == 2) {
    // ════════════════════════════════════════════════════════
    //  2路组相联：1-bit 真 LRU
    // ════════════════════════════════════════════════════════
    val lastUsed = RegInit(VecInit(Seq.fill(nSetsD)(0.U(1.W))))

    when(io.touch.valid) {
      lastUsed(io.touch.idx) := io.touch.way
    }

    victimResp := ~lastUsed(victimIdxReg)

    when(io.flush.valid) {
      lastUsed(io.flush.idx) := 0.U(1.W)
    }

  } else if (nWaysD == 4) {
    // ════════════════════════════════════════════════════════
    //  4路组相联：3-bit PLRU 伪二叉树
    // ════════════════════════════════════════════════════════
    val plruTree = RegInit(VecInit(Seq.fill(nSetsD)(0.U(3.W))))

    def updatePLRU(oldPLRU: UInt, way: UInt): UInt = {
      val newPLRU = WireDefault(0.U(3.W))
      switch(way) {
        is(0.U) { newPLRU := Cat(oldPLRU(2), 1.U(1.W), 1.U(1.W)) }
        is(1.U) { newPLRU := Cat(oldPLRU(2), 0.U(1.W), 1.U(1.W)) }
        is(2.U) { newPLRU := Cat(1.U(1.W), oldPLRU(1), 0.U(1.W)) }
        is(3.U) { newPLRU := Cat(0.U(1.W), oldPLRU(1), 0.U(1.W)) }
      }
      newPLRU
    }

    def getVictim(plru: UInt): UInt = {
      val plru0 = plru(0)
      val plru1 = plru(1)
      val plru2 = plru(2)
      Mux(!plru0, Mux(!plru1, 0.U, 1.U), Mux(!plru2, 2.U, 3.U))
    }

    when(io.touch.valid) {
      plruTree(io.touch.idx) := updatePLRU(plruTree(io.touch.idx), io.touch.way)
    }

    victimResp := getVictim(plruTree(victimIdxReg))

    when(io.flush.valid) {
      plruTree(io.flush.idx) := 0.U(3.W)
    }

  } else if (nWaysD == 8) {
    // ════════════════════════════════════════════════════════
    //  8路组相联：7-bit PLRU 伪二叉树
    //
    //  树结构（7个节点，控制8个叶子）：
    //                       bit0
    //                    /        \
    //               bit1            bit2
    //              /    \          /    \
    //          bit3      bit4  bit5      bit6
    //          / \       / \   / \       / \
    //         w0 w1     w2 w3 w4 w5     w6 w7
    //
    //  规则：
    //  - bit = 0 表示指针朝向左子树（左侧更老）
    //  - bit = 1 表示指针朝向右子树（右侧更老）
    //  - Victim：沿着指针一路向下，走到哪里就是被替换的路。
    //  - Touch：访问某路时，该路径上的所有节点必须反转，指向**远离**该路的分支。
    // ════════════════════════════════════════════════════════
    val plruTree = RegInit(VecInit(Seq.fill(nSetsD)(0.U(7.W))))

    def updatePLRU(oldPLRU: UInt, way: UInt): UInt = {
      val newPLRU = WireDefault(0.U(7.W))
      // 提取旧状态的各个 bit，方便重新组合
      val b6 = oldPLRU(6)
      val b5 = oldPLRU(5)
      val b4 = oldPLRU(4)
      val b3 = oldPLRU(3)
      val b2 = oldPLRU(2)
      val b1 = oldPLRU(1)
      val b0 = oldPLRU(0)

      // Cat 拼接顺序：Cat(MSB, ..., LSB) -> Cat(b6, b5, b4, b3, b2, b1, b0)
      switch(way) {
        // 访问 Way0 (左-左-左): 保护左侧，将 b0, b1, b3 指向右侧(1)
        is(0.U) { newPLRU := Cat(b6, b5, b4, 1.U(1.W), b2, 1.U(1.W), 1.U(1.W)) }
        // 访问 Way1 (左-左-右): 保护 Way1，将 b0, b1 指向右侧(1)，b3 指向左侧(0)
        is(1.U) { newPLRU := Cat(b6, b5, b4, 0.U(1.W), b2, 1.U(1.W), 1.U(1.W)) }
        // 访问 Way2 (左-右-左): 将 b0 指向右侧(1)，b1 指向左侧(0)，b4 指向右侧(1)
        is(2.U) { newPLRU := Cat(b6, b5, 1.U(1.W), b3, b2, 0.U(1.W), 1.U(1.W)) }
        // 访问 Way3 (左-右-右): 将 b0 指向右侧(1)，b1 指向左侧(0)，b4 指向左侧(0)
        is(3.U) { newPLRU := Cat(b6, b5, 0.U(1.W), b3, b2, 0.U(1.W), 1.U(1.W)) }
        // 访问 Way4 (右-左-左): 将 b0 指向左侧(0)，b2 指向右侧(1)，b5 指向右侧(1)
        is(4.U) { newPLRU := Cat(b6, 1.U(1.W), b4, b3, 1.U(1.W), b1, 0.U(1.W)) }
        // 访问 Way5 (右-左-右): 将 b0 指向左侧(0)，b2 指向右侧(1)，b5 指向左侧(0)
        is(5.U) { newPLRU := Cat(b6, 0.U(1.W), b4, b3, 1.U(1.W), b1, 0.U(1.W)) }
        // 访问 Way6 (右-右-左): 将 b0 指向左侧(0)，b2 指向左侧(0)，b6 指向右侧(1)
        is(6.U) { newPLRU := Cat(1.U(1.W), b5, b4, b3, 0.U(1.W), b1, 0.U(1.W)) }
        // 访问 Way7 (右-右-右): 将 b0 指向左侧(0)，b2 指向左侧(0)，b6 指向左侧(0)
        is(7.U) { newPLRU := Cat(0.U(1.W), b5, b4, b3, 0.U(1.W), b1, 0.U(1.W)) }
      }
      newPLRU
    }

    def getVictim(plru: UInt): UInt = {
      val b0 = plru(0)
      val b1 = plru(1)
      val b2 = plru(2)
      val b3 = plru(3)
      val b4 = plru(4)
      val b5 = plru(5)
      val b6 = plru(6)

      // 树形路由逻辑：0 走上面/左边分支，1 走下面/右边分支
      Mux(!b0,
        Mux(!b1,
          Mux(!b3, 0.U, 1.U),
          Mux(!b4, 2.U, 3.U)
        ),
        Mux(!b2,
          Mux(!b5, 4.U, 5.U),
          Mux(!b6, 6.U, 7.U)
        )
      )
    }

    when(io.touch.valid) {
      plruTree(io.touch.idx) := updatePLRU(plruTree(io.touch.idx), io.touch.way)
    }

    victimResp := getVictim(plruTree(victimIdxReg))

    when(io.flush.valid) {
      plruTree(io.flush.idx) := 0.U(7.W)
    }
  }

  io.victim.resp := victimResp //Mux(victimReqReg, victimResp, 0.U)
}
