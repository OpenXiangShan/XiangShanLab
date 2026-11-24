/* tlb_invalidation: 'sfence.vma' / 'global_leaf' / 'address'; page 3, offset 0x58,
   asid 0x1285/0x50e7, vmid 0x0/0x0, va window 0x74de63200000, gpa window 0x0,
   fence address 0x74de63203058, fence identifier 0x1285. */

typedef unsigned long uint64_t;

static volatile uint64_t portable_phase __attribute__((section(".data"))) = 1;
static volatile uint64_t portable_reason __attribute__((section(".data"))) = 0;
static volatile uint64_t portable_pc, portable_cause, portable_tval, portable_status;
static volatile uint64_t portable_count;
static uint64_t portable_notes[32][5];
static void portable_note(uint64_t, uint64_t, uint64_t, uint64_t, uint64_t);
static void portable_capture(uint64_t, uint64_t, uint64_t, uint64_t);
static __attribute__((noreturn)) void portable_finish(int);
static __attribute__((naked, aligned(4), used)) void portable_setup_vector(void);


static __attribute__((naked, aligned(4), noreturn)) void park_hart(void)
{
    asm volatile(
        "1: wfi\n"
        "j 1b\n"
    );
}

static char report_text[4096];
/* In .data: not every test clears .bss. */
static uint64_t report_length __attribute__((section(".data"))) = 0;
static uint64_t report_item;

static inline void put_text(const char *text)
{
    while (*text)
        report_text[report_length++] = *text++;
}

static inline void put_hexadecimal(uint64_t value)
{
    char digits[16];
    int count = 0;

    do
        digits[count++] = "0123456789abcdef"[value & 15];
    while (value >>= 4);
    put_text("0x");
    while (count)
        report_text[report_length++] = digits[--count];
}

static inline void put_decimal(uint64_t value)
{
    char digits[20];
    int count = 0;

    do
        digits[count++] = '0' + value % 10;
    while (value /= 10);
    while (count)
        report_text[report_length++] = digits[--count];
}

static inline void put_item_start(void)
{
    put_text("   ");
    put_decimal(++report_item);
    put_text(") ");
}

static inline void put_item(const char *text)
{
    put_item_start();
    put_text(text);
    put_text("\n");
}

static inline const char *cause_name(uint64_t cause)
{
    switch (cause) {
    case 1: return "取指访问错误";
    case 2: return "非法指令";
    case 5: return "读取访问错误";
    case 7: return "存储访问错误";
    case 8: case 9: case 11: return "环境调用";
    case 12: return "取指页错误";
    case 13: return "读取页错误";
    case 15: return "存储页错误";
    case 20: return "客户机取指页错误";
    case 21: return "客户机读取页错误";
    case 23: return "客户机存储页错误";
    default: return "未知异常";
    }
}

static inline void put_trap(uint64_t cause, uint64_t tval)
{
    put_item_start();
    put_text("异常mcause = ");
    put_decimal(cause);
    put_text("（");
    put_text(cause_name(cause));
    put_text("）\n");
    put_item_start();
    put_text("异常mtval = ");
    put_hexadecimal(tval);
    put_text("\n");
}

static void report_begin(void)
{
    put_text("测试内容：\n   " "检查 sfence.vma（按地址）能否使全局（G 位）叶子页表项的缓存翻译在两个地址空间都失效" "\n预期结果：\n");
    report_item = 0;
    put_item("地址0x74de63203058 = 0x3058");
    put_item("地址0x74de63203058 = 0x3058");
    put_item("地址0x74de63203058 = 0x100003058");
    put_item("地址0x74de63203058 = 0x100003058");
    put_text("运行结果：\n");
    report_item = 0;
}


static void portable_varuint(uint64_t value)
{
    do {
        uint64_t byte = value & 127;
        value >>= 7;
        report_text[report_length++] = byte | (value ? 128 : 0);
    } while (value);
}

