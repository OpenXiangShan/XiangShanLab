package minixiangshan.frontend.icache
 
import chisel3._
import chisel3.util._
import minixiangshan.mem.L2cache.L2NativeReadIO
import minixiangshan.config.Parameters
import minixiangshan.config._
import minixiangshan.mmu._
import minixiangshan.config.NSModule
import minixiangshan.config.NSBundle
 
class ICacheMainPipe(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val redirect = Input(Bool())
    // CPU接口
    val cpu_req = Flipped(Decoupled(new Bundle {
      val addr  = UInt(32.W)   // 虚拟地址
    }))
    val icache_resp = Decoupled(new IcacheResp)
    
    // L2 接口替换掉原有的 AXI
    val l2_read     = new L2NativeReadIO(1)
    
    // SRAM接口
    val arrays_read = new ICacheArrayRead
    val array_write = new ICacheArrayWrite
    val victim_read = new victimRead
    val replacer_touch = new victimChange
    // MMU接口
    val mmu = new MMURead
  })

  // ══════════════════════════════════════════════════════════════
  // 根据配置决定是否使用 PIPT
  // ══════════════════════════════════════════════════════════════
  
  if (usePIPT == 1) { 
    // --- S0 阶段：向 MMU 发起地址翻译 ---
    val s1_valid = RegInit(false.B)
    val s1_vaddr = RegInit(0.U(32.W))
    val s1_fire  = Wire(Bool())

    val s1_accepts = !s1_valid || s1_fire || io.redirect
    io.cpu_req.ready := s1_accepts
    
    val s0_fire = io.cpu_req.valid && s1_accepts && !io.redirect

    io.mmu.toMmu.valid := s0_fire
    io.mmu.toMmu.bits.vaddr := io.cpu_req.bits.addr
    io.mmu.fromMmu.ready := true.B

    when (s0_fire) {
      s1_valid := true.B
      s1_vaddr := io.cpu_req.bits.addr
    } .elsewhen (s1_fire || io.redirect) {
      s1_valid := false.B
    }

    // --- S1 阶段：接收 MMU，获取物理地址并向 SRAM Array 发起物理索引读 ---
    val s1_mmu_data    = RegInit(0.U.asTypeOf(new MmuToIcache))
    val s1_mmu_latched = RegInit(false.B)
    val next_mmu_latched = WireDefault(s1_mmu_latched)

    when (s1_valid && io.mmu.fromMmu.valid && !s1_mmu_latched) {
      s1_mmu_data      := io.mmu.fromMmu.bits
      next_mmu_latched := true.B
    }
    when (s1_fire || io.redirect) {
      next_mmu_latched := false.B
    }
    s1_mmu_latched := next_mmu_latched

    val cur_mmu_data = Mux(s1_valid && io.mmu.fromMmu.valid, io.mmu.fromMmu.bits, s1_mmu_data)
    val mmu_ready    = (s1_valid && io.mmu.fromMmu.valid) || s1_mmu_latched

    val s2_valid = RegInit(false.B)
    val s2_fire  = Wire(Bool())
    val s2_accepts = !s2_valid || s2_fire || io.redirect

    s1_fire := s1_valid && mmu_ready && s2_accepts && !io.redirect

    val s2_vaddr       = RegInit(0.U(32.W))
    val s2_mmu_data_reg = RegInit(0.U.asTypeOf(new MmuToIcache))

    // S1 发起 Array Read (使用物理索引 pidx)
    io.arrays_read.req.valid := s1_fire
    io.arrays_read.req.idx   := cur_mmu_data.paddr(blockOffBits + idxBitsI - 1, blockOffBits)

    when (s1_fire) {
      s2_valid       := true.B
      s2_vaddr       := s1_vaddr
      s2_mmu_data_reg := cur_mmu_data
    } .elsewhen (s2_fire || io.redirect) {
      s2_valid := false.B
    }

    // --- S2 阶段：接收 SRAM Array，执行 Tag 匹配判定与状态机触发 ---
    val expect_array_resp = RegNext(s1_fire, false.B)
    val s2_array_data     = RegInit(0.U.asTypeOf(new arrayReadData))
    val s2_array_latched  = RegInit(false.B)
    val next_array_latched = WireDefault(s2_array_latched)

    when (expect_array_resp) {
      s2_array_data      := io.arrays_read.resp.data
      next_array_latched := true.B
    }
    when (s2_fire || io.redirect) {
      next_array_latched := false.B
    }
    s2_array_latched := next_array_latched

    val cur_array_data = Mux(expect_array_resp, io.arrays_read.resp.data, s2_array_data)
    val array_ready    = expect_array_resp || s2_array_latched

    // 命中判定计算 (PIPT: 使用 MMU 中保留的物理地址进行判断)
    val ptag = s2_mmu_data_reg.paddr(31, blockOffBits + idxBitsI)
    val pidx = s2_mmu_data_reg.paddr(blockOffBits + idxBitsI - 1, blockOffBits)

    val hits = Wire(Vec(nWaysI, Bool()))
    for (i <- 0 until nWaysI) {
      hits(i) := cur_array_data.cacheLine(i).has && cur_array_data.cacheLine(i).tag === ptag
    }
    val array_hit = hits.asUInt.orR
    val hit_way   = OHToUInt(hits)
    val array_hit_data = cur_array_data.cacheLine(hit_way).data

    val has_mmu_error = s2_mmu_data_reg.error.getAnyError
    val is_cacheable  = s2_mmu_data_reg.cacheable
    
    val is_direct_hit = array_hit && is_cacheable && !has_mmu_error
    val s2_can_resp_direct = is_direct_hit || has_mmu_error

    // PIPT L2 FSM (状态精简版，去除了 Drain 状态)
    val s_idle :: s_miss_req :: s_miss_wait :: s_miss_write :: s_uncache_req :: s_uncache_wait :: s_done :: Nil = Enum(7)
    
    val state = RegInit(s_idle)
    
    val fsm_paddr      = RegInit(0.U(32.W))
    val fsm_ptag       = RegInit(0.U(tagBitsI.W))
    val fsm_idx        = RegInit(0.U(idxBitsI.W)) // PIPT 使用统一的 physical index
    val fsm_is_uncache = RegInit(false.B)

    val miss_data_buffer    = RegInit(0.U((blockBytes * 8).W))
    val miss_data_valid     = RegInit(false.B)
    val uncache_data_buffer = RegInit(0.U(32.W))
    val uncache_data_valid  = RegInit(false.B)
    val beat_counter        = RegInit(0.U(4.W))

    val fsm_trigger = s2_valid && array_ready && !s2_can_resp_direct && (state === s_idle) && !io.redirect

    when (io.redirect) {
      state := s_idle 
    } .otherwise {
      switch (state) {
        is (s_idle) {
          when (fsm_trigger) {
            state := Mux(!is_cacheable, s_uncache_req, s_miss_req)
            fsm_paddr      := s2_mmu_data_reg.paddr
            fsm_ptag       := ptag
            fsm_idx        := pidx
            fsm_is_uncache := !is_cacheable
          }
        }
        is (s_miss_req) { when (io.l2_read.req.fire) { state := s_miss_wait } }
        is (s_miss_wait) {
          when (io.l2_read.resp.fire && io.l2_read.resp.bits.last) { state := s_miss_write }
        }
        is (s_miss_write) { state := s_done }
        is (s_uncache_req) { when (io.l2_read.req.fire) { state := s_uncache_wait } }
        is (s_uncache_wait) {
          when (io.l2_read.resp.fire && io.l2_read.resp.bits.last) { state := s_done }
        }
        is (s_done) {
          when (io.icache_resp.ready && s2_fire) { state := s_idle }
        }
      }
    }

    // ══ L2 接口驱动与取消机制 ══
    val nativeReqPending = (state === s_miss_req) || (state === s_uncache_req)
    val nativeRespExpected = (state === s_miss_wait) || (state === s_uncache_wait)
    
    io.l2_read.cancel := io.redirect && nativeRespExpected
    
    io.l2_read.req.valid := nativeReqPending && !io.redirect
    io.l2_read.req.bits.id := 0.U
    io.l2_read.req.bits.addr := Mux(state === s_miss_req, Cat(fsm_ptag, fsm_idx, 0.U(blockOffBits.W)), fsm_paddr)
    io.l2_read.req.bits.size := 2.U
    io.l2_read.req.bits.uncache := state === s_uncache_req

    // Redirect affects architectural consumption, but the native return channel
    // keeps draining so frontend flush cannot enter L2 scheduling combinationally.
    io.l2_read.resp.ready := nativeRespExpected && (io.l2_read.resp.bits.id === 0.U)

    // L2 读响应接收
    when (state === s_miss_wait && io.l2_read.resp.fire) {
      when(io.l2_read.resp.bits.fullLine) {
        miss_data_buffer := io.l2_read.resp.bits.data
      }.otherwise {
        val data_offset = beat_counter * 32.U
        miss_data_buffer := miss_data_buffer | (io.l2_read.resp.bits.data(31, 0) << data_offset)
        beat_counter := beat_counter + 1.U
      }
      when (io.l2_read.resp.bits.last) {
        miss_data_valid := true.B
        beat_counter := 0.U
      }
    }

    when (state === s_uncache_wait && io.l2_read.resp.fire) {
      uncache_data_buffer := io.l2_read.resp.bits.data(31, 0)
      uncache_data_valid := true.B
    }

    // 数据清理
    when (s2_fire || io.redirect) {
      miss_data_valid    := false.B
      uncache_data_valid := false.B
      miss_data_buffer   := 0.U
      beat_counter       := 0.U
    }

    // PIPT S2 数据装配与 CPU 输出
    val is_done_uncache = (state === s_done) && fsm_is_uncache
    val resp_data = Mux(state === s_done, 
                        Mux(is_done_uncache, uncache_data_buffer, miss_data_buffer),
                        array_hit_data)

    val word_offset = Wire(UInt(5.W))
    word_offset :=  s2_vaddr(blockOffBits-1, 2)
    val out_instrs  = Wire(Vec(fetchWidth, UInt(32.W)))
    val out_valids  = Wire(Vec(fetchWidth, Bool()))
    diffDontTouch(out_valids)

    for (i <- 0 until fetchWidth) {
      val word_off_i = word_offset + i.U
      val bit_off = word_off_i * 32.U
      out_instrs(i) := (resp_data >> bit_off)(31, 0)
      out_valids(i) := word_off_i < (blockBytes/4).U
    }

    val out_uncached = is_done_uncache || (!is_cacheable && !has_mmu_error && state === s_done)
    when (is_done_uncache || has_mmu_error) {
      out_instrs(0) := Mux(has_mmu_error, 0.U, uncache_data_buffer(31, 0))
      out_valids(0) := true.B
      for (i <- 1 until fetchWidth) {
        out_instrs(i) := 0.U
        out_valids(i) := false.B
      }
    }

    val s2_ready_to_fire = s2_valid && array_ready && (s2_can_resp_direct || state === s_done)
    s2_fire := s2_ready_to_fire && io.icache_resp.ready && !io.redirect

    io.icache_resp.valid           := s2_ready_to_fire
    io.icache_resp.bits.instrs     := out_instrs
    io.icache_resp.bits.instvalids := out_valids
    diffDontTouch(io.icache_resp.bits.instvalids)
    io.icache_resp.bits.addr       := s2_vaddr
    io.icache_resp.bits.uncached   := out_uncached
    io.icache_resp.bits.mmu_error  := s2_mmu_data_reg.error

    // PIPT 周边模块控制
    val miss_touch = (state === s_miss_write) && miss_data_valid
    val hit_touch  = s2_valid && array_ready && is_direct_hit && s2_fire

    io.replacer_touch.valid := hit_touch || miss_touch
    io.replacer_touch.idx   := Mux(miss_touch, fsm_idx, pidx)
    io.replacer_touch.way   := Mux(miss_touch, io.victim_read.resp, hit_way)

    io.victim_read.req := (state === s_miss_req) || (state === s_miss_wait) || (state === s_miss_write)
    io.victim_read.idx := fsm_idx

    io.array_write.valid := (state === s_miss_write) && miss_data_valid
    io.array_write.idx   := fsm_idx
    io.array_write.tag   := fsm_ptag
    io.array_write.data  := miss_data_buffer
    io.array_write.way   := io.victim_read.resp

    // 性能计数器
    val perf_hit       = RegInit(0.U(32.W))
    val perf_miss      = RegInit(0.U(32.W))
    val perf_uncached  = RegInit(0.U(32.W))
    val perf_mmu_error = RegInit(0.U(32.W))
     
    when(s2_fire) {
      when(has_mmu_error) { 
        perf_mmu_error := perf_mmu_error + 1.U 
      }.elsewhen(state === s_done) {
        when(fsm_is_uncache) { perf_uncached := perf_uncached + 1.U }
      }.elsewhen(is_direct_hit) {
        perf_hit := perf_hit + 1.U
      }
    }
    when(state === s_miss_write) {
      perf_miss := perf_miss + 1.U
    }

  }else{

    // ══════════════════════════════════════════════════════════════
    // VIPT 2-Stage Pipeline (合并 L2 接口替换)
    // ══════════════════════════════════════════════════════════════

    val s1_valid = RegInit(false.B)
    val s1_vaddr = RegInit(0.U(32.W))
    val s1_fire  = Wire(Bool())

    val s1_accepts = !s1_valid || s1_fire || io.redirect
    io.cpu_req.ready := s1_accepts
    
    val s0_fire = io.cpu_req.valid && s1_accepts && !io.redirect

    io.arrays_read.req.valid := s0_fire
    io.arrays_read.req.idx   := io.cpu_req.bits.addr(blockOffBits + idxBitsI - 1, blockOffBits)

    io.mmu.toMmu.valid := s0_fire
    io.mmu.toMmu.bits.vaddr := io.cpu_req.bits.addr
    io.mmu.fromMmu.ready := true.B

    when (s0_fire) {
      s1_valid := true.B
      s1_vaddr := io.cpu_req.bits.addr
    } .elsewhen (s1_fire || io.redirect) {
      s1_valid := false.B
    }
    
    // --- Array 与 MMU 捕获机制保持一致 ---
    val expect_array_resp = RegNext(s0_fire, false.B)
    val s1_array_data     = RegInit(0.U.asTypeOf(new arrayReadData))
    val s1_array_latched  = RegInit(false.B)
    val next_array_latched = WireDefault(s1_array_latched)

    when (expect_array_resp) {
      s1_array_data      := io.arrays_read.resp.data
      next_array_latched := true.B
    }
    when (s1_fire || io.redirect) { next_array_latched := false.B }
    s1_array_latched := next_array_latched
    
    val cur_array_data = Mux(expect_array_resp, io.arrays_read.resp.data, s1_array_data)
    val array_ready    = expect_array_resp || s1_array_latched

    val s1_mmu_data    = RegInit(0.U.asTypeOf(new MmuToIcache))
    val s1_mmu_latched = RegInit(false.B)
    val next_mmu_latched = WireDefault(s1_mmu_latched)

    when (s1_valid && io.mmu.fromMmu.valid && !s1_mmu_latched) {
      s1_mmu_data      := io.mmu.fromMmu.bits
      next_mmu_latched := true.B
    }
    when (s1_fire || io.redirect) { next_mmu_latched := false.B }
    s1_mmu_latched := next_mmu_latched

    val cur_mmu_data = Mux(s1_valid && io.mmu.fromMmu.valid, io.mmu.fromMmu.bits, s1_mmu_data)
    val mmu_ready    = (s1_valid && io.mmu.fromMmu.valid) || s1_mmu_latched

    // VIPT 命中判定
    val ptag = cur_mmu_data.paddr(31, blockOffBits + idxBitsI)
    val vidx = s1_vaddr(blockOffBits + idxBitsI - 1, blockOffBits)

    val hits = Wire(Vec(nWaysI, Bool()))
    for (i <- 0 until nWaysI) {
      hits(i) := cur_array_data.cacheLine(i).has && cur_array_data.cacheLine(i).tag === ptag
    }
    val array_hit = hits.asUInt.orR
    val hit_way   = OHToUInt(hits)
    val array_hit_data = cur_array_data.cacheLine(hit_way).data

    val has_mmu_error = cur_mmu_data.error.getAnyError
    val is_cacheable  = cur_mmu_data.cacheable
    
    val is_direct_hit = array_hit && is_cacheable && !has_mmu_error
    val s1_can_resp_direct = is_direct_hit || has_mmu_error

    // VIPT L2 FSM
    val s_idle :: s_miss_req :: s_miss_wait :: s_miss_write :: s_uncache_req :: s_uncache_wait :: s_done :: Nil = Enum(7)
    
    val state = RegInit(s_idle)
    
    val fsm_paddr      = RegInit(0.U(32.W))
    val fsm_ptag       = RegInit(0.U(tagBitsI.W))
    val fsm_vidx       = RegInit(0.U(idxBitsI.W))
    val fsm_is_uncache = RegInit(false.B)

    val miss_data_buffer    = RegInit(0.U((blockBytes * 8).W))
    val miss_data_valid     = RegInit(false.B)
    val uncache_data_buffer = RegInit(0.U(32.W))
    val uncache_data_valid  = RegInit(false.B)
    val beat_counter        = RegInit(0.U(4.W))

    val fsm_trigger = s1_valid && array_ready && mmu_ready && !s1_can_resp_direct && (state === s_idle) && !io.redirect

    when (io.redirect) {
      state := s_idle 
    } .otherwise {
      switch (state) {
        is (s_idle) {
          when (fsm_trigger) {
            state := Mux(!is_cacheable, s_uncache_req, s_miss_req)
            fsm_paddr      := cur_mmu_data.paddr
            fsm_ptag       := ptag
            fsm_vidx       := vidx
            fsm_is_uncache := !is_cacheable
          }
        }
        is (s_miss_req) { when (io.l2_read.req.fire) { state := s_miss_wait } }
        is (s_miss_wait) {
          when (io.l2_read.resp.fire && io.l2_read.resp.bits.last) { state := s_miss_write }
        }
        is (s_miss_write) { state := s_done }
        is (s_uncache_req) { when (io.l2_read.req.fire) { state := s_uncache_wait } }
        is (s_uncache_wait) {
          when (io.l2_read.resp.fire && io.l2_read.resp.bits.last) { state := s_done }
        }
        is (s_done) {
          when (io.icache_resp.ready && s1_fire) { state := s_idle }
        }
      }
    }

    // ══ L2 接口驱动与取消机制 ══
    val nativeReqPending = (state === s_miss_req) || (state === s_uncache_req)
    val nativeRespExpected = (state === s_miss_wait) || (state === s_uncache_wait)
    
    io.l2_read.cancel := io.redirect && nativeRespExpected
    
    io.l2_read.req.valid := nativeReqPending && !io.redirect
    io.l2_read.req.bits.id := 0.U
    io.l2_read.req.bits.addr := Mux(state === s_miss_req, Cat(fsm_ptag, fsm_vidx, 0.U(blockOffBits.W)), fsm_paddr)
    io.l2_read.req.bits.size := 2.U
    io.l2_read.req.bits.uncache := state === s_uncache_req

    // Redirect affects architectural consumption, but the native return channel
    // keeps draining so frontend flush cannot enter L2 scheduling combinationally.
    io.l2_read.resp.ready := nativeRespExpected && (io.l2_read.resp.bits.id === 0.U)

    // L2 读响应接收
    when (state === s_miss_wait && io.l2_read.resp.fire) {
      when(io.l2_read.resp.bits.fullLine) {
        miss_data_buffer := io.l2_read.resp.bits.data
      }.otherwise {
        val data_offset = beat_counter * 32.U
        miss_data_buffer := miss_data_buffer | (io.l2_read.resp.bits.data(31, 0) << data_offset)
        beat_counter := beat_counter + 1.U
      }
      when (io.l2_read.resp.bits.last) {
        miss_data_valid := true.B
        beat_counter := 0.U
      }
    }

    when (state === s_uncache_wait && io.l2_read.resp.fire) {
      uncache_data_buffer := io.l2_read.resp.bits.data(31, 0)
      uncache_data_valid := true.B
    }

    when (s1_fire || io.redirect) {
      miss_data_valid    := false.B
      uncache_data_valid := false.B
      miss_data_buffer   := 0.U
      beat_counter       := 0.U
    }

    // VIPT S1 数据装配与 CPU 输出
    val is_done_uncache = (state === s_done) && fsm_is_uncache
    val resp_data = Mux(state === s_done, 
                        Mux(is_done_uncache, uncache_data_buffer, miss_data_buffer),
                        array_hit_data)

    val word_offset = Wire(UInt(5.W))
    word_offset :=  s1_vaddr(blockOffBits-1, 2)
    val out_instrs  = Wire(Vec(fetchWidth, UInt(32.W)))
    val out_valids  = Wire(Vec(fetchWidth, Bool()))
    diffDontTouch(out_valids)

    for (i <- 0 until fetchWidth) {
      val word_off_i = word_offset + i.U
      val bit_off = word_off_i * 32.U
      out_instrs(i) := (resp_data >> bit_off)(31, 0)
      out_valids(i) := word_off_i < (blockBytes/4).U
    }

    val out_uncached = is_done_uncache || (!is_cacheable && !has_mmu_error && state === s_done)
    when (is_done_uncache || has_mmu_error) {
      out_instrs(0) := Mux(has_mmu_error, 0.U, uncache_data_buffer(31, 0))
      out_valids(0) := true.B
      for (i <- 1 until fetchWidth) {
        out_instrs(i) := 0.U
        out_valids(i) := false.B
      }
    }

    val s1_ready_to_fire = s1_valid && array_ready && mmu_ready && (s1_can_resp_direct || state === s_done)
    s1_fire := s1_ready_to_fire && io.icache_resp.ready

    io.icache_resp.valid           := s1_ready_to_fire
    io.icache_resp.bits.instrs     := out_instrs
    io.icache_resp.bits.instvalids := out_valids
    diffDontTouch(io.icache_resp.bits.instvalids)
    io.icache_resp.bits.addr       := s1_vaddr
    io.icache_resp.bits.uncached   := out_uncached
    io.icache_resp.bits.mmu_error  := cur_mmu_data.error

    // VIPT 周边模块控制
    val miss_touch = (state === s_miss_write) && miss_data_valid
    val hit_touch  = s1_valid && array_ready && mmu_ready && is_direct_hit && s1_fire

    io.replacer_touch.valid := hit_touch || miss_touch
    io.replacer_touch.idx   := Mux(miss_touch, fsm_vidx, vidx)
    io.replacer_touch.way   := Mux(miss_touch, io.victim_read.resp, hit_way)

    io.victim_read.req := (state === s_miss_req) || (state === s_miss_wait) || (state === s_miss_write)
    io.victim_read.idx := fsm_vidx

    io.array_write.valid := (state === s_miss_write) && miss_data_valid
    io.array_write.idx   := fsm_vidx
    io.array_write.tag   := fsm_ptag
    io.array_write.data  := miss_data_buffer
    io.array_write.way   := io.victim_read.resp

    // 性能计数器
    val perf_hit       = RegInit(0.U(32.W))
    val perf_miss      = RegInit(0.U(32.W))
    val perf_uncached  = RegInit(0.U(32.W))
    val perf_mmu_error = RegInit(0.U(32.W))
     
    when(s1_fire) {
      when(has_mmu_error) { 
        perf_mmu_error := perf_mmu_error + 1.U 
      }.elsewhen(state === s_done) {
        when(fsm_is_uncache) { perf_uncached := perf_uncached + 1.U }
      }.elsewhen(is_direct_hit) {
        perf_hit := perf_hit + 1.U
      }
    }
    when(state === s_miss_write) {
      perf_miss := perf_miss + 1.U
    }
  }
}