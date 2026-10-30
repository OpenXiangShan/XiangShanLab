// csr_ooo_read_write.c
// Companion analysis: out-of-order CSR reads / waitForward lists / CSR write serialization
//
// _start is located at 0x80000000; instructions and addresses correspond one-to-one:
//   0x80000000: addi t0, x0, 0x111    fill the pipeline
//   0x80000004: addi t1, x0, 0x222
//   0x80000008: addi t2, x0, 0x333
//   0x8000000c: csrr t3, mscratch     (A) out-of-order read: mscratch=0x340 is not on the list
//   0x80000010: addi t4, x0, 0x444    (B) younger integer instruction; can execute past (A)
//   0x80000014: csrr t5, mstatus      (C) ordered read: mstatus=0x300 is on the waitForward list
//   0x80000018: csrw mscratch, t0     (D) CSR write: always serialized
//   0x8000001c: csrw mscratch, t1     (E) CSR write: always serialized
//   0x80000020: j    0x80000020       halt, wait for the simulation -C limit
//
// Notes:
//   - csrr is a pseudo-instruction for csrrs rd, csr, x0; csrw is a pseudo-instruction for csrrw x0, csr, rs.
//   - The program does not initialize mscratch, so its reset value is not guaranteed; in this dump (A) actually reads
//     0xc4a64dd286bb263f. Do not assume it is 0, and do not rely on it in the program.
//   - All instructions are placed in the same asm block to ensure their order is not disturbed by the compiler.

__attribute__((section(".text.init"), naked))
void _start(void) {
    asm volatile(
        "addi t0, x0, 0x111\n"
        "addi t1, x0, 0x222\n"
        "addi t2, x0, 0x333\n"
        "csrr t3, mscratch\n"       // (A) out-of-order read, no waitForward
        "addi t4, x0, 0x444\n"      // (B) can execute past (A)
        "csrr t5, mstatus\n"        // (C) on the waitForward list
        "csrw mscratch, t0\n"       // (D) serialized
        "csrw mscratch, t1\n"       // (E) serialized
        "1: j 1b\n"                 // halt
    );
}
