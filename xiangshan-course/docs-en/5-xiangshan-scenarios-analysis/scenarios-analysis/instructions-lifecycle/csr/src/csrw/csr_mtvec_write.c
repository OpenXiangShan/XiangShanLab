// csr_mtvec_write.c
// Reproduction: csrw mtvec, t0
// Expansion: csrrw x0, mtvec, t0   -> encoding 0x30529073
// Written value: 0x80001234 -> MODE=Direct, BASE=0x80001234

#define MT_VEC_VAL 0x80001234UL

__attribute__((section(".text.init"), naked))
void _start(void) {
    asm volatile(
        // li is a pseudo-instruction that expands into 4 instructions:
        // 0x80000000: lui   t0, 0x80
        // 0x80000004: addiw t0, t0, 1
        // 0x80000006: slli  t0, t0, 0xc
        // 0x80000008: addi  t0, t0, 564    -> t0 = 0x80001234
        // 0x8000000c: csrw  mtvec, t0      -> encoding 0x30529073
        // 0x80000010: csrr  t1, mtvec      -> read back, for waveform comparison
        // 0x80000014: j     0x80000014     -> infinite loop, wait for -C to expire
        "li    t0, 0x80001234\n"
        "csrw  mtvec, t0\n"
        "csrr  t1, mtvec\n"
        "li    t2, 0x80001234\n"
        "bne   t1,t2,  1f\n"
        "j     2f\n"
        "1:    j 1b\n"
        "2:    j 2b\n"

    );
}
