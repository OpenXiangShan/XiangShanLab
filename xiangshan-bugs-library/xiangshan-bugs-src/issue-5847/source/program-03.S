trigger_loop:
  addi  a1, a1, 1

  li    t0, 0xAAAABBBBCCCCDDDD
  sd    t0, 44(s1)            # bytes 44..51, crosses 16B boundary at 48

  li    t2, 0xEEEE
  sd    t2, 192(s1)
  sd    t2, 256(s1)
  sd    t2, 320(s1)
  sd    t2, 384(s1)
  sd    t2, 448(s1)
  sd    t2, 512(s1)
  sd    t2, 576(s1)
  sd    t2, 640(s1)
  sd    t2, 704(s1)
  sd    t2, 768(s1)
  sd    t2, 832(s1)
  sd    t2, 896(s1)
  sd    t2, 960(s1)
  sd    t2, 1024(s1)
  sd    t2, 1088(s1)
  sd    t2, 1152(s1)

  blt   a1, a0, trigger_loop
