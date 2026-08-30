#include <am.h>
#include <klib.h>
#include <stdint.h>

#define CACHE_LINE_BYTES 64
#define TARGET_VALUE 0x1122334455667788UL

static volatile uint64_t target_line
  __attribute__((aligned(CACHE_LINE_BYTES))) = TARGET_VALUE;

static volatile int hart0_ready
  __attribute__((aligned(CACHE_LINE_BYTES)));
static volatile int hart1_done
  __attribute__((aligned(CACHE_LINE_BYTES)));

static inline uint64_t load_target(void) {
  uint64_t value;
  asm volatile("ld %0, 0(%1)" : "=r"(value) : "r"(&target_line) : "memory");
  return value;
}

int main(void) {
  int hart = _cpu();

  if (hart == 0) {
    if (load_target() != TARGET_VALUE) {
      return 1;
    }

    asm volatile("fence rw, rw" ::: "memory");
    hart0_ready = 1;
    asm volatile("fence rw, rw" ::: "memory");

    while (!hart1_done) {
      asm volatile("fence rw, rw" ::: "memory");
    }
    return 0;
  }

  while (!hart0_ready) {
    asm volatile("fence rw, rw" ::: "memory");
  }
  asm volatile("fence rw, rw" ::: "memory");

  if (load_target() != TARGET_VALUE) {
    return 2;
  }

  hart1_done = 1;
  asm volatile("fence rw, rw" ::: "memory");

  // Keep hart 1 alive until hart 0 observes hart1_done and terminates.
  while (1) {
    asm volatile("nop");
  }
}
