#include <klib.h>
#include <stdint.h>

#define CACHE_LINE_BYTES 64UL
#define DCACHE_BANKS 8UL
#define DCACHE_SETS 256UL
#define TRIGGER_ROUNDS 64UL
#define EXPECTED_ROUND 0x6666666666666666ULL

extern uint64_t cache_conflict_warmup(const volatile uint64_t *base);
extern uint64_t cache_conflict_trigger(const volatile uint64_t *base);

__attribute__((aligned(4096), section(".data.cache_conflict")))
static volatile uint64_t conflict_data[24] = {
  [0] = 0x1111111111111111ULL,
  [8] = 0x2222222222222222ULL,
  [16] = 0x3333333333333333ULL,
};

static volatile uint64_t result_sink;

static uintptr_t dcache_bank(uintptr_t addr) {
  return (addr >> 3) & (DCACHE_BANKS - 1);
}

static uintptr_t dcache_set(uintptr_t addr) {
  return (addr >> 6) & (DCACHE_SETS - 1);
}

int main(void) {
  const uintptr_t base = (uintptr_t)conflict_data;
  const uint64_t expected_round = EXPECTED_ROUND;

  printf("CACHE_CONFLICT_BEGIN\n");
  printf("base=%lx bank=%lu sets=%lu/%lu/%lu\n",
         base,
         dcache_bank(base),
         dcache_set(base),
         dcache_set(base + CACHE_LINE_BYTES),
         dcache_set(base + 2 * CACHE_LINE_BYTES));

  if ((base & (CACHE_LINE_BYTES - 1)) != 0 ||
      dcache_bank(base) != dcache_bank(base + CACHE_LINE_BYTES) ||
      dcache_bank(base) != dcache_bank(base + 2 * CACHE_LINE_BYTES) ||
      dcache_set(base) == dcache_set(base + CACHE_LINE_BYTES) ||
      dcache_set(base) == dcache_set(base + 2 * CACHE_LINE_BYTES)) {
    printf("CACHE_CONFLICT_FAIL address-layout\n");
    return 1;
  }

  const uint64_t warmup = cache_conflict_warmup(conflict_data);
  asm volatile("fence rw, rw" ::: "memory");

  uint64_t checksum = 0;
  for (uint64_t i = 0; i < TRIGGER_ROUNDS; i++) {
    checksum += cache_conflict_trigger(conflict_data);
    asm volatile("fence rw, rw" ::: "memory");
  }

  result_sink = checksum;
  const uint64_t expected = expected_round * TRIGGER_ROUNDS;
  if (warmup != expected_round || checksum != expected) {
    printf("CACHE_CONFLICT_FAIL warmup=%lx checksum=%lx expected=%lx\n",
           warmup, checksum, expected);
    return 1;
  }

  printf("CACHE_CONFLICT_PASS rounds=%lu checksum=%lx\n",
         TRIGGER_ROUNDS, result_sink);
  return 0;
}
