#include <am.h>
#include <riscv.h>
#include <xsextra.h>
#include <klib.h>

/*
 * cbo.clean/cbo.flush/cbo.inval require read permission.  The pre-4702
 * StoreUnit sends these instructions to the DTLB as writes instead.
 */
#define EXCEPTION_STORE_ACCESS_FAULT 7
#define EXCEPTION_STORE_PAGE_FAULT   15

#define CBO_VA 0xb0004000UL

static _Context *initial_trap(_Event ev, _Context *ctx) {
  (void)ev;
  return ctx;
}

static volatile int permission_fault;

static _Context *unexpected_permission_fault(_Event *ev, _Context *ctx) {
  (void)ev;
  permission_fault = 1;
  printf("unexpected premission fault pc = 0x%x!\n", ctx->sepc);
  /* The test instruction is a deliberately emitted 32-bit CBO encoding. */
  ctx->sepc += 4;
  return ctx;
}

static void execute_cbo_inval(uintptr_t address) {
  /* CBO.INVAL (a0): funct7=0000000, funct3=010, opcode=0001111. */
  asm volatile(
      "mv a0, %0\n"
      ".word 0x0005200f\n"
      :
      : "r"(address)
      : "a0", "memory");
}

int main(void) {
  extern int g_config_disable_timer;
  g_config_disable_timer = 1;

  /* _cte_init enters S-mode and installs the AM trap entry. */
  _cte_init(initial_trap);

  irq_handler_reg(EXCEPTION_STORE_PAGE_FAULT, &unexpected_permission_fault);
  irq_handler_reg(EXCEPTION_STORE_ACCESS_FAULT, &unexpected_permission_fault);
  asm volatile("sfence.vma" ::: "memory");

  execute_cbo_inval(CBO_VA);

  /* Unpatched XiangShan reaches the handler; patched XiangShan reaches here. */
  _halt(permission_fault ? 1 : 0);
}
