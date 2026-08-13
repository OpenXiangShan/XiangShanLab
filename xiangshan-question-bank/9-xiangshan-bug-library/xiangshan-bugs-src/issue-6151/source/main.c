#include <klib.h>
#include <stdint.h>

static inline uint64_t rd_mcycle(void) {
  uint64_t c;
  asm volatile("csrr %0, mcycle" : "=r"(c));
  return c;
}

static void init_vector(void) {
  asm volatile(
    "lui   a0, 0x2\n\t"
    "addiw a0, a0, 512\n\t"
    "csrs  mstatus, a0\n\t"
    "csrwi vcsr, 0\n\t"
    ::: "a0");
}

__attribute__((aligned(64)))
static volatile uint8_t buf[256];

#define K 256

int main(void) {
  init_vector();
  uintptr_t p = (uintptr_t)buf;

  uint64_t a0 = rd_mcycle();
  for (int i = 0; i < K; i++) {
    asm volatile(
      "li t0, 0\n\t"
      "vsetvli x0, t0, e8, m1, ta, ma\n\t"
      "vlseg2e8.v v0, (%0)\n\t"
      :: "r"(p) : "t0", "v0", "v1", "memory");
  }
  uint64_t a1 = rd_mcycle();

  uint64_t b0 = rd_mcycle();
  for (int i = 0; i < K; i++) {
    asm volatile(
      "li t0, 2\n\t"
      "vsetvli x0, t0, e8, m1, ta, ma\n\t"
      "vlseg2e8.v v0, (%0)\n\t"
      :: "r"(p) : "t0", "v0", "v1", "memory");
  }
  uint64_t b1 = rd_mcycle();

  uint64_t c0 = rd_mcycle();
  for (int i = 0; i < K; i++) {
    asm volatile(
      "li t0, 0\n\t"
      "vsetvli x0, t0, e8, m1, ta, ma\n\t"
      "vle8.v v0, (%0)\n\t"
      :: "r"(p) : "t0", "v0", "memory");
  }
  uint64_t c1 = rd_mcycle();

  uint64_t dA = a1 - a0, dB = b1 - b0, dC = c1 - c0;

  printf("=== VSegmentUnit with vl=0 (K=%d iters each) ===\n", K);
  printf("(A) vl=0 vlseg2e8.v : %lu cyc total, %lu cyc/iter\n", dA, dA / K);
  printf("(B) vl=2 vlseg2e8.v : %lu cyc total, %lu cyc/iter\n", dB, dB / K);
  printf("(C) vl=0 vle8.v     : %lu cyc total, %lu cyc/iter\n", dC, dC / K);
  printf("delta(A-B) per iter = %ld cyc\n", (long)(dA / K) - (long)(dB / K));
  printf("delta(A-C) per iter = %ld cyc\n", (long)(dA / K) - (long)(dC / K));
  return 0;
}