static __attribute__((noreturn)) void portable_finish(int pass)
{
    uint64_t code = portable_reason ? 3 : portable_phase == 1 ? 2 : !pass;
    report_length = 0;
    put_text("PTB1");
    portable_varuint(portable_phase); portable_varuint(pass != 0);
    portable_varuint(portable_reason); portable_varuint(portable_pc);
    portable_varuint(portable_cause); portable_varuint(portable_tval);
    portable_varuint(portable_status); portable_varuint(portable_count);
    for (uint64_t i = 0; i < portable_count; i++)
        for (uint64_t j = 0; j < 5; j++) portable_varuint(portable_notes[i][j]);
    report_text[report_length++] = 10;
    for (uint64_t i = 0; i < report_length; i++) {
        while (*(volatile unsigned char *)0x40600008 & 0x08) {}
        *(volatile unsigned char *)0x40600004 = report_text[i];
    }
    asm volatile("fence iorw, iorw\nmv a0, %0\n.word 0x0005006b" :: "r"(code) : "a0", "memory");
    park_hart();
}

static void portable_note(uint64_t kind, uint64_t a, uint64_t b, uint64_t c, uint64_t d)
{
    if (portable_count >= 32) { portable_reason = 90; portable_finish(0); }
    uint64_t i = portable_count++;
    portable_notes[i][0] = kind; portable_notes[i][1] = a;
    portable_notes[i][2] = b; portable_notes[i][3] = c; portable_notes[i][4] = d;
}

static void portable_capture(uint64_t cause, uint64_t pc, uint64_t tval, uint64_t status)
{
    portable_cause = cause; portable_pc = pc;
    portable_tval = tval; portable_status = status;
    if (portable_phase == 1) portable_finish(0);
}

static __attribute__((noreturn, used)) void portable_setup_abort(void)
{
    uint64_t cause, pc, tval, status;
    asm volatile("csrr %0, mcause" : "=r"(cause));
    asm volatile("csrr %0, mepc" : "=r"(pc));
    asm volatile("csrr %0, mtval" : "=r"(tval));
    asm volatile("csrr %0, mstatus" : "=r"(status));
    portable_capture(cause, pc, tval, status);
    portable_finish(0);
}

static __attribute__((naked, aligned(4), used)) void portable_setup_vector(void)
{
    asm volatile("j portable_setup_abort");
}

static __attribute__((noreturn)) void report_finish(int pass)
{
    portable_finish(pass);
}


#define PAGE_SIZE 4096UL
#define GIB (1UL << 30)
#define RAM_BASE 0x80000000UL
#define RAM_SIZE 0x10000000UL
#define PROBE_BASE 0x80800000UL
#define BANK_PAGES 8UL
#define VA_WINDOW 0x12000000UL
/* Per space: the root, a level-2 table each for RAM and the window, the window's
   level-1 table, and two leaf tables. */
#define TABLES 12

#define SV48 (9UL << 60)
#define PTE_V 1UL
#define USER_PAGE_FLAGS 0xdfUL  /* V R W X U A D */
#define SPACE_A 0
#define SPACE_B 1
#define BANK_A 0
#define BANK_B 1
#define PTE_G 32UL

#define SYS_FINISH 0

extern uint64_t __bss_start[], __bss_end[];
static unsigned char stack[8192] __attribute__((section(".stack"), aligned(16), used));
static uint64_t tables[TABLES][512] __attribute__((aligned(4096)));
static uint64_t next_table;
static uint64_t *roots[2];
static uint64_t *leaf_tables[2][2];
/* Each space's level-1 entry for the window, which selects one of its leaf tables. */
static uint64_t *leaf_pointers[2];

#define STEPS 4

/* Per probe in script order: its address and the load value or the trap cause it should produce. */
struct step { uint64_t address, value, cause; int load; };
static const struct step expected[STEPS] = {
    { 0x12003058UL, 0x3058UL, 0, 1 },
    { 0x12003058UL, 0x3058UL, 0, 1 },
    { 0x12003058UL, 0x100003058UL, 0, 1 },
    { 0x12003058UL, 0x100003058UL, 0, 1 },
};
/* One spare slot keeps a stray trap after the last probe in bounds. */
static struct { uint64_t value, cause, tval; } observed[STEPS + 1];
static uint64_t step;

