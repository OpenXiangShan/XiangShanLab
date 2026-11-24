# VA 0x40000000 + i*0x1000 -> l0[i]
# l0[0..6] = PPN of `tgt`, D A X W R V;  l0[7] = the same leaf with PTE bit 52 set
# (S-mode, Sv39, PMP allows everything)
  li  a4, 0x40000000;  ld t4, 0(a4);  sd t1, 8(a4)
  li  a4, 0x40001000;  ld t4, 0(a4);  sd t1, 8(a4)
  ...                                                # the same for 0x40002000 .. 0x40005000
  li  a4, 0x40007000;  ld t4, 0(a4)                  # must raise a load access fault
