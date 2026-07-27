# perfetch的bug

/nfs/home/wanghao/xs-test/perfetch





![figure-001-prefetch-bug-exception](./img/prefetch-bug/figure-001-prefetch-bug-exception.png)

v3把0x80001198识别到了一个异常（v2没有识别到）

反汇编：



![figure-002-prefetch-bug-disassembly-ori](./img/prefetch-bug/figure-002-prefetch-bug-disassembly-ori.png)

只是一条ORI指令？？？

再看v3行为：

![figure-003-prefetch-bug](./img/prefetch-bug/figure-003-prefetch-bug.png)

真把

```scala
    80001198:	001ae013          	ori	zero,s5,1
```

识别成了异常

异常码mcause是0x5

![figure-004-prefetch-bug-exception-mcause](./img/prefetch-bug/figure-004-prefetch-bug-exception-mcause.png)

？一条ORI识别出来有加载访问异常？？？？？？





> 更新: 2026-05-15 16:01:20  