static int step_passes(uint64_t index)
{
    if (expected[index].cause)
        return observed[index].cause == expected[index].cause && (observed[index].tval == 0 || observed[index].tval == expected[index].address);
    return !observed[index].cause && (!expected[index].load || observed[index].value == expected[index].value);
}

static __attribute__((noreturn)) void report(void)
{
    int pass = 1;

    report_begin();
    for (uint64_t index = 0; index < STEPS; index++) {
        if (observed[index].cause) {
            put_trap(observed[index].cause, observed[index].tval);
        } else {
            put_item_start();
            put_text("地址");
            put_hexadecimal(expected[index].address);
            put_text(" = ");
            put_hexadecimal(observed[index].value);
            put_text("\n");
        }
        portable_note(20, expected[index].address, observed[index].value, observed[index].cause, observed[index].tval);
        pass &= step_passes(index);
    }
    report_finish(pass);
}

static uint64_t pte(uint64_t pa, uint64_t flags) { return (pa >> 12 << 10) | flags; }
static uint64_t *table_of(uint64_t pte) { return (uint64_t *)(pte >> 10 << 12); }
static uint64_t bank_page(uint64_t bank, uint64_t page) { return PROBE_BASE + (bank * BANK_PAGES + page) * PAGE_SIZE; }

/* The entry for va at the given level, building the tables above it. */
static uint64_t *walk(uint64_t *root, uint64_t va, int level)
{
    uint64_t *table = root;

    for (int above = 3; above > level; above--) {
        uint64_t *entry = &table[(va >> (12 + 9 * above)) & 511];

        if (!*entry)
            *entry = pte((uint64_t)tables[next_table++], PTE_V);
        table = table_of(*entry);
    }
    return &table[(va >> (12 + 9 * level)) & 511];
}

/* satp for a root and identifier. Never fences. */
static void write_satp(uint64_t *root, uint64_t id)
{
    uint64_t requested = SV48 | (id << 44) | ((uint64_t)root >> 12);
    asm volatile("csrw satp, %0" :: "r"(requested) : "memory");
    uint64_t actual;
    asm volatile("csrr %0, satp" : "=r"(actual));
    portable_note(10, requested, actual, 0, 0);
    if (actual != requested) { portable_reason = 5; portable_finish(0); }
    portable_note(13, (uint64_t)root, table_of(root[0])[(RAM_BASE >> 30) & 511], USER_PAGE_FLAGS, 0);
}

/* Fill the banks, build both spaces, and enter space A with nothing cached. Each
   word of a bank holds its bank number and offset; space A's original leaf table
   maps bank A and its alternate maps bank B, and space B is the reverse. */
static void reset(void)
{
    for (uint64_t bank = 0; bank < 2; bank++)
        for (uint64_t offset = 0; offset < BANK_PAGES * PAGE_SIZE; offset += 8)
            *(volatile uint64_t *)(bank_page(bank, 0) + offset) = (bank << 32) | offset;
    for (uint64_t space = 0; space < 2; space++) {
        roots[space] = tables[next_table++];
        for (uint64_t pa = RAM_BASE & ~(GIB - 1); pa < RAM_BASE + RAM_SIZE; pa += GIB)
            *walk(roots[space], pa, 2) = pte(pa, USER_PAGE_FLAGS);
        for (uint64_t alternate = 0; alternate < 2; alternate++) {
            leaf_tables[space][alternate] = tables[next_table++];
            for (uint64_t page = 0; page < BANK_PAGES; page++)
                leaf_tables[space][alternate][page] = pte(bank_page(space ^ alternate, page), USER_PAGE_FLAGS);
        }
        leaf_pointers[space] = walk(roots[space], VA_WINDOW, 1);
        *leaf_pointers[space] = pte((uint64_t)leaf_tables[space][0], PTE_V);
    }
    write_satp(roots[0], 0x1285UL);
    asm volatile("sfence.vma zero, zero" ::: "memory");
}

struct frame { uint64_t ra, t0, t1, t2, a0, a1, a2, a3, a4, a5, a6, a7, t3, t4, t5, t6; };

