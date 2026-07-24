# perfetch的bug

/nfs/home/wanghao/xs-test/perfetch





![1778828762061-b5144943-eff1-49cc-baa6-ea55db6a5070.png](./img/uqw2OqOlourM75P0/1778828762061-b5144943-eff1-49cc-baa6-ea55db6a5070-350935.png)

v3把0x80001198识别到了一个异常（v2没有识别到）

反汇编：



![1778828851235-d1673564-106a-4ac5-8b94-fc5af481b488.png](./img/uqw2OqOlourM75P0/1778828851235-d1673564-106a-4ac5-8b94-fc5af481b488-509972.png)

只是一条ORI指令？？？

再看v3行为：

![1778828890309-aa566937-8665-4af1-b348-b83c82dbb422.png](./img/uqw2OqOlourM75P0/1778828890309-aa566937-8665-4af1-b348-b83c82dbb422-031352.png)

真把

```scala
    80001198:	001ae013          	ori	zero,s5,1
```

识别成了异常

异常码mcause是0x5

![1778829014210-8671bb74-ece1-42c6-9aa9-9352b9144f9b.png](./img/uqw2OqOlourM75P0/1778829014210-8671bb74-ece1-42c6-9aa9-9352b9144f9b-204637.png)

？一条ORI识别出来有加载访问异常？？？？？？





> 更新: 2026-05-15 16:01:20  
