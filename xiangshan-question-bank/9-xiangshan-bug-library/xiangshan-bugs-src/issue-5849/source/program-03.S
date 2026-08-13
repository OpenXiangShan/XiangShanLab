  li t0, 0xDEADBEEFCAFEBABE
  sd t0, 12(s0)           # cross-16B store (bytes 12..19)
  lw t1, 20(s0)           # non-overlap load from next 16B block
  bnez t1, bug_hit

  li t0, 0x1122334455667788
  sd t0, 12(s0)           # cross-16B store again
  ld t1, 24(s0)           # non-overlap load from next 16B block
  bnez t1, bug_hit
