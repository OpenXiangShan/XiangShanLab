
typedef unsigned long uintptr_t;
typedef unsigned long uint64_t;

#define INIT_REGISTERS() \
    asm volatile( \
        "li x1,  0\n"  \
        "li x2,  0\n"  \
        "li x3,  0\n"  \
        "li x4,  0\n"  \
        "li x5,  0\n"  \
        "li x6,  0\n"  \
        "li x7,  0\n"  \
        "li x8,  0\n"  \
        "li x9,  0\n"  \
        "li x10, 0\n"  \
        "li x11, 0\n"  \
        "li x12, 0\n"  \
        "li x13, 0\n"  \
        "li x14, 0\n"  \
        "li x15, 0\n"  \
        "li x16, 0\n"  \
        "li x17, 0\n"  \
        "li x18, 0\n"  \
        "li x19, 0\n"  \
        "li x20, 0\n"  \
        "li x21, 0\n"  \
        "li x22, 0\n"  \
        "li x23, 0\n"  \
        "li x24, 0\n"  \
        "li x25, 0\n"  \
        "li x26, 0\n"  \
        "li x27, 0\n"  \
        "li x28, 0\n"  \
        "li x29, 0\n"  \
        "li x30, 0\n"  \
        "li x31, 0\n" \
    )


    #define CLEAR_FLOAT_REGISTERS_ALT() \
    asm volatile( \
        "fmv.d.x f0, x0\n"  \
        "fmv.d.x f1, x0\n"  \
        "fmv.d.x f2, x0\n"  \
        "fmv.d.x f3, x0\n"  \
        "fmv.d.x f4, x0\n"  \
        "fmv.d.x f5, x0\n"  \
        "fmv.d.x f6, x0\n"  \
        "fmv.d.x f7, x0\n"  \
        "fmv.d.x f8, x0\n"  \
        "fmv.d.x f9, x0\n"  \
        "fmv.d.x f10, x0\n" \
        "fmv.d.x f11, x0\n" \
        "fmv.d.x f12, x0\n" \
        "fmv.d.x f13, x0\n" \
        "fmv.d.x f14, x0\n" \
        "fmv.d.x f15, x0\n" \
        "fmv.d.x f16, x0\n" \
        "fmv.d.x f17, x0\n" \
        "fmv.d.x f18, x0\n" \
        "fmv.d.x f19, x0\n" \
        "fmv.d.x f20, x0\n" \
        "fmv.d.x f21, x0\n" \
        "fmv.d.x f22, x0\n" \
        "fmv.d.x f23, x0\n" \
        "fmv.d.x f24, x0\n" \
        "fmv.d.x f25, x0\n" \
        "fmv.d.x f26, x0\n" \
        "fmv.d.x f27, x0\n" \
        "fmv.d.x f28, x0\n" \
        "fmv.d.x f29, x0\n" \
        "fmv.d.x f30, x0\n" \
        "fmv.d.x f31, x0\n" \
    )


#define CLEAR_VEC_REGISTERS_ALT() \
    asm volatile( \
        "li t0, 0x0\n"  \
        "vmv.v.x v0, t0\n"  \
        "vmv.v.x v1, t0\n"  \
        "vmv.v.x v2, t0\n"  \
        "vmv.v.x v3, t0\n"  \
        "vmv.v.x v4, t0\n"  \
        "vmv.v.x v5, t0\n"  \
        "vmv.v.x v6, t0\n"  \
        "vmv.v.x v7, t0\n"  \
        "vmv.v.x v8, t0\n"  \
        "vmv.v.x v9, t0\n"  \
        "vmv.v.x v10, t0\n"  \
        "vmv.v.x v11, t0\n"  \
        "vmv.v.x v12, t0\n"  \
        "vmv.v.x v13, t0\n"  \
        "vmv.v.x v14, t0\n"  \
        "vmv.v.x v15, t0\n"  \
        "vmv.v.x v16, t0\n"  \
        "vmv.v.x v17, t0\n"  \
        "vmv.v.x v18, t0\n"  \
        "vmv.v.x v19, t0\n"  \
        "vmv.v.x v20, t0\n"  \
        "vmv.v.x v21, t0\n"  \
        "vmv.v.x v22, t0\n"  \
        "vmv.v.x v23, t0\n"  \
        "vmv.v.x v24, t0\n"  \
        "vmv.v.x v25, t0\n"  \
        "vmv.v.x v26, t0\n"  \
        "vmv.v.x v27, t0\n"  \
        "vmv.v.x v28, t0\n"  \
        "vmv.v.x v29, t0\n"  \
        "vmv.v.x v30, t0\n"  \
        "vmv.v.x v31, t0\n"  \
    );

