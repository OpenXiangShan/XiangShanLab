// csr_ooo_read_write.c
// 配合分析：CSR 读的乱序性 / waitForward 清单 / CSR 写的串行化
//
// _start 位于 0x80000000，指令与地址一一对应：
//   0x80000000: addi t0, x0, 0x111    填充流水线
//   0x80000004: addi t1, x0, 0x222
//   0x80000008: addi t2, x0, 0x333
//   0x8000000c: csrr t3, mscratch     (A) 乱序读：mscratch=0x340 不在清单
//   0x80000010: addi t4, x0, 0x444    (B) 年轻整数指令，可越过 (A) 执行
//   0x80000014: csrr t5, mstatus      (C) 顺序读：mstatus=0x300 在 waitForward 清单
//   0x80000018: csrw mscratch, t0     (D) CSR 写：始终串行化
//   0x8000001c: csrw mscratch, t1     (E) CSR 写：始终串行化
//   0x80000020: j    0x80000020       停机，等仿真 -C 到期
//
// 说明：
//   - csrr 是 csrrs rd, csr, x0 的伪指令；csrw 是 csrrw x0, csr, rs 的伪指令。
//   - 程序没有初始化 mscratch，它的复位值没有保证；本 dump 里 (A) 实际读到
//     0xc4a64dd286bb263f，不要假定为 0，也不要在程序里依赖它。
//   - 所有指令放在同一个 asm 块里，保证顺序不被编译器打乱。

__attribute__((section(".text.init"), naked))
void _start(void) {
    asm volatile(
        "addi t0, x0, 0x111\n"
        "addi t1, x0, 0x222\n"
        "addi t2, x0, 0x333\n"
        "csrr t3, mscratch\n"       // (A) 乱序读，不 waitForward
        "addi t4, x0, 0x444\n"      // (B) 可越过 (A)
        "csrr t5, mstatus\n"        // (C) 在 waitForward 清单内
        "csrw mscratch, t0\n"       // (D) 串行化
        "csrw mscratch, t1\n"       // (E) 串行化
        "1: j 1b\n"                 // 停机
    );
}
