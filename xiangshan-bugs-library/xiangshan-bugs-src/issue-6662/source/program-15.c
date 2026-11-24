// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/src/isa/riscv64/system/mmu.c#L1489-L1498
    bool res = ((mpte >> 2) & 0xff)==0 && ((mpte >> 62) & 0x1)==0;
    bool res_l= ((mpte >> 58) & 0x1f)==0;
    uint64_t data = ((mpte << 1) >> 1) >>10;

    if (!v || !res || (l && !res_l)) {
       Log("Mpt pagefault!");
       Logtr("mpte: %04lx, level: %d", (unsigned long)mpte,level);
       Logtr("mpt_base: %04lx, addr: %04lx, mpte_addr: %04lx", (unsigned long)mpt_base, (unsigned long)addr, (unsigned long)mpte_addr);
      return false;
    }
