#include <am.h>
#include <klib.h>
#include <xsextra.h>

#define EXCEPTION_LOAD_ADDR_MISALIGNED 4
#define EXCEPTION_LOAD_ACCESS_FAULT 5

static volatile unsigned misaligned_seen;
static volatile unsigned access_fault_seen;

/* The generic CTE callback is not used by the RISC-V trap shim, but _cte_init
 * still requires one and installs the supervisor trap entry point. */
static _Context *cte_handler(_Event event, _Context *context) {
  (void)event;
  return context;
}

static _Context *load_misaligned_handler(_Event *event, _Context *context) {
  (void)event;
  printf("TRAP: load address misaligned (cause=4)\n");
  ++misaligned_seen;
  context->sepc += 4;
  return context;
}

static _Context *load_access_fault_handler(_Event *event, _Context *context) {
  (void)event;
  printf("TRAP: load access fault (cause=5)\n");
  ++access_fault_seen;
  context->sepc += 4;
  return context;
}

static void issue_prefetch_r(uintptr_t address) {
  /* Zicbop prefetch.r: funct3=110, rd=0, rs2=1, S-immediate=0, rs1=a2. */
  register uintptr_t base asm("a2") = address;
  asm volatile(".word 0x00166013" : : "r"(base) : "memory");
}

int main(void) {
  /* Kunminghu's timer is an on-chip MMIO device at 0x38000000. */
  const uintptr_t mmio_misaligned = 0x38000001UL;

  /* Avoid an unrelated timer interrupt while the exception test is running. */
  extern int g_config_disable_timer;
  g_config_disable_timer = 1;

  printf("prefetch.r misaligned MMIO test: addr=0x%lx\n", mmio_misaligned);
  _cte_init(cte_handler);
  irq_handler_reg(EXCEPTION_LOAD_ADDR_MISALIGNED, load_misaligned_handler);
  irq_handler_reg(EXCEPTION_LOAD_ACCESS_FAULT, load_access_fault_handler);

  issue_prefetch_r(mmio_misaligned);

  printf("prefetch.r completed: misaligned=%u access_fault=%u\n",
         misaligned_seen, access_fault_seen);
  _halt(0);
}
