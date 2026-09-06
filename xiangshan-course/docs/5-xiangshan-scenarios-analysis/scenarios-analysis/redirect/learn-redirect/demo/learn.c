#include <stdint.h>
#include <klib.h>

volatile uint64_t * const output_base = (uint64_t *)0x90000000;

static volatile uint64_t sink64;
static volatile uint32_t branch_pattern[64] = {
  1, 0, 1, 1, 0, 0, 1, 0,
  0, 1, 0, 1, 1, 0, 1, 0,
  1, 1, 0, 1, 0, 0, 1, 1,
  0, 1, 0, 0, 1, 1, 0, 1,
  1, 0, 0, 1, 1, 0, 1, 1,
  0, 0, 1, 0, 1, 1, 0, 0,
  1, 1, 0, 1, 0, 1, 0, 0,
  1, 0, 1, 1, 0, 1, 0, 1
};

static inline uint64_t read_cycles(void) {
  uint64_t cycles;
  asm volatile("rdcycle %0" : "=r"(cycles));
  return cycles;
}

__attribute__((noinline)) static uint64_t direct_jump_chain(uint64_t seed) {
  uint64_t out;

  asm volatile(
    "mv %[out], %[in]\n\t"
    "addi %[out], %[out], 1\n\t"
    "jal x0, 1f\n\t"
    "addi %[out], %[out], 100\n\t"
    "1:\n\t"
    "addi %[out], %[out], 2\n\t"
    "jal x0, 2f\n\t"
    "addi %[out], %[out], 200\n\t"
    "2:\n\t"
    "addi %[out], %[out], 3\n\t"
    "jal x0, 3f\n\t"
    "addi %[out], %[out], 300\n\t"
    "3:\n\t"
    "addi %[out], %[out], 4\n\t"
    : [out] "=&r"(out)
    : [in] "r"(seed)
    : "memory"
  );

  return out;
}

__attribute__((noinline)) static uint64_t jalr_jump_chain(uint64_t seed) {
  uint64_t out;
  uintptr_t target1;
  uintptr_t target2;

  asm volatile(
    "la %[t1], 1f\n\t"
    "la %[t2], 2f\n\t"
    "mv %[out], %[in]\n\t"
    "addi %[out], %[out], 5\n\t"
    "jalr x0, %[t1], 0\n\t"
    "addi %[out], %[out], 500\n\t"
    "1:\n\t"
    "addi %[out], %[out], 6\n\t"
    "jalr x0, %[t2], 0\n\t"
    "addi %[out], %[out], 600\n\t"
    "2:\n\t"
    "addi %[out], %[out], 7\n\t"
    : [out] "=&r"(out), [t1] "=&r"(target1), [t2] "=&r"(target2)
    : [in] "r"(seed)
    : "memory"
  );

  return out;
}

__attribute__((noinline)) static uint64_t branch_redirect_storm(void) {
  volatile uint32_t *p = branch_pattern;
  uint64_t acc = 0;

  for (int i = 0; i < 64; ++i) {
    uint32_t v = p[i];
    asm volatile(
      "beqz %[flag], 1f\n\t"
      "add %[acc], %[acc], %[pos]\n\t"
      "j 2f\n\t"
      "1:\n\t"
      "sub %[acc], %[acc], %[neg]\n\t"
      "2:\n\t"
      : [acc] "+&r"(acc)
      : [flag] "r"(v), [pos] "r"((uint64_t)(i + 1)), [neg] "r"((uint64_t)(i + 3))
      : "memory"
    );
  }

  return acc;
}

__attribute__((noinline)) static void frontend_flush_by_fencei(void) {
  asm volatile(
    "addi t0, zero, 1\n\t"
    "addi t1, zero, 2\n\t"
    "add  t2, t0, t1\n\t"
    "fence.i\n\t"
    "addi t3, t2, 3\n\t"
    "addi t4, t3, 4\n\t"
    ::: "t0", "t1", "t2", "t3", "t4", "memory"
  );
}

__attribute__((noinline)) static uint64_t mixed_redirect_window(void) {
  uint64_t acc = 0;

  acc += direct_jump_chain(10);
  acc += jalr_jump_chain(20);
  acc += branch_redirect_storm();
  frontend_flush_by_fencei();

  asm volatile(
    "addi %[acc], %[acc], 11\n\t"
    "beqz %[acc], 1f\n\t"
    "jal x0, 2f\n\t"
    "1:\n\t"
    "addi %[acc], %[acc], 700\n\t"
    "2:\n\t"
    : [acc] "+&r"(acc)
    :
    : "memory"
  );

  return acc;
}

int main(void) {
  for (int i = 0; i < 16; ++i) {
    output_base[i] = 0;
  }

  uint64_t t0;
  uint64_t t1;
  uint64_t result;

  t0 = read_cycles();
  result = direct_jump_chain(1);
  t1 = read_cycles();
  output_base[0] = t1 - t0;
  output_base[1] = result;

  t0 = read_cycles();
  result = jalr_jump_chain(2);
  t1 = read_cycles();
  output_base[2] = t1 - t0;
  output_base[3] = result;

  t0 = read_cycles();
  result = branch_redirect_storm();
  t1 = read_cycles();
  output_base[4] = t1 - t0;
  output_base[5] = result;

  t0 = read_cycles();
  frontend_flush_by_fencei();
  t1 = read_cycles();
  output_base[6] = t1 - t0;
  output_base[7] = 0xFEC0ULL;

  t0 = read_cycles();
  result = mixed_redirect_window();
  t1 = read_cycles();
  output_base[8] = t1 - t0;
  output_base[9] = result;

  sink64 = output_base[1] ^ output_base[3] ^ output_base[5] ^ output_base[9];
  output_base[10] = sink64;
  output_base[11] = 0x5245444952454354ULL;

  printf("learnRedirect done: direct=%lu jalr=%lu branch=%lu mix=%lu\n",
         output_base[1], output_base[3], output_base[5], output_base[9]);

  return 0;
}
