// https://github.com/OpenXiangShan/difftest/blob/4c64fe86b17e0564232b52bb3800d9b15c45a3a0/src/test/csrc/difftest/checkers/traps.cpp#L99-L106
  } else {
#ifdef DEBUG_MODE_DIFF
    if (DEBUG_MEM_REGION(true, probe.exceptionPC)) {
      debug_mode_copy(probe.exceptionPC, 4, probe.exceptionInst);
    }
#endif
    proxy->ref_exec(1);
  }
