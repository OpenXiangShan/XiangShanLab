li  s3, 0x38022000
li  t0, 5            # or 1
sd  t0, 0x10(s3)     # tag mask register
li  t0, 0xffffffff3  # ese=1, persist=1, comp=tag, all 32 banks
sd  t0, 0(s3)
fence
ld  s4, 0(s2)        # B (case 1) or A (case 2)
sd  zero, 0(s3)      # disarm
