_start:
  li   s2, 200                  # iteration count
  li   t0, 0x8010000F           # base; offset 15 -> cross-16B sd
  li   t3, 0xDEADBEEF
  li   t5, 32                   # unique-line stride
.Lloop:
  sd   t3, 0(t0)                # first  cross-16B sd
  sd   t3, 16(t0)               # second cross-16B sd
  add  t0, t0, t5
  addi s2, s2, -1
  bnez s2, .Lloop
