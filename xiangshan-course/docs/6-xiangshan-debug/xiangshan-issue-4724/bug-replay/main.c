#include <am.h>
#include <csr.h>
#include <klib.h>
#include <xsextra.h>

#define CSR_PMACFG0  0x7c0
#define CSR_PMAADDR0 0x7c8

#define PMA_REGION_SIZE 4096UL
#define PMA_ENTRY_MASK  0xffUL

#define PMA_R          (1UL << 0)
#define PMA_W          (1UL << 1)
#define PMA_A_NAPOT    (3UL << 3)
#define PMA_ATOMIC     (1UL << 5)
#define PMA_C          (1UL << 6)
#define PMA_L          (1UL << 7)

#define PMA_ENTRY_CFG (PMA_C | PMA_A_NAPOT | PMA_W | PMA_R)

#define STORE_AMO_ACCESS_FAULT 7

#define INITIAL_VALUE 0x1122334455667788ULL
#define AMO_ADDEND     0x10ULL
#define AMO_NO_RESULT  0xbad0bad0bad0bad0ULL

/*
 * A complete, naturally aligned page makes the intended PMA interval
 * independent of the final link address.
 */
static volatile uint64_t pma_region[PMA_REGION_SIZE / sizeof(uint64_t)]
  __attribute__((aligned(PMA_REGION_SIZE))) = {INITIAL_VALUE};

static volatile uint64_t access_fault_count;

extern int g_config_disable_timer;

static _Context *cte_entry_handler(_Event event, _Context *context) {
  (void)event;
  return context;
}

static _Context *amo_access_fault_handler(_Event *event, _Context *context) {
  (void)event;

  access_fault_count++;
  printf("[AccessFault] store/AMO access fault: scause=0x%lx, "
         "sepc=0x%lx, stval=0x%lx\n",
         (unsigned long)context->scause,
         (unsigned long)context->sepc,
         (unsigned long)csr_read(stval));

  /* amoadd.d is always a 32-bit instruction. Skip it to avoid retrapping. */
  context->sepc += 4;
  return context;
}

static uintptr_t encode_napot(uintptr_t base, uintptr_t size) {
  return (base + size / 2 - 1) >> 2;
}

static void configure_pma_region(void) {
  const uintptr_t base = (uintptr_t)&pma_region[0];
  const uintptr_t encoded_addr = encode_napot(base, PMA_REGION_SIZE);
  const uintptr_t old_cfg = csr_read(CSR_PMACFG0);
  const uintptr_t cfg_without_entry0 = old_cfg & ~PMA_ENTRY_MASK;

  if ((base & (PMA_REGION_SIZE - 1)) != 0) {
    printf("PMA region is not 4 KiB aligned: 0x%lx\n",
           (unsigned long)base);
    _halt(1);
  }

  if ((old_cfg & PMA_L) != 0) {
    printf("PMA entry 0 is locked: pmacfg0=0x%lx\n",
           (unsigned long)old_cfg);
    _halt(1);
  }

  /* Disable entry 0 while changing its address and matching mode. */
  csr_write(CSR_PMACFG0, cfg_without_entry0);
  csr_write(CSR_PMAADDR0, encoded_addr);
  csr_write(CSR_PMACFG0, cfg_without_entry0 | PMA_ENTRY_CFG);
  asm volatile("fence rw, rw\n\tsfence.vma" ::: "memory");

  const uintptr_t actual_cfg = csr_read(CSR_PMACFG0) & PMA_ENTRY_MASK;
  const uintptr_t actual_addr = csr_read(CSR_PMAADDR0);

  printf("PMA entry 0: [0x%lx, 0x%lx), cfg=0x%lx "
         "(C=1 W=1 R=1 atomic=0)\n",
         (unsigned long)base,
         (unsigned long)(base + PMA_REGION_SIZE),
         (unsigned long)actual_cfg);

  if (actual_cfg != PMA_ENTRY_CFG || actual_addr != encoded_addr ||
      (actual_cfg & PMA_ATOMIC) != 0) {
    printf("PMA readback mismatch: pmaaddr0=0x%lx (expected 0x%lx)\n",
           (unsigned long)actual_addr,
           (unsigned long)encoded_addr);
    _halt(1);
  }
}

int main(void) {
  volatile uint64_t *const target = &pma_region[0];
  uint64_t amo_old_value = AMO_NO_RESULT;

  configure_pma_region();

  /* _cte_init() resets the handler table, so register cause 7 afterwards. */
  g_config_disable_timer = 1;
  _cte_init(cte_entry_handler);
  irq_handler_reg(STORE_AMO_ACCESS_FAULT, amo_access_fault_handler);
  printf("CTE store/AMO AccessFault handler registered before AMO\n");

  printf("Before amoadd.d: address=0x%lx value=0x%lx addend=0x%lx\n",
         (unsigned long)target,
         (unsigned long)*target,
         (unsigned long)AMO_ADDEND);

  asm volatile(
    "amoadd.d %[old], %[addend], (%[address])"
    : [old] "+r"(amo_old_value)
    : [address] "r"(target), [addend] "r"(AMO_ADDEND)
    : "memory"
  );

  printf("After amoadd.d: handler_count=%lu old=0x%lx memory=0x%lx\n",
         (unsigned long)access_fault_count,
         (unsigned long)amo_old_value,
         (unsigned long)*target);

  if (access_fault_count == 0) {
    printf("AMO completed without entering the AccessFault handler\n");
  } else {
    printf("AMO was skipped after entering the AccessFault handler\n");
  }

  return 0;
}
