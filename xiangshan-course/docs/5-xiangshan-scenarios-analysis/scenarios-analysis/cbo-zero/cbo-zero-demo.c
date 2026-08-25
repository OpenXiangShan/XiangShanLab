#include <stdint.h>

/* Keep the three targets in a normal cacheable data section.  The linker
 * places this section in the AM data image, which is backed by main memory. */
/* 8 KiB is one DCache set stride for the Kunminghu V2 default (128 sets,
 * 64-byte lines).  Keeping all lines in one object makes the set relationship
 * explicit instead of relying on linker placement of separate objects. */
__attribute__((aligned(64)))
static volatile uint8_t same_set_lines[11][0x2000];

__attribute__((aligned(64)))
static volatile uint8_t target_l2_miss[64];

static inline void cbo_zero(const volatile void *addr)
{
  __asm__ volatile("cbo.zero 0(%0)" :: "r"(addr) : "memory");
}

static inline uint64_t read_line(const volatile uint8_t *line)
{
  uint64_t value;
  __asm__ volatile("ld %0, 0(%1)" : "=r"(value) : "r"(line) : "memory");
  return value;
}

static inline void fence_rw(void)
{
  __asm__ volatile("fence rw, rw" ::: "memory");
}

int main(void)
{
  volatile uint64_t sink = 0;

  /* Scenario 1: bring the line into L1D, then zero it while it is an L1 hit. */
  volatile uint8_t *target_hit = &same_set_lines[0][0];
  volatile uint8_t *target_l2_hit = &same_set_lines[1][0];

  target_hit[0] = 0x5a;
  sink ^= read_line(target_hit);
  fence_rw();
  cbo_zero(target_hit);
  fence_rw();

  /* Scenario 2 warmup: put the target and all conflict lines in the hierarchy. */
  sink ^= read_line(target_l2_hit);
  for (unsigned i = 2; i < 11; ++i) {
    sink ^= read_line(&same_set_lines[i][0]);
  }
  fence_rw();

  /* Re-touching the conflict lines forces target_l2_hit out of L1D. */
  for (unsigned i = 2; i < 11; ++i) {
    sink ^= read_line(&same_set_lines[i][0]);
  }
  fence_rw();
  cbo_zero(target_l2_hit);
  fence_rw();

  /* Scenario 3: this line has never been read or written, so cbo.zero starts
   * from a cold L1/L2 line and must obtain ownership from the next level. */
  cbo_zero(target_l2_miss);
  fence_rw();

  /* Keep all memory operations architecturally observable. */
  (void)sink;
  return 0;
}
