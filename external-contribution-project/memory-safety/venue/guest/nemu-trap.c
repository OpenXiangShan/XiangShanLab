/* nemu-trap: end the NEMU simulation with the exit code given as argv[1].
 *
 * Verbatim port of cpu-sec-test-env scripts/riscv64_nemu/venue/guest/
 * nemu-trap.c (2026-10-31).
 *
 * The instruction .word 0x0005006b is NEMU's `nemu_trap`: its decoding entry
 * is src/isa/riscv64/instr/decode.c:107 of the pinned NEMU, and its handler
 * (src/isa/riscv64/instr/special.h) takes a0 as the exit code -- 0 makes NEMU
 * print HIT GOOD TRAP and exit 0, anything else HIT BAD TRAP and exit 1
 * (src/cpu/cpu-exec.c:1037, src/nemu-main.c:29).
 *
 * The same instruction, with the same mv-a0 idiom, is what workload-builder's
 * br2-external/package/nemu-trap/main.c (pinned b7c321d) issues; this file is
 * the venue's own minimal helper so the guest needs no buildroot package.
 */

static void nemu_trap(int code) {
    __asm__ volatile("mv a0, %0; .word 0x0005006b" : : "r"(code));
}

int main(int argc, char **argv) {
    int code = 0;
    if (argc > 1) {
        for (const char *p = argv[1]; *p >= '0' && *p <= '9'; ++p)
            code = code * 10 + (*p - '0');
    }
    nemu_trap(code);
    return 0; /* never reached: the trap ends the simulation */
}
