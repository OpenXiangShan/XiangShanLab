package minixiangshan.frontend

import chisel3._
import chisel3.util._
import minixiangshan.config.Parameters
import minixiangshan.config._
import minixiangshan.config.NSModule
import minixiangshan.config.NSBundle
import minixiangshan.util.SimpleBlockRAM
import scala.util.Random

class BPU(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    // 预测接口
    val predictReq   = Input(new BpuPredictReq)
    val predictResp  = Output(new BpuPredictResp)
    val predictFire  = Input(Bool())   

    // BPU更新接口
    val update_pd    = Input(new BpuUpdateReq)   
    val update_br    = Input(new BpuUpdateReq)   

    // RAS接口 (已弃用，保留以防止顶层连线报错)
    val rasRestore     = Input(Bool())
    val rasRestoreTop  = Input(UInt(log2Ceil(rasSize).W))
  })

  // 提取公共变量：位宽计算
  val btbEntryNoValidWidth = 0.U.asTypeOf(new BTBEntryNoValid).getWidth
  val mergedWidth = btbEntryNoValidWidth + 2 // 将 BTB entry 与 2-bit PHT counter 拼接

  if(useNewBPU == 0){

    // ==================== 辅助计算 ====================
    val fetchBlockBitsValue = log2Ceil(fetchWidth) + 2
  
    val rnd = new Random()
    val randomBtbInit = Seq.fill(BtbPhtSize)(BigInt(btbEntryNoValidWidth, rnd))
  
    // ==================== 实例化双体 整合 BRAM ====================
    // Bank0: 存储当前对齐块 (Block N) 的预测信息
    val validArray0 = RegInit(VecInit(Seq.fill(BtbPhtSize)(false.B)))
    // 合并 BTB 和 PHT 为一个 btbPhtMem0
    val btbPhtMem0 = Module(new SimpleBlockRAM(depth = BtbPhtSize, width = mergedWidth, readLatency = 1))
  
    // Bank1: 存储下一个对齐块 (Block N+1) 的预测信息
    val validArray1 = RegInit(VecInit(Seq.fill(BtbPhtSize)(false.B)))
    // 合并 BTB 和 PHT 为一个 btbPhtMem1
    val btbPhtMem1 = Module(new SimpleBlockRAM(depth = BtbPhtSize, width = mergedWidth, readLatency = 1))
  
    // ==================== 读请求逻辑 (提前一拍使用 nextPC 索引) ====================
    val readBlockIdx = io.predictReq.nextPC(btbIndexBits + fetchBlockBitsValue - 1, fetchBlockBitsValue)
  
    // 共享同一个读使能和读地址
    btbPhtMem0.io.rd_en   := io.predictReq.rdBpu
    btbPhtMem0.io.rd_addr := readBlockIdx
  
    btbPhtMem1.io.rd_en   := io.predictReq.rdBpu
    btbPhtMem1.io.rd_addr := readBlockIdx
  
    // ==================== 预测命中与优先级逻辑 (当前周期使用 pc 校验) ====================
    val fetchOffset = io.predictReq.pc(fetchBlockBitsValue - 1, 2)
    
    val tag0 = io.predictReq.pc(31, btbIndexBits + fetchBlockBitsValue)
    val nextBlockBase = Cat(io.predictReq.pc(31, fetchBlockBitsValue) + 1.U, 0.U(fetchBlockBitsValue.W))
    val tag1 = nextBlockBase(31, btbIndexBits + fetchBlockBitsValue)
  
    val readIdxReg = RegEnable(readBlockIdx, 0.U(btbIndexBits.W), io.predictReq.rdBpu)
  
    // 解析 Bank0 (当前块) 数据：将合并的位宽分离
    val btbEntry0  = btbPhtMem0.io.rd_data(mergedWidth - 1, 2).asTypeOf(new BTBEntryNoValid)
    val phtCounter0= btbPhtMem0.io.rd_data(1, 0)
    val phtTaken0  = phtCounter0(1)
    
    val btbHit0    = validArray0(readIdxReg) && (btbEntry0.tag === tag0) && (btbEntry0.offset >= fetchOffset)
    val predTaken0 = btbHit0 && (btbEntry0.isJalr || btbEntry0.isJal || phtTaken0)
  
    // 解析 Bank1 (下一块) 数据：将合并的位宽分离
    val btbEntry1  = btbPhtMem1.io.rd_data(mergedWidth - 1, 2).asTypeOf(new BTBEntryNoValid)
    val phtCounter1= btbPhtMem1.io.rd_data(1, 0)
    val phtTaken1  = phtCounter1(1)
    
    val btbHit1    = validArray1(readIdxReg) && (btbEntry1.tag === tag1) && (btbEntry1.offset < fetchOffset) && !io.predictReq.crossLine
    val predTaken1 = btbHit1 && (btbEntry1.isJalr || btbEntry1.isJal || phtTaken1)
  
    // ==================== 仲裁与输出生成 ====================
    val finalTaken  = predTaken0 || predTaken1
    val finalTarget = Mux(predTaken0, btbEntry0.target, btbEntry1.target)
  
    val offset0_out = btbEntry0.offset - fetchOffset
    val offset1_out = btbEntry1.offset + fetchWidth.U - fetchOffset
    val finalOffset = Mux(predTaken0, offset0_out, offset1_out)
  
    io.predictResp.taken       := finalTaken
    io.predictResp.takenOffset := finalOffset
    io.predictResp.target      := finalTarget
  
    io.predictResp.meta.btbHit     := btbHit0 || btbHit1
    io.predictResp.meta.valid      := Mux(btbHit1 , validArray1(readIdxReg)  , validArray0(readIdxReg) )
    io.predictResp.meta.btbIsJalr  := Mux(btbHit1 , btbEntry1.isJalr         , btbEntry0.isJalr        )
    io.predictResp.meta.btbIsJal   := Mux(btbHit1 , btbEntry1.isJal          , btbEntry0.isJal         )
    io.predictResp.meta.btbIsCall  := Mux(btbHit1 , btbEntry1.isCall         , btbEntry0.isCall        )
    io.predictResp.meta.btbIsRet   := Mux(btbHit1 , btbEntry1.isRet          , btbEntry0.isRet         )
    io.predictResp.meta.btbOffset  := Mux(btbHit1 , btbEntry1.offset         , btbEntry0.offset        )
    io.predictResp.meta.phtCounter := Mux(btbHit1 , phtCounter1              , phtCounter0             )
    io.predictResp.meta.rasTop     := 0.U 
    io.predictResp.meta.predTaken  := finalTaken
    io.predictResp.meta.predTarget := finalTarget
  
    // ==================== BPU 更新逻辑 (双写核心) ====================
    val doUpdate = io.update_br.valid || io.update_pd.valid
    val update   = Mux(io.update_br.valid, io.update_br, io.update_pd)
  
    // 默认关闭所有写使能
    btbPhtMem0.io.wr_en := false.B; btbPhtMem0.io.wr_addr := 0.U; btbPhtMem0.io.wr_data := 0.U
    btbPhtMem1.io.wr_en := false.B; btbPhtMem1.io.wr_addr := 0.U; btbPhtMem1.io.wr_data := 0.U
  
    when(doUpdate) {
      val updateBlockIdx = update.pc(btbIndexBits + fetchBlockBitsValue - 1, fetchBlockBitsValue)
      val updateTag      = update.pc(31, btbIndexBits + fetchBlockBitsValue)
  
      val newEntry = Wire(new BTBEntryNoValid)
      newEntry.tag    := updateTag
      newEntry.target := update.target
      newEntry.isJalr := update.isJalr
      newEntry.isJal  := update.isJal
      newEntry.isCall := update.isCall
      newEntry.isRet  := update.isRet
      newEntry.offset := update.offset 
      
      val oldCounter  = update.oldPhtCounter 
      val nextCounter = WireDefault(oldCounter)
      when(!update.validEntry){
        nextCounter := 2.U
      }.elsewhen(update.taken && oldCounter =/= 3.U) {
        nextCounter := oldCounter + 1.U
      }.elsewhen(!update.taken && oldCounter =/= 0.U) {
        nextCounter := oldCounter - 1.U
      }

      // 组装要写入的合并数据
      val mergedWriteData = Cat(newEntry.asUInt, nextCounter)
  
      // --- 双发写入逻辑 ---
      // 1. 写入 Bank0
      btbPhtMem0.io.wr_en   := true.B
      btbPhtMem0.io.wr_addr := updateBlockIdx
      btbPhtMem0.io.wr_data := mergedWriteData
      validArray0(updateBlockIdx) := update.validEntry
  
      // 2. 写入 Bank1
      val updateBlockIdx_minus_1 = updateBlockIdx - 1.U
  
      btbPhtMem1.io.wr_en   := true.B
      btbPhtMem1.io.wr_addr := updateBlockIdx_minus_1
      btbPhtMem1.io.wr_data := mergedWriteData
      validArray1(updateBlockIdx_minus_1) := update.validEntry
    }

  } else {
    
    // ==================== 辅助计算 ====================
    val cacheLineBits = log2Ceil(blockBytes)
   
    // ==================== 实例化 4 个 整合 Bank ====================
    val validArrays = Seq.fill(4)(RegInit(VecInit(Seq.fill(BtbPhtSize)(false.B))))
    // 统一替换为合并后的 btbPhtMems
    val btbPhtMems = Seq.tabulate(4)(_ =>
      Module(new SimpleBlockRAM(depth = BtbPhtSize, width = mergedWidth, readLatency = 1)))
   
    // ==================== 读请求逻辑 ====================
    val nextPC = io.predictReq.nextPC
   
    val bankReadAddr = Wire(Vec(4, UInt(btbIndexBits.W)))
   
    for (b <- 0 until 4) {
      bankReadAddr(b) := 0.U
    }
   
    for (i <- 0 until 4) {
      val pc_i   = nextPC + (i * 4).U
      val bank   = pc_i(3, 2)
      val btbIdx = pc_i(btbIndexBits + 3, 4) // 这里统一使用 btbIndexBits 作为共用索引宽度
   
      when(bank === 0.U) {
        bankReadAddr(0) := btbIdx
      } .elsewhen(bank === 1.U) {
        bankReadAddr(1) := btbIdx
      } .elsewhen(bank === 2.U) {
        bankReadAddr(2) := btbIdx
      } .otherwise {
        bankReadAddr(3) := btbIdx
      }
    }
   
    // 发送读请求到 4 个 Bank
    for (b <- 0 until 4) {
      btbPhtMems(b).io.rd_en   := io.predictReq.rdBpu
      btbPhtMems(b).io.rd_addr := bankReadAddr(b)
    }
   
    // ==================== 预测处理逻辑 ====================
    val curPC = io.predictReq.pc
   
    val btbEntries   = Wire(Vec(4, new BTBEntryNoValid))
    val phtCounters  = Wire(Vec(4, UInt(2.W)))
    val entryValids  = Wire(Vec(4, Bool()))
    val btbHits      = Wire(Vec(4, Bool()))
    val predTakens   = Wire(Vec(4, Bool()))
    val crossLines   = Wire(Vec(4, Bool()))
   
    for (i <- 0 until 4) {
      val pc_i   = curPC + (i * 4).U
      val bank   = pc_i(3, 2)
      val btbIdx = pc_i(btbIndexBits + 3, 4)
      val tag    = pc_i(31, btbIndexBits + 4)
   
      // 读取合并数据并拆解
      val mergedData = MuxLookup(bank, 0.U, (0 until 4).map(b => b.U -> btbPhtMems(b).io.rd_data))
      val valid      = MuxLookup(bank, false.B, (0 until 4).map(b => b.U -> validArrays(b)(btbIdx)))
   
      btbEntries(i)  := mergedData(mergedWidth - 1, 2).asTypeOf(new BTBEntryNoValid)
      phtCounters(i) := mergedData(1, 0)
      entryValids(i) := valid
   
      crossLines(i) := pc_i(31, cacheLineBits) =/= curPC(31, cacheLineBits)
      btbHits(i) := !crossLines(i) && valid && (btbEntries(i).tag === tag)
   
      val phtTaken = phtCounters(i)(1)
      predTakens(i) := btbHits(i) && (btbEntries(i).isJalr || btbEntries(i).isJal || phtTaken)
    }
   
    // ==================== 仲裁与输出 ====================
    val finalTaken  = predTakens.asUInt.orR
    val finalTarget = MuxCase(0.U, (0 until 4).map(i => predTakens(i) -> btbEntries(i).target))
    val finalOffset = MuxCase(0.U, (0 until 4).map(i => predTakens(i) -> i.U))
    val finalHit    = btbHits.asUInt.orR
   
    val selectedPos = MuxCase(0.U, (0 until 4).map(i => btbHits(i) -> i.U))
   
    io.predictResp.taken       := finalTaken
    io.predictResp.takenOffset := finalOffset
    io.predictResp.target      := finalTarget
   
    io.predictResp.meta.btbHit     := finalHit
    io.predictResp.meta.valid      := entryValids(selectedPos)
    io.predictResp.meta.btbIsJalr  := btbEntries(selectedPos).isJalr
    io.predictResp.meta.btbIsJal   := btbEntries(selectedPos).isJal
    io.predictResp.meta.btbIsCall  := btbEntries(selectedPos).isCall
    io.predictResp.meta.btbIsRet   := btbEntries(selectedPos).isRet
    io.predictResp.meta.btbOffset  := btbEntries(selectedPos).offset
    io.predictResp.meta.phtCounter := phtCounters(selectedPos)
    io.predictResp.meta.rasTop     := 0.U
    io.predictResp.meta.predTaken  := finalTaken
    io.predictResp.meta.predTarget := finalTarget
   
    // ==================== BPU 更新逻辑 ====================
    val doUpdate = io.update_br.valid || io.update_pd.valid
    val update   = Mux(io.update_br.valid, io.update_br, io.update_pd)
   
    for (b <- 0 until 4) {
      btbPhtMems(b).io.wr_en   := false.B
      btbPhtMems(b).io.wr_addr := 0.U
      btbPhtMems(b).io.wr_data := 0.U
    }
   
    when(doUpdate) {
      val updatePC     = update.pc
      val updateBank   = updatePC(3, 2)
      val updateBtbIdx = updatePC(btbIndexBits + 3, 4)
      val updateTag    = updatePC(31, btbIndexBits + 4)
   
      val newEntry = Wire(new BTBEntryNoValid)
      newEntry.tag    := updateTag
      newEntry.target := update.target
      newEntry.isJalr := update.isJalr
      newEntry.isJal  := update.isJal
      newEntry.isCall := update.isCall
      newEntry.isRet  := update.isRet
      newEntry.offset := update.offset
   
      val oldCounter  = update.oldPhtCounter
      val nextCounter = WireDefault(oldCounter)
      when(!update.validEntry) {
        nextCounter := 2.U
      } .elsewhen(update.taken && oldCounter =/= 3.U) {
        nextCounter := oldCounter + 1.U
      } .elsewhen(!update.taken && oldCounter =/= 0.U) {
        nextCounter := oldCounter - 1.U
      }
      
      val mergedWriteData = Cat(newEntry.asUInt, nextCounter)
   
      for (b <- 0 until 4) {
        when(updateBank === b.U) {
          btbPhtMems(b).io.wr_en   := true.B
          btbPhtMems(b).io.wr_addr := updateBtbIdx
          btbPhtMems(b).io.wr_data := mergedWriteData
          validArrays(b)(updateBtbIdx) := update.validEntry
        }
      }
    }
  }
}