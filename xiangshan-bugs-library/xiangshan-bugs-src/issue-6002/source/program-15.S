li      t3, 0xdeadbeef
sw      t3, 16(s0)
fence   rw, rw
...
sd      s2, 0(t2)
lwu     a0, 16(s0)
