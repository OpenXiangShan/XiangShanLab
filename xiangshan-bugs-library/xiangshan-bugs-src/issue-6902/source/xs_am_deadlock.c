#include <am.h>
#include <klib.h>

/* XS emu 死锁 AM 环境重现 v2——加大压力强制写回
 * v1（64KB 连写）未复现。v2 策略：
 *   1) 写脏 0x88000000 区 1MB（超出 L2 容量，迫使 L1/L2 逐出脏行）
 *   2) 从合法 DRAM 读 2MB 冲刷（强制 0x88000000 脏行从 L2 逐出→写回）
 *   3) 验证读回
 */
#define ZONE ((volatile uint64_t *)0x88000000UL)
#define ZONE_SZ (131072)       /* 1MB / 8B */
#define FLUSH ((volatile uint64_t *)0x80020000UL)  /* AM 堆附近合法 DRAM */
#define FLUSH_SZ (262144)      /* 2MB / 8B */

int main() {
    printf("V2 phase 1: dirty 1MB at 0x88000000\n");
    for (int i = 0; i < ZONE_SZ; i++) {
        ZONE[i] = (uint64_t)i;
    }
    printf("V2 phase 1 done\n");

    printf("V2 phase 2: flush by reading 2MB legal DRAM\n");
    uint64_t acc = 0;
    for (int i = 0; i < FLUSH_SZ; i++) {
        acc += FLUSH[i];
    }
    printf("V2 phase 2 done acc=%d\n", (int)(acc & 0xFF));

    printf("V2 phase 3: verify read back\n");
    uint64_t err = 0;
    for (int i = 0; i < ZONE_SZ; i += 1024) {
        if (ZONE[i] != (uint64_t)i) err++;
    }
    printf("V2 phase 3 done err=%d\n", (int)err);

    printf("V2 ALL COMPLETED - NO DEADLOCK\n");
    return 0;
}