// RVA23 extensions configuration
struct rva23_config {
    uintptr_t mstatus_val;
    uintptr_t misa_val;
    uintptr_t menvcfg_val;
    uintptr_t senvcfg_val;
    uintptr_t pmp_config;
};

// RVA23 full extensions configuration
const struct rva23_config rva23_full_config = {
    .mstatus_val = (3 << 11) | (1 << 17) | (1 << 18) | (0x3 << 13) | (0x3 << 9) | (0x1 << 22),
    .misa_val = (1ULL << 62) | (1 << 8) | (1 << 12) | (1 << 0) | (1 << 1) | 
                (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5) | (1 << 7) | 
                (1 << 18) | (1 << 20),
    .menvcfg_val = (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5),
    .senvcfg_val = (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5),
    .pmp_config = 0x0F
};

static volatile unsigned long tohost __attribute__((section(".tohost")));
static volatile unsigned long fromhost __attribute__((section(".tohost")));

// Stack
static char stack[4096] __attribute__((aligned(16)));

// Data sections
static uint64_t int_data[1024] __attribute__((section(".data.random0"))) = {0};
static double float_data[1024] __attribute__((section(".data.random1"))) = {0};
static uintptr_t addr_data[1024] __attribute__((section(".data.random2"))) = {0};

// CSR operations
static inline uintptr_t csr_read(uintptr_t csr) {
    uintptr_t value;
    asm volatile ("csrr %0, %1" : "=r"(value) : "i"(csr));
    return value;
}

#define csr_write(csr, value) ({ \
    asm volatile ("csrw " #csr ", %0" :: "r"(value)); \
})

static inline void csr_set(uintptr_t csr, uintptr_t value) {
    asm volatile ("csrs %0, %1" :: "i"(csr), "r"(value));
}

static inline void sfence_vma() {
    asm volatile ("sfence.vma zero, zero");
}

// Initialize floating-point environment
static void init_float_env() {
    asm volatile ("csrw 0x003, %0" :: "r"(0));  // FCSR
}

static void init_vector_env() {
    asm volatile ("vsetivli zero, 1, e32, m1, ta, ma");
}

// Apply RVA23 full extensions configuration
static void apply_rva23_config(const struct rva23_config* config) {
    asm volatile ("csrw 0x301, %0" :: "r"(config->misa_val));        // MISA
    asm volatile ("csrw 0x300, %0" :: "r"(config->mstatus_val));     // MSTATUS
    asm volatile ("csrw 0x3B0, %0" :: "r"(-1UL));                    // PMPADDR0
    asm volatile ("csrw 0x3A0, %0" :: "r"(config->pmp_config));      // PMPCFG0
    asm volatile ("csrw 0x30A, %0" :: "r"(config->menvcfg_val));     // MENVCFG
    // asm volatile ("csrw 0x30C, %0" :: "r"(config->senvcfg_val));     // SENVCFG
    init_float_env();
    init_vector_env();
    asm volatile ("csrw 0x302, %0" :: "r"((1 << 8) | (1 << 9) | (1 << 11)));  // MEDELEG
    asm volatile ("csrw 0x303, %0" :: "r"((1 << 1) | (1 << 5) | (1 << 9)));   // MIDELEG
}

// Disable multicore
static void disable_multicore() {
    uintptr_t hartid;
    asm volatile ("csrr %0, mhartid" : "=r"(hartid));
    if (hartid != 0) {
        while (1) { asm volatile ("wfi"); }
    }
}

