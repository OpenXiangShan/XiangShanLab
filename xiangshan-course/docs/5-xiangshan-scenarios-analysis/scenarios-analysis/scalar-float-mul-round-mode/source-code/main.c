#include <am.h>
#include <klib.h>
#include <stdint.h>

/* 11 组输入，每组在五种静态舍入模式下各做一次乘法，再用动态 frm 复算一遍。
 * 操作数以位型传入，由 fmv.w.x 原样搬进浮点寄存器，避免 C 转换改变位型。 */
#define NCASE 11
#define NMODE 5

#define RM_RNE 0
#define RM_RTZ 1
#define RM_RDN 2
#define RM_RUP 3
#define RM_RMM 4

static const char *mode_name[NMODE] = {"rne", "rtz", "rdn", "rup", "rmm"};

struct test_case {
  const char *name;
  uint32_t a;
  uint32_t b;
  uint32_t result[NMODE];
  uint8_t flags[NMODE];
};

static const struct test_case cases[NCASE] = {
    /* 0: 1.5 * 2.0 = 3.0，精确 */
    {"exact", 0x3FC00000, 0x40000000,
     {0x40400000, 0x40400000, 0x40400000, 0x40400000, 0x40400000},
     {0x00, 0x00, 0x00, 0x00, 0x00}},
    /* 1: (1+2^-23)^2，低于中点 */
    {"below_mid", 0x3F800001, 0x3F800001,
     {0x3F800002, 0x3F800002, 0x3F800002, 0x3F800003, 0x3F800002},
     {0x01, 0x01, 0x01, 0x01, 0x01}},
    /* 2: 1.5*(1+3*2^-23)，正中点，下候选末位为偶 */
    {"tie_even", 0x3FC00000, 0x3F800003,
     {0x3FC00004, 0x3FC00004, 0x3FC00004, 0x3FC00005, 0x3FC00005},
     {0x01, 0x01, 0x01, 0x01, 0x01}},
    /* 3: 1.5*(1+2^-23)，正中点，下候选末位为奇 */
    {"tie_odd", 0x3FC00000, 0x3F800001,
     {0x3FC00002, 0x3FC00001, 0x3FC00001, 0x3FC00002, 0x3FC00002},
     {0x01, 0x01, 0x01, 0x01, 0x01}},
    /* 4: -1.5*(1+3*2^-23)，负中点，近零候选末位为偶 */
    {"neg_tie_even", 0xBFC00000, 0x3F800003,
     {0xBFC00004, 0xBFC00004, 0xBFC00005, 0xBFC00004, 0xBFC00005},
     {0x01, 0x01, 0x01, 0x01, 0x01}},
    /* 5: 最大有限数 * 2.0，正上溢 */
    {"ovf_pos", 0x7F7FFFFF, 0x40000000,
     {0x7F800000, 0x7F7FFFFF, 0x7F7FFFFF, 0x7F800000, 0x7F800000},
     {0x05, 0x05, 0x05, 0x05, 0x05}},
    /* 6: 负的最大有限数 * 2.0，负上溢 */
    {"ovf_neg", 0xFF7FFFFF, 0x40000000,
     {0xFF800000, 0xFF7FFFFF, 0xFF800000, 0xFF7FFFFF, 0xFF800000},
     {0x05, 0x05, 0x05, 0x05, 0x05}},
    /* 7: 最小次正规数 * 0.5 = 2^-150，下溢中点 */
    {"uf_tie_pos", 0x00000001, 0x3F000000,
     {0x00000000, 0x00000000, 0x00000000, 0x00000001, 0x00000001},
     {0x03, 0x03, 0x03, 0x03, 0x03}},
    /* 8: 最小次正规数 * 0.25 = 2^-151，低于下溢中点 */
    {"uf_below_pos", 0x00000001, 0x3E800000,
     {0x00000000, 0x00000000, 0x00000000, 0x00000001, 0x00000000},
     {0x03, 0x03, 0x03, 0x03, 0x03}},
    /* 9: -最小次正规数 * 0.5 = -2^-150，负下溢中点 */
    {"uf_tie_neg", 0x80000001, 0x3F000000,
     {0x80000000, 0x80000000, 0x80000001, 0x80000000, 0x80000001},
     {0x03, 0x03, 0x03, 0x03, 0x03}},
    /* 10: 2^-148 * 0.5 = 2^-149，次正规数精确结果 */
    {"sub_exact", 0x00000002, 0x3F000000,
     {0x00000001, 0x00000001, 0x00000001, 0x00000001, 0x00000001},
     {0x00, 0x00, 0x00, 0x00, 0x00}},
};

