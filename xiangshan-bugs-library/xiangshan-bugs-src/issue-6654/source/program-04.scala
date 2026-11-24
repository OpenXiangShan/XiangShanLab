// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala#L280-L290
      when (!io.dtlb.resp.bits.miss) {
        when (!addrAligned || triggerDebugMode || triggerBreakpoint) {
          // NOTE: when addrAligned or trigger fire, do not need to wait tlb actually
          // check for miss aligned exceptions, tlb exception are checked next cycle for timing
          // if there are exceptions, no need to execute it
          state := s_finish
          out_valid := true.B
          atom_override_xtval := true.B
        }.otherwise {
          state := s_pm
        }
