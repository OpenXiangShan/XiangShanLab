### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

make -s -C apps/lkvm-static install
正克隆到 'repo'...
remote: Enumerating objects: 368, done.
remote: Counting objects: 100% (368/368), done.
remote: Compressing objects: 100% (352/352), done.
remote: Total 368 (delta 14), reused 181 (delta 10), pack-reused 0 (from 0)
接收对象中: 100% (368/368), 418.32 KiB | 233.00 KiB/s, 完成.
处理 delta 中: 100% (14/14), 完成.
Makefile:377: Skipping optional libraries: bfd zlib aio
Makefile:377: Skipping optional libraries: bfd zlib aio
In file included from include/kvm/pci.h:13,
                 from include/kvm/vfio.h:6,
                 from include/kvm/kvm-config.h:5,
                 from include/kvm/kvm.h:6,
                 from builtin-balloon.c:9:
include/kvm/fdt.h:5:10: fatal error: libfdt.h: No such file or directory
    5 | #include <libfdt.h>
      |          ^~~~~~~~~~
compilation terminated.
make[2]: *** [Makefile:491：builtin-balloon.static.o] 错误 1
make[1]: *** [Makefile:20：/home/hjn/Linux_Kernel/riscv-rootfs/apps/lkvm-static/build/lkvm-static] 错误 2
make: [Makefile:12：apps/lkvm-static] 错误 2 (已忽略）



### Expected behavior

在riscv-rootfs下执行make时，报错fatal error: libfdt.h: No such file or directory，但确认已经安装了libfdt-dev (1.5.1-1)。

[TRANSLATION]

When executing make under riscv-rootfs, fatal error occurs, which is libfdt.h: No such file or directory. However libfdt-dev (1.5.1-1) is installed

### To Reproduce

在riscv-rootfs下执行make

[TRANSLATION]

 executing make under riscv-rootfs

### Environment

- XiangShan commit id: 16ae9ddcda54fc9a2fddffad73174cf793ac7814
- riscv-rootfs commit id: 28dcbcf6ff87ea8dcb2e64b62b497e16cf19bb15


### Additional context

_No response_
