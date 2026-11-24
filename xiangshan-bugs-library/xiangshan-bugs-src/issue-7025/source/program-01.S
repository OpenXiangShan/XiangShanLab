# sp = 0x80040000; f0 = 0x123456789abcdef0; FS enabled
# Observation CSR writes and the warmup-control branch are omitted here.
fsd   f0, 0(sp)
ld    a1, 0(sp)       # Observed: 0x123456789abcdef0
fence rw, rw
ld    a2, 0(sp)       # Observed: 0x8877665544332211