static void ecall(uint64_t number, uint64_t a0, uint64_t a1, uint64_t a2, uint64_t a3)
{
    register uint64_t x10 asm("a0") = a0;
    register uint64_t x11 asm("a1") = a1;
    register uint64_t x12 asm("a2") = a2;
    register uint64_t x13 asm("a3") = a3;
    register uint64_t x17 asm("a7") = number;

    asm volatile("ecall" :: "r"(x10), "r"(x11), "r"(x12), "r"(x13), "r"(x17) : "memory");
}

/* The steps a script takes. Probes run in place; everything privileged is an ecall. */
/* .option norvc keeps the probe a 4-byte instruction, which the handler's mepc += 4 skips after a trap. */
extern char portable_probe[];
static __attribute__((noinline, noclone, used)) void probe(uint64_t address)
{
    uint64_t value = 0;

    asm volatile(".option push\n" ".option norvc\n" ".global portable_probe\nportable_probe:\nld %0, 0(%1)\n" ".option pop" : "+r"(value) : "r"(address) : "memory");
    observed[step++].value = value;
}
#define SYS_SWITCH_SPACE 1
static void switch_space(uint64_t space, uint64_t id) { ecall(SYS_SWITCH_SPACE, space, id, 0, 0); }
#define SYS_MAP_PAGE 3
static void map_page(uint64_t space, uint64_t page, uint64_t bank, uint64_t flags) { ecall(SYS_MAP_PAGE, space, page, bank, flags); }
#define SYS_FENCE 5
static void fence(void) { ecall(SYS_FENCE, 0, 0, 0, 0); }

void handle_trap(struct frame *frame)
{
    uint64_t cause, pc;

    asm volatile("csrr %0, mcause" : "=r"(cause));
    asm volatile("csrr %0, mepc" : "=r"(pc));
    uint64_t status, tval;
    asm volatile("csrr %0, mstatus" : "=r"(status));
    asm volatile("csrr %0, mtval" : "=r"(tval));
    if (portable_phase == 1 || (cause != 8 && pc != (uint64_t)portable_probe)) {
        portable_phase = 1; portable_capture(cause, pc, tval, status);
    }
    if (cause != 8) {
        portable_capture(cause, pc, tval, status);
        observed[step].cause = cause;
        asm volatile("csrr %0, mtval" : "=r"(observed[step].tval));
    } else switch (frame->a7) {
    case SYS_FINISH:
        report();
    case SYS_SWITCH_SPACE:
        write_satp(roots[frame->a0], frame->a1);
        break;
    case SYS_MAP_PAGE:
        table_of(*leaf_pointers[frame->a0])[frame->a1] = pte(bank_page(frame->a2, frame->a1), frame->a3);
        break;
    case SYS_FENCE:
        asm volatile("sfence.vma %0, zero" :: "r"(0x12003058UL) : "memory");
        break;
    default:
        park_hart();
    }
    /* Resume after the handled ecall, which is always 4 bytes. */
    asm volatile("csrr %0, mepc" : "=r"(pc));
    pc += 4;
    asm volatile("csrw mepc, %0" :: "r"(pc));
}

static __attribute__((naked, aligned(4))) void trap_vector(void)
{
    asm volatile(
        "addi sp, sp, -128\n"
        "sd ra, 0(sp)\n"
        "sd t0, 8(sp)\n"
        "sd t1, 16(sp)\n"
        "sd t2, 24(sp)\n"
        "sd a0, 32(sp)\n"
        "sd a1, 40(sp)\n"
        "sd a2, 48(sp)\n"
        "sd a3, 56(sp)\n"
        "sd a4, 64(sp)\n"
        "sd a5, 72(sp)\n"
        "sd a6, 80(sp)\n"
        "sd a7, 88(sp)\n"
        "sd t3, 96(sp)\n"
        "sd t4, 104(sp)\n"
        "sd t5, 112(sp)\n"
        "sd t6, 120(sp)\n"
        "mv a0, sp\n"
        "call handle_trap\n"
        "ld ra, 0(sp)\n"
        "ld t0, 8(sp)\n"
        "ld t1, 16(sp)\n"
        "ld t2, 24(sp)\n"
        "ld a0, 32(sp)\n"
        "ld a1, 40(sp)\n"
        "ld a2, 48(sp)\n"
        "ld a3, 56(sp)\n"
        "ld a4, 64(sp)\n"
        "ld a5, 72(sp)\n"
        "ld a6, 80(sp)\n"
        "ld a7, 88(sp)\n"
        "ld t3, 96(sp)\n"
        "ld t4, 104(sp)\n"
        "ld t5, 112(sp)\n"
        "ld t6, 120(sp)\n"
        "addi sp, sp, 128\n"
        "mret\n"
    );
}