static int failures;
static int checks;

static uint32_t sr[NMODE];
static uint32_t sf[NMODE];

static void check(const char *name, const char *path, unsigned mode, uint32_t got,
                  uint32_t expected) {
  checks++;
  if (got != expected) {
    failures++;
    printf("FAIL case=%s path=%s rm=%s got=0x%08x expected=0x%08x\n", name, path,
           mode_name[mode], got, expected);
  }
}

static void init_state(void) {
  asm volatile("li t0, 0x6600\n"
               "csrs mstatus, t0\n"
               "csrwi fcsr, 0"
               :
               :
               : "t0");
}

/* 静态模式：舍入模式编码在 fmul.s 的 rm 字段里。结果先存下，等五个模式
 * 都跑完再打一行，减少串口输出占用的仿真时间。 */
#define RUN_STATIC(RND, idx, col)                                              \
  do {                                                                         \
    uint32_t r_, f_;                                                           \
    asm volatile(                                                              \
        "csrw    fflags, zero\n\t"                                             \
        "fmv.w.x fa0, %2\n\t"                                                  \
        "fmv.w.x fa1, %3\n\t"                                                  \
        "fmul.s  fa2, fa0, fa1, " #RND "\n\t"                                  \
        "fmv.x.w %0, fa2\n\t"                                                  \
        "csrr    %1, fflags"                                                   \
        : "=r"(r_), "=r"(f_)                                                   \
        : "r"(cases[idx].a), "r"(cases[idx].b)                                 \
        : "fa0", "fa1", "fa2");                                                \
    sr[(col)] = r_;                                                            \
    sf[(col)] = f_;                                                            \
    check(cases[idx].name, "static", (col), r_, cases[idx].result[(col)]);     \
    check(cases[idx].name, "static-flags", (col), f_, cases[idx].flags[(col)]);\
  } while (0)

static void run_static(int i) {
  RUN_STATIC(rne, i, RM_RNE);
  RUN_STATIC(rtz, i, RM_RTZ);
  RUN_STATIC(rdn, i, RM_RDN);
  RUN_STATIC(rup, i, RM_RUP);
  RUN_STATIC(rmm, i, RM_RMM);
  printf("OBS %-14s rne=%08x rtz=%08x rdn=%08x rup=%08x rmm=%08x "
         "flags=%02x/%02x/%02x/%02x/%02x\n",
         cases[i].name, sr[0], sr[1], sr[2], sr[3], sr[4],
         sf[0], sf[1], sf[2], sf[3], sf[4]);
}

/* 动态模式：指令的 rm 字段为 111，运行时从 fcsr.frm 取模式。 */
static void run_dyn(int i, unsigned mode) {
  uint32_t r_, f_;
  asm volatile("csrw    frm, %4\n\t"
               "csrw    fflags, zero\n\t"
               "fmv.w.x fa0, %2\n\t"
               "fmv.w.x fa1, %3\n\t"
               "fmul.s  fa2, fa0, fa1\n\t"
               "fmv.x.w %0, fa2\n\t"
               "csrr    %1, fflags"
               : "=r"(r_), "=r"(f_)
               : "r"(cases[i].a), "r"(cases[i].b), "r"(mode)
               : "fa0", "fa1", "fa2");
  check(cases[i].name, "dyn", mode, r_, cases[i].result[mode]);
  check(cases[i].name, "dyn-flags", mode, f_, cases[i].flags[mode]);
}

int main(void) {
  init_state();
  for (int i = 0; i < NCASE; i++) {
    run_static(i);
    for (unsigned m = 0; m < NMODE; m++) run_dyn(i, m);
  }
  printf("FMUL_ROUND_MODE_SUMMARY checks=%d failures=%d\n", checks, failures);
  return failures ? 1 : 0;
}
