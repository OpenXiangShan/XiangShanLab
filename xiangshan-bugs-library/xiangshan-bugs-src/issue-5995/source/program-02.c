write_pmaaddr0(pma_napot_4kb((uintptr_t)target_page));
write_pmacfg0((old_pmacfg0 & ~0xffull) | PMA_C | PMA_A_NAPOT | PMA_W | PMA_R);
write_hstatus(old_hstatus & ~(1ull << 8));
asm volatile("fence rw, rw" ::: "memory");

hlvx_value = do_hlvx_wu((uintptr_t)target_page);

if (trap_count == 0) {
  printf("FAIL hlvx bypassed PMA execute permission\n");
  assert(0);
}
if (trap_mcause != 5) {
  printf("FAIL unexpected hlvx trap cause\n");
  assert(0);
}
