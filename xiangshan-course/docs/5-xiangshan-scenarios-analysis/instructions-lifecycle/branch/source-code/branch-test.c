#include <am.h>

static volatile unsigned long sink;

static unsigned long branch_test(void) {
  unsigned long taken_value = 1;
  unsigned long different_value = 2;
  unsigned long result = 0;
  unsigned long i;

  /* Actual taken: rs1 == rs2, so the addi in the fall-through path is skipped. */
  __asm__ volatile (
      "beq %[a], %[a], 1f\n"
      "addi %[r], %[r], 1\n"
      "1:"
      : [r] "+r" (result)
      : [a] "r" (taken_value)
      : "memory");

  /* Actual not-taken: rs1 != rs2, so the fall-through addi is executed. */
  __asm__ volatile (
      "beq %[a], %[b], 1f\n"
      "addi %[r], %[r], 2\n"
      "1:"
      : [r] "+r" (result)
      : [a] "r" (taken_value), [b] "r" (different_value)
      : "memory");

  /* Repeated pattern: taken once every eight iterations. */
  for (i = 0; i < 64; i++) {
    unsigned long zero = i & 7;
    __asm__ volatile (
        "beq %[z], zero, 1f\n"
        "addi %[r], %[r], 1\n"
        "j 2f\n"
        "1: addi %[r], %[r], 3\n"
        "2:"
        : [r] "+r" (result)
        : [z] "r" (zero)
        : "memory");
  }

  return result;
}

int main(void) {
  sink = branch_test();
  _halt(0);
  return 0;
}
