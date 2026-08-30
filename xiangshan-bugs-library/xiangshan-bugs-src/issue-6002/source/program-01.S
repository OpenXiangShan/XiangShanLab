la      s0, test_buf             # test_buf = 0x80001080
addi    s1, s0, 12               # s1 = 0x8000108c
li      s2, 0x1122334455667788

# Delay store address so the younger load can execute first.
li      t5, 1
divu    t2, s1, t5
divu    t2, t2, t5
divu    t2, t2, t5
divu    t2, t2, t5
divu    t2, t2, t5
divu    t2, t2, t5

sd      s2, 0(t2)                # older store: 0x8000108c..0x80001093
lwu     a0, 16(s0)               # younger load: 0x80001090..0x80001093

li      a1, 0x11223344
bne     a0, a1, fail
