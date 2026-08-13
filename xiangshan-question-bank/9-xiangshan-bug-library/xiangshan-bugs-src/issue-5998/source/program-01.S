sd   zero, 0(t0)
sd   zero, 8(t0)
sd   zero, 16(t0)
sd   zero, 24(t0)
fence rw, rw

li   t1, 0xaabbccdd
sw   t1, 20(t0)                 # older store: bytes 20..23

li   t2, 0x1122334455667788
sd   t2, 12(t0)                 # younger store: bytes 12..19, crosses the 16B boundary

ld   a0, 16(t0)                 # load bytes 16..23
li   t3, 0xaabbccdd11223344
bne  a0, t3, fail
