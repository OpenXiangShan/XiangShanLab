#include <klib.h>
#include <stdint.h>

extern unsigned char nc_data_page[];
extern unsigned char s_mode_payload[];
extern unsigned char measured_prefetch_r[];

__attribute__((noreturn)) void run_prefetch_replay(void);

int main(void) {
  const uintptr_t warm_pa = (uintptr_t)nc_data_page + 0x40;
  const uintptr_t measured_pa = (uintptr_t)nc_data_page + 0x180;
  const uintptr_t measured_pc =
      (uintptr_t)measured_prefetch_r - (uintptr_t)s_mode_payload;

  printf("PFNC warm_pa=%lx target_pa=%lx prefetch_pc=%lx\n",
         (unsigned long)warm_pa,
         (unsigned long)measured_pa,
         (unsigned long)measured_pc);

  run_prefetch_replay();
  __builtin_unreachable();
}
