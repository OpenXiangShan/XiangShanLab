# 一条FADD指令的简单分析过程

基于的波形的文件：
<a href="source-code/float-add-inst.zip" target="_blank">【附件: float-add-inst.zip】</a>

基于的波形的测试程序：

```
#include <klib.h>

volatile float a = 1.25f;
volatile float b = 2.50f;
volatile float c;

int main() {
  c = a + b;// 目标：生成 fadd.s
  assert(c == 3.75f);
  printf("success\n");
  return 0;
}
```

## 1.

未完待续。。。。。

