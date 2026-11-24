// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/src/isa/riscv64/system/mmu.c#L1509
      mpt_perm = (data>>(range_off[level]*3)) & 0x7;
