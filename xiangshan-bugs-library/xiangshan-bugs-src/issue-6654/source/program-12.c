// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/src/isa/riscv64/instr/rva/amo.c#L120-L123
  // AMO does not support misalign operation
  // So check misalign before real memory access
  void isa_amo_misalign_data_addr_check(vaddr_t vaddr, int len, int type);
  isa_amo_misalign_data_addr_check(*src1, width, type);
