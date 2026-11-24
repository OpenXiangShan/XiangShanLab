// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/src/memory/paddr.c#L609
  if (!check_paddr(addr, len, MEM_TYPE_WRITE, MEM_TYPE_WRITE, mode, vaddr)) {
