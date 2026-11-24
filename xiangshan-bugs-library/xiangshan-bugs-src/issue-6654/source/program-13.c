// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/src/isa/riscv64/system/mmu.c#L1068-L1078
// AMO access currently does not support hardware misalignment.
void isa_amo_misalign_data_addr_check(vaddr_t vaddr, int len, int type) {
  if (unlikely((vaddr & (len - 1)) != 0)) {
    Logm("addr misaligned happened: vaddr:%lx len:%d type:%d pc:%lx", vaddr, len, type, cpu.pc);
    if (ISDEF(CONFIG_AMO_AC_SOFT)) {
      int ex = cpu.amo || type == MEM_TYPE_WRITE ? EX_SAM : EX_LAM;
      cpu.trapInfo.tval = vaddr;
      longjmp_exception(ex);
    }
  }
}
