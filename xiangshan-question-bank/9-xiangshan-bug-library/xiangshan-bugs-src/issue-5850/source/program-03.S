  # s0 = pagebuf + 4080 (near page boundary)
  li t0, 160
  sb t0, 0(s0)              # initialize 1 byte

  li t1, 0x44332211
  sw t1, 13(s0)             # cross-page store at offset 4093
  li t2, 0x44332211
  sw t2, 13(s0)             # cross-page store again
  fence rw, rw
