# AMO指令的复杂程序的执行过程分析 (TODO)

本文我们分析 `amoswap.w.aq`指令的执行过程.

```bash
#include <klib.h>

// Spinlock data structure
struct spinlock {
  unsigned int locked;  // 1 if acquired
  unsigned int hartid;  // if acquired, the hart ID of the core that acquired the lock
};

// Multi-Core functions
static inline unsigned int get_hartid() {
  unsigned int res;
  asm volatile("csrr %0, mhartid" : "=r"(res));
  return res;
}

// Spinlock functions
void initLock(struct spinlock *lk) {
  lk->locked = 0; lk->hartid = -1; return;
}

static inline unsigned int xchg(volatile unsigned int *addr, unsigned int newval) {
  unsigned int old;
  asm volatile(
    "amoswap.w.aq %0, %2, (%1)"
    : "=r"(old)
    : "r"(addr), "r"(newval)
    : "memory"
  );
  return old;
}

// acquire the lock (spin)
void acquirelock(struct spinlock *lk) {

  unsigned int id = get_hartid();

  while (1) {
    if (xchg(&lk->locked, 1) == 0)
      break;
  }

  // memory barrier:
  // prevent later loads/stores from moving before lock acquired
  asm volatile("fence rw, rw" ::: "memory");

  lk->hartid = id;
}

// release the lock
void releaselock(struct spinlock *lk) {

  unsigned int id = get_hartid();

  // correctness check (like xv6 panic)
  if (!lk->locked || lk->hartid != id) {
    printf("panic: release from non-owner core %d (owner %d)\n",
           id, lk->hartid);
    while (1);
  }

  lk->hartid = -1;

  // memory barrier:
  // ensure all writes in critical section visible before unlock
  asm volatile("fence rw, rw" ::: "memory");

  // release lock
  lk->locked = 0;
}

static struct spinlock lk __attribute__((aligned(64)));
static volatile int lock_initialized __attribute__((aligned(64))) = 0;
static volatile int core_counts[4] __attribute__((aligned(64))) = {5,7,0,0};

int main() {
  unsigned int id = get_hartid();
  printf("hart %d started\n", id);

  if (id == 0) {
    initLock(&lk);
    asm volatile("fence rw,rw" ::: "memory");
    lock_initialized = 1;
  }

  while (lock_initialized == 0);

  asm volatile("fence r, r" ::: "memory");
  printf("hart %d passed init barrier\n", id);

  while (core_counts[id] != 0) {
    acquirelock(&lk);
    printf("C%dAQ ", id);
    releaselock(&lk);
    printf("C%dRL ", id);
    core_counts[id] = core_counts[id] - 1;
    asm volatile("fence rw,rw" ::: "memory");
  }

  int sum = -1;
  while (sum != 0) {
    asm volatile("fence rw,rw" ::: "memory");
    sum = core_counts[0] + core_counts[1] + core_counts[2] + core_counts[3];
  }

  return 0;
}

```


> 更新: 2026-05-26 15:57:08  
