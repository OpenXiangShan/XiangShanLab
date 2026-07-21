# 香山系统软件篇题库

1.  内核的一级页表和二级页表存放在什么地方？用户进程的一级页表和二级页表分别存放在什么地方？（10 分）
2.  请简述 Linux 内核在理想情况下页面分配器 (page allocator) 是如何分配出连续物理页面的？如何从分配掩码中确定可以从哪些 zone 中分配内存？页面分配器是按照什么方向来扫描 zone 的？（20 分）
3.  现代容器（如 Docker）依赖 Linux 内核提供的两个核心特性：
    -   **Namespaces**：提供隔离性（PID、UTS、IPC、Network 等）
    -   **Cgroups**：提供资源限制（CPU、内存等）
    编写一个程序，使用 `clone()` 创建一个新的进程，并在新的命名空间中运行一个用户指定的命令，同时将该进程限制在一个特定的 cgroup 中（例如限制 CPU 使用时间）。（30 分）
4.  描述 CVE-2019-5736 漏洞的触发和修复原理。（40 分）
5.  实现新增 3 条 ecall：
    参考 [https://github.com/riscv-non-isa/riscv-ap-tee/blob/main/src/sbi_cove.adoc](https://github.com/riscv-non-isa/riscv-ap-tee/blob/main/src/sbi_cove.adoc)
    1.  `sbi_covh_convert_pages()`
    2.  `sbi_covh_global_fence()`
    3.  `sbi_covh_local_fence()`
6.  当内核镜像文件 `Image` 尺寸比较大时（比如 43MB），使用以下方法编译 OpenSBI 镜像，启动 Linux 时会遇到失败，请问原因是什么，应该如何调整 OpenSBI 以解决这个问题？

    **Linux 启动报错信息（见文件 qemu.txt）：**

[ 7.102089] Kernel panic - not syncing: uncompression error  
[ 7.103977] CPU: 0 UID: 0 PID: 26 Comm: kworker/u4:1 Not tainted 6.18.0-00031-g664baa8caf89-dirty #107 NONE  
[ 7.105606] Hardware name: bosc,kmh-v2-1core (DT)  
... (详细堆栈信息省略) ...  
**OpenSBI 源码下载及编译命令：**
```
bash
git clone https://github.com/OpenXiangShan/opensbi.git
cd opensbi/
git checkout devel
export ARCH=riscv && export CROSS_COMPILE=riscv64-unknown-linux-gnu- && export PATH=$PATH:<交叉编译工具链安装位置>/bin
make distclean
make PLATFORM=generic CROSS_COMPILE=riscv64-unknown-linux-gnu- FW_FDT_PATH=./kmh-v2-1core.dtb FW_TEXT_START=0x80000000 FW_JUMP_ADDR=0x80400000
```
**参考资料：**
-   RISC-V 交叉编译工具链: [https://github.com/plctlab/riscv-gnu-toolchain/releases/download/2025.08.02/riscv64-glibc-ubuntu-22.04-gcc-nightly-2025.08.02-nightly.tar.xz](https://github.com/plctlab/riscv-gnu-toolchain/releases/download/2025.08.02/riscv64-glibc-ubuntu-22.04-gcc-nightly-2025.08.02-nightly.tar.xz)
-   预编译好的设备树文件: 参见文件 `kmh-v2-1core.dtb`
-   预编译好的内核镜像文件: 参见文件 `Image`

**QEMU 源码下载及编译命令：**
```
bash
git clone https://github.com/OpenXiangShan/qemu.git
git checkout devel
./configure --target-list=riscv64-linux-user,riscv64-softmmu --enable-slirp --enable-virtfs --enable-debug --enable-zstd --enable-plugins
make
```
**QEMU 启动命令**（需要在可用内存大于 18GB 的 Linux PC 上运行）：
```
bash
./build/qemu-system-riscv64 -nographic -machine xiangshan-kunminghu -smp 1 -m 16G -bios ./fw_jump.bin -nographic -device loader,file=./Image,addr=0x80400000
```