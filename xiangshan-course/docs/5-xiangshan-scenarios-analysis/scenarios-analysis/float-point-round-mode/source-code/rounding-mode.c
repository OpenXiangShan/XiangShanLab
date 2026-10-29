#include <am.h>
#include <klib.h>
#include <stdint.h>

#define LANES 4
#define RM_RNE 0u
#define RM_RTZ 1u
#define RM_RDN 2u
#define RM_RUP 3u
#define RM_RMM 4u
#define FFLAG_NV 0x10u
#define FFLAG_OF 0x04u
#define FFLAG_NX 0x01u
#define SENTINEL 0x4b000000u

struct basic_case {
  const char *name;
  uint32_t lhs;
  uint32_t rhs;
  uint32_t result[5];
  uint8_t flags[5];
};

#include "expected.h"

static int failures;
static int checks;

static void init_state(void) {
  asm volatile("li t0, 0x6600\n"
               "csrs mstatus, t0\n"
               "csrwi fcsr, 0\n"
               "csrwi vcsr, 0\n"
               "csrwi vstart, 0"
               :
               :
               : "t0", "memory");
}

static void fail_u32(const char *group, const char *name, unsigned rm, unsigned lane,
                     uint32_t got, uint32_t expected) {
  failures++;
  printf("FAIL group=%s case=%s rm=%s lane=%u got=0x%08x expected=0x%08x\n",
         group, name, k_round_mode_names[rm], lane, got, expected);
}

static void check_u32(const char *group, const char *name, unsigned rm, unsigned lane,
                      uint32_t got, uint32_t expected) {
  checks++;
  if (got != expected) fail_u32(group, name, rm, lane, got, expected);
}

__attribute__((noinline))
static unsigned run_unmasked(unsigned rm, unsigned vl, const uint32_t *lhs,
                             const uint32_t *rhs, uint32_t *dst, int clear_flags) {
  unsigned flags;
  if (clear_flags)
    asm volatile("csrw frm, %4\n"
                 "csrwi fflags, 0\n"
                 "vsetvli zero, %5, e32, m1, tu, mu\n"
                 "vle32.v v1, (%1)\n"
                 "vle32.v v2, (%2)\n"
                 "vle32.v v3, (%3)\n"
                 "vfadd.vv v3, v1, v2\n"
                 "vse32.v v3, (%3)\n"
                 "csrr %0, fflags"
                 : "=r"(flags)
                 : "r"(lhs), "r"(rhs), "r"(dst), "r"(rm), "r"(vl)
                 : "v1", "v2", "v3", "memory");
  else
    asm volatile("csrw frm, %4\n"
               "vsetvli zero, %5, e32, m1, tu, mu\n"
               "vle32.v v1, (%1)\n"
               "vle32.v v2, (%2)\n"
               "vle32.v v3, (%3)\n"
               "vfadd.vv v3, v1, v2\n"
               "vse32.v v3, (%3)\n"
               "csrr %0, fflags"
               : "=r"(flags)
               : "r"(lhs), "r"(rhs), "r"(dst), "r"(rm), "r"(vl)
               : "v1", "v2", "v3", "memory");
  return flags;
}

__attribute__((noinline))
static unsigned run_masked(unsigned rm, unsigned vl, uint32_t mask_bits,
                           const uint32_t *lhs, const uint32_t *rhs, uint32_t *dst) {
  unsigned flags;
  asm volatile("csrw frm, %4\n"
               "csrwi fflags, 0\n"
               "vsetvli zero, %5, e32, m1, tu, mu\n"
               "vle32.v v1, (%1)\n"
               "vle32.v v2, (%2)\n"
               "vle32.v v3, (%3)\n"
               "vmv.v.x v0, %6\n"
               "vfadd.vv v3, v1, v2, v0.t\n"
               "vse32.v v3, (%3)\n"
               "csrr %0, fflags"
               : "=r"(flags)
               : "r"(lhs), "r"(rhs), "r"(dst), "r"(rm), "r"(vl),
                 "r"(mask_bits)
               : "v0", "v1", "v2", "v3", "memory");
  return flags;
}

static void fill4(uint32_t *values, uint32_t value) {
  for (unsigned i = 0; i < LANES; i++) values[i] = value;
}

static void test_basic_matrix(void) {
  uint32_t lhs[LANES], rhs[LANES], dst[LANES];
  for (unsigned c = 0; c < BASIC_CASE_COUNT; c++) {
    fill4(lhs, k_basic_cases[c].lhs);
    fill4(rhs, k_basic_cases[c].rhs);
    for (unsigned rm = 0; rm < ROUND_MODE_COUNT; rm++) {
      fill4(dst, SENTINEL);
      unsigned flags = run_unmasked(rm, LANES, lhs, rhs, dst, 1);
      for (unsigned lane = 0; lane < LANES; lane++) {
        check_u32("basic", k_basic_cases[c].name, rm, lane, dst[lane],
                  k_basic_cases[c].result[rm]);
      }
      check_u32("basic-flags", k_basic_cases[c].name, rm, 0, flags,
                k_basic_cases[c].flags[rm]);
      printf("OBS basic case=%s rm=%s result=0x%08x flags=0x%02x\n",
             k_basic_cases[c].name, k_round_mode_names[rm], dst[0], flags);
    }
  }
}

static void test_mask_and_vl(void) {
  const struct basic_case *inexact = &k_basic_cases[1];
  uint32_t lhs[LANES], rhs[LANES], dst[LANES];
  fill4(lhs, inexact->lhs);
  fill4(rhs, inexact->rhs);

  fill4(dst, SENTINEL);
  unsigned flags = run_masked(RM_RNE, LANES, 0x5, lhs, rhs, dst);
  for (unsigned lane = 0; lane < LANES; lane++) {
    uint32_t expected = (lane == 0 || lane == 2) ? inexact->result[RM_RNE] : SENTINEL;
    check_u32("partial-mask", inexact->name, RM_RNE, lane, dst[lane], expected);
  }
  check_u32("partial-mask-flags", inexact->name, RM_RNE, 0, flags, FFLAG_NX);

  fill4(dst, SENTINEL);
  flags = run_masked(RM_RNE, LANES, 0x0, lhs, rhs, dst);
  for (unsigned lane = 0; lane < LANES; lane++)
    check_u32("all-mask", inexact->name, RM_RNE, lane, dst[lane], SENTINEL);
  check_u32("all-mask-flags", inexact->name, RM_RNE, 0, flags, 0);

  fill4(dst, SENTINEL);
  flags = run_unmasked(RM_RUP, 2, lhs, rhs, dst, 1);
  for (unsigned lane = 0; lane < LANES; lane++) {
    uint32_t expected = lane < 2 ? inexact->result[RM_RUP] : SENTINEL;
    check_u32("vl-short", inexact->name, RM_RUP, lane, dst[lane], expected);
  }
  check_u32("vl-short-flags", inexact->name, RM_RUP, 0, flags, FFLAG_NX);

  fill4(dst, SENTINEL);
  flags = run_unmasked(RM_RNE, 0, lhs, rhs, dst, 1);
  for (unsigned lane = 0; lane < LANES; lane++)
    check_u32("vl-zero", inexact->name, RM_RNE, lane, dst[lane], SENTINEL);
  check_u32("vl-zero-flags", inexact->name, RM_RNE, 0, flags, 0);
}

static void test_flags_accumulate(void) {
  uint32_t lhs[LANES], rhs[LANES], dst[LANES];
  const struct basic_case *inexact = &k_basic_cases[1];
  const struct basic_case *overflow = &k_basic_cases[5];

  fill4(lhs, inexact->lhs);
  fill4(rhs, inexact->rhs);
  fill4(dst, SENTINEL);
  unsigned flags = run_unmasked(RM_RNE, LANES, lhs, rhs, dst, 1);
  check_u32("flags-accumulate-nx", inexact->name, RM_RNE, 0, flags, FFLAG_NX);

  fill4(lhs, overflow->lhs);
  fill4(rhs, overflow->rhs);
  flags = run_unmasked(RM_RNE, LANES, lhs, rhs, dst, 0);
  check_u32("flags-accumulate-of", overflow->name, RM_RNE, 0, flags, FFLAG_OF | FFLAG_NX);

  uint32_t pos_inf[LANES], neg_inf[LANES];
  fill4(pos_inf, 0x7f800000u);
  fill4(neg_inf, 0xff800000u);
  flags = run_unmasked(RM_RNE, LANES, pos_inf, neg_inf, dst, 0);
  check_u32("flags-accumulate-nv", "inf-plus-neg-inf", RM_RNE, 0, flags,
            FFLAG_NV | FFLAG_OF | FFLAG_NX);
}

static void test_mode_switch(void) {
  const struct basic_case *mid = &k_basic_cases[2];
  const unsigned sequence[] = {RM_RNE, RM_RUP, RM_RTZ, RM_RMM, RM_RDN, RM_RNE};
  uint32_t lhs[LANES], rhs[LANES], dst[LANES];
  fill4(lhs, mid->lhs);
  fill4(rhs, mid->rhs);
  for (unsigned i = 0; i < sizeof(sequence) / sizeof(sequence[0]); i++) {
    unsigned rm = sequence[i];
    fill4(dst, SENTINEL);
    unsigned flags = run_unmasked(rm, LANES, lhs, rhs, dst, 1);
    for (unsigned lane = 0; lane < LANES; lane++)
      check_u32("mode-switch", mid->name, rm, lane, dst[lane], mid->result[rm]);
    check_u32("mode-switch-flags", mid->name, rm, 0, flags, mid->flags[rm]);
  }
}

int main(void) {
  init_state();
  test_basic_matrix();
  test_mask_and_vl();
  test_flags_accumulate();
  test_mode_switch();
  printf("ROUNDING_MODE_SUMMARY checks=%d failures=%d\n", checks, failures);
  return failures ? 1 : 0;
}