static void run(void)
{
    /* A cached global leaf goes stale in every space when it is remapped. */
    map_page(SPACE_A, 3, BANK_A, USER_PAGE_FLAGS | PTE_G);
    map_page(SPACE_B, 3, BANK_A, USER_PAGE_FLAGS | PTE_G);
    probe(0x12003058UL);
    switch_space(SPACE_B, 0x50e7UL);
    probe(0x12003058UL);
    map_page(SPACE_A, 3, BANK_B, USER_PAGE_FLAGS | PTE_G);
    map_page(SPACE_B, 3, BANK_B, USER_PAGE_FLAGS | PTE_G);
    fence();
    probe(0x12003058UL);
    switch_space(SPACE_A, 0x1285UL);
    probe(0x12003058UL);
}

void entry(void)
{

    for (volatile uint64_t *p = __bss_start; p < __bss_end; p++)
        *p = 0;
    asm volatile("csrw mtvec, %0" :: "r"((uint64_t)portable_setup_vector) : "memory");
    asm volatile(
        "li t0, 8\n"
        "csrs 0x744, t0\n"
        "li t0, 1\n"
        "slli t0, t0, 42\n"
        "csrc mstatus, t0\n"
        ::: "t0", "memory");
    asm volatile(
        "li t0, -1\n"
        "csrw 0x30c, t0\n"
        "csrw 0x30d, t0\n"
        "csrw 0x30e, t0\n"
        "csrw 0x30f, t0\n"
        "csrw 0x60c, t0\n"
        "csrw 0x60d, t0\n"
        "csrw 0x60e, t0\n"
        "csrw 0x60f, t0\n"
        "csrw 0x10c, t0\n"
        ::: "t0", "memory");
    asm volatile(
        "csrw mie, zero\n"
        "csrw medeleg, zero\n"
        "csrw mideleg, zero\n"
        ::: "t0", "memory");
    asm volatile(
        "csrw satp, zero\n"
        "sfence.vma zero, zero\n"
        ::: "t0", "memory");
    /* Access setup only: permit lower-mode code/data on PMP-equipped models. */
    asm volatile("csrw pmpaddr0, %0\ncsrw pmpcfg0, %1"
                 :: "r"(~0UL), "r"(0x1fUL) : "memory");
    asm volatile(
        "csrw hedeleg, zero\n"
        "csrw hideleg, zero\n"
        "csrw vsatp, zero\n"
        "csrw hgatp, zero\n"
        "hfence.gvma zero, zero\n"
        ::: "t0", "memory");
    reset();
    asm volatile("csrw mtvec, %0" :: "r"((uint64_t)trap_vector) : "memory");
    portable_phase = 2;

    /* mret into the next instruction with MPP = U. */
    asm volatile(
        "la t0, 1f\n"
        "csrw mepc, t0\n"
        "mret\n"
        "1:\n"
        ::: "t0", "memory"
    );
    run();
    ecall(SYS_FINISH, 0, 0, 0, 0);

}

__attribute__((naked, section(".text.init"))) void _start(void)
{
    asm volatile(
        "csrr t0, mhartid\n"
        "bnez t0, park_hart\n"
        "csrw mstatus, zero\n"
        "csrw mie, zero\n"
        ".option push\n"
        ".option norelax\n"
        "la gp, __global_pointer$\n"
        ".option pop\n"
        "la sp, stack + 8192\n"
        "j entry\n"
    );
}
