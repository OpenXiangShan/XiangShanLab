### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

When trying to execute a spinlock in multi-core XiangShan, if the release store is executed without a fence, other cores may wait for 1M cycles to get the release result. Since `1M` cycles means `0.33ms` in a 3GHz processor, it's too long for real-world applications and causes poor spinlock performance.

Maybe we should reduce the EvictCycles to `1<<10` or `1<<8` at [Sbuffer.scala#L39](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala#L39). And may also backport to kunminghu-v2.

### Expected behavior

We should see these 2 hello appear at nearly the same time in the produce program from the reproduce program.

### To Reproduce

A simple software environment to reproduce: https://github.com/cyyself/simple-sw-workbench/tree/xs-spinlock

The code is:

```c++
std::atomic<bool> global_lock(false);

void smp_acquire_lock() {
    while (global_lock.exchange(true, std::memory_order_acquire)) {
        // Busy-wait
    }
}

void smp_release_lock() {
    global_lock.store(false, std::memory_order_release);
}

int main(long hartid) {
    smp_acquire_lock();
    print_s("Hello from hart ");
    print_digit(hartid);
    print_s("\n");
    smp_release_lock();
    while(1);
    return 0;
}
```

How to run the reproduction software:

```shell
pushd $NOOP_HOME
make emu PGO_WORKLOAD=`realpath ./ready-to-run/coremark-2-iteration.bin` NUM_CORES=2 EMU_THREADS=8 EMU_TRACE=fst -j `nproc` CONFIG=MinimalConfig
popd
git clone git@github.com:cyyself/simple-sw-workbench.git -b xs-spinlock
make CROSS_COMPILE=riscv64-unknwon-linux-gnu-
# May replace with riscv64-linux-gnu-
$NOOP_HOME/build/emu -i start.bin --no-diff 2>/dev/null
```

Reproduced result:
```console
emu compiled at Feb  4 2026, 17:10:36
Using simulated 32768B flash
Core  0's Commit SHA is: 5b2718575f, dirty: 0
Core  1's Commit SHA is: 5b2718575f, dirty: 0
Using simulated 8386560MB RAM
The image is ../simple-sw-workbench/start.bin
Hello from hart 1
Hello from hart 0
^CCore 0: SOME SIGNAL STOPS THE PROGRAM at pc = 0x80000128
Core-0 instrCnt = 307,410, cycleCnt = 1,112,751, IPC = 0.276261
Core 1: SOME SIGNAL STOPS THE PROGRAM at pc = 0x80000128
Core-1 instrCnt = 555,041, cycleCnt = 1,112,751, IPC = 0.498801
Seed=0 Guest cycle spent: 1,112,756 (this will be different from cycleCnt if emu loads a snapshot)
Host time spent: 135,692ms
```

It takes about 1M cycles for the store buffer to drain.

### Environment

- XiangShan branch: kunminghu-v3
- XiangShan commit id: 5b2718575f
- XiangShan config: MinimalConfig
- NEMU commit id: -
- SPIKE commit id: -


### Additional context

_No response_
