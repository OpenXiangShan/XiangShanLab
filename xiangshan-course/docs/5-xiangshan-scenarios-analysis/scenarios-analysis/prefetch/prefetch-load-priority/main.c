#include <stdint.h>

/*
 * Keep the two streams in separate, page-aligned regions.  They are never
 * touched before the assembly tests, so the first accesses start cold in L1D.
 * 8 KiB per stream is enough for the short, cold bursts below.  The stack is
 * placed after the image BSS by the riscv64-xs AM linker.
 */
#define STREAM_BYTES (8 * 1024)
#define STREAM_WORDS (STREAM_BYTES / sizeof(uint64_t))

static volatile uint64_t demand_stream[STREAM_WORDS]
    __attribute__((aligned(4096)));
static volatile uint64_t prefetch_stream[STREAM_WORDS]
    __attribute__((aligned(4096)));

/* Keep the return value observable without spending trace cycles on UART. */
static volatile uint64_t result_sink;

extern uint64_t pf_priority_burst(const volatile uint64_t *demand,
                                  const volatile uint64_t *prefetch,
                                  uint64_t rounds);

int main(void) {
  /*
   * The default waveform has six older independent demand loads followed by
   * six younger independent prefetch.r instructions.  Every target is a
   * different 64-byte line, so MissQueue merging cannot explain the winner.
   * prefetch_priority.S additionally contains reverse/pair controls for
   * manual experiments; leaving them out here isolates this one arbitration.
   */
  result_sink = pf_priority_burst(demand_stream, prefetch_stream, 1);
  return 0;
}
