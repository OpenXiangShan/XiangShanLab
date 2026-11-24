// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/src/memory/paddr.c#L303-L314
  #ifdef CONFIG_RV_MPT_CHECK
    if (!isa_mpt_check_permission(addr, len, type, mode)) {
      if (trap_type == MEM_TYPE_WRITE) {
        raise_access_fault(EX_SAF, vaddr);
      } else {
        Log("isa mpt check failed, vaddr=" FMT_WORD ", paddr=" FMT_PADDR ", len=0x%x, type=0x%x, mode=0x%x",
          vaddr, addr, len, type, mode);
        raise_read_access_fault(trap_type, vaddr);
      }
      return false;
    }
  #endif