void enable_rom_protection(void) {
    // ROM区域：0x80000000 - 0x8001FFFF (128KB)
    uintptr_t rom_start = 0x80000000;
    uintptr_t rom_end = rom_start + 128 * 1024 - 1;
    
    // 计算PMP地址寄存器值 (NAPOT模式)
    // NAPOT: 地址 = (base >> 2) | ((size-1) >> 3)
    uintptr_t pmpaddr = (rom_start >> 2) | ((128 * 1024 - 1) >> 3);
    
    asm volatile(
        // 配置PMP0保护ROM为只读(执行+读)
        "csrw pmpaddr0, %0\n"
        "li t0, (1 << 7) | (1 << 2) | (1 << 1)\n"  // L=1, A=NAPOT, X=1, R=1, W=0
        "csrw pmpcfg0, t0\n"
        : : "r"(pmpaddr) : "t0", "memory"
    );
    
    // 确保PMP配置生效
    asm volatile("sfence.vma" ::: "memory");
}

// Machine mode trap handler
// __attribute__((naked, aligned(4)))
__attribute__((section(".text.trap"), naked,aligned(4), noreturn))
void trap_handler() {
    asm volatile(
        "csrr t0, mcause          \n"
        "csrr t1, mepc            \n"
        "lhu  t2, 0(t1)           \n"
        "andi t2, t2, 0x3         \n"
        "li   t3, 0x3             \n"
        "beq  t2, t3, not_compressed \n"
        "addi t1, t1, 2           \n"
        "j    update_mepc         \n"
        "not_compressed:          \n"
        "addi t1, t1, 4           \n"
        "update_mepc:             \n"
        "csrw mepc, t1            \n"
        "mret                     \n"
    );
}

static void inline _good_exit();

static void inline __attribute__((always_inline)) do_tohost(int tohost_value)
{
  while (tohost)
    fromhost = 0;
  tohost = tohost_value;
}

// Program entry
__attribute__((naked, section(".text.init")))
void _start() {
    // INIT_REGISTERS();
    asm volatile(
        "la sp, %0         \n"
        "j main_c_entry    \n" 
        : : "i" (stack + sizeof(stack))
    );
}

// Exit program
static void inline __attribute__((always_inline)) _good_exit() {
    asm volatile(
        "_good_exit:\n\t"
        "li a0, 0\n\t"
        ".word 0x5006b\n\t"
    );

    asm volatile(
        "li a0, 1\n\t"           // 退出请求
        "la t0, tohost\n\t"
        "sd a0, 0(t0)\n\t"
        "1: j 1b\n\t"            // 循环等待
    );
}

// User fuzz test main function
static void inline __attribute__((always_inline)) _fuzz_main(){
	asm volatile("_l0:\n\t\n\tlw x26, -2048(x23)\n\tlw x22, -2047(x9)\n\tlw x25, -1023(x30)\n\tlw x28, 0(x0)\n\tlw x6, 1(x30)\n\tlw x20, 2(x12)\n\tlw x10, 3(x6)\n\tlw x15, 4(x27)\n\tlw x19, 5(x0)\n\tlw x11, 6(x18)\n\tlw x10, 7(x24)\n\tlw x3, 8(x9)\n\tlw x2, 9(x30)\n\tlw x29, 10(x6)\n\tlw x17, 15(x9)\n\tlw x13, 16(x28)\n\tlw x17, 17(x16)\n\tlw x24, 21(x30)\n\tlw x3, 31(x25)\n\tlw x23, 32(x29)\n\tlw x8, 33(x0)\n\tlw x20, 63(x13)\n\tlw x4, 64(x28)\n\tlw x29, 65(x16)\n\tlw x19, 127(x18)\n\tlw x27, 128(x3)\n\tlw x13, 129(x15)\n\tlw x21, 255(x4)\n\tlw x21, 256(x30)\n\tlw x4, 257(x3)\n\tlw x0, 511(x13)\n\tlw x1, 512(x27)\n\tlw x11, 513(x10)\n\tlw x5, 1023(x27)\n\tlw x4, 1024(x13)\n\tlw x14, 1025(x15)\n\tlw x16, 2046(x4)\n\tlw x29, 2047(x1)\n\t");

}
void main_c_entry() {
    // enable_rom_protection();
    disable_multicore();
    apply_rva23_config(&rva23_full_config);
        
    asm volatile ("csrw 0x305, %0" :: "r"((uintptr_t)trap_handler));  // MTVEC
    INIT_REGISTERS();
    CLEAR_FLOAT_REGISTERS_ALT();
    CLEAR_VEC_REGISTERS_ALT();
    _fuzz_main();
    
    _good_exit();
}
