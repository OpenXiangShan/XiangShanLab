### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

I wasn't able to measure dual issue of LMUL=1 vector instructions on KunminghuV2Config.

In an unrolled loop without/minimal dependency chain, e.g.:
```
vadd.vv v8,v16,v24
vadd.vv v9,v17,v25
vadd.vv v10,v18,v26
vadd.vv v11,v19,v27
...
```
with different instructions (`vadd.vv`, `vid.v`, interleaved `vadd.vv`&`vfadd.vv`), the measured average throughput (cycles per "LMUL=1 instruction") was always 1 at LMUL=1, and about 0.75 at LMUL=2.
The results were similar with different register dependency chains.


### Expected behavior

The expected behavior would be for a measured throughput of 0.5, since KunminghuV2Config has two vector execution units that support `vadd.vv`.

### To Reproduce

I used [this buildscript](https://github.com/camel-cdr/rvv-bench/wiki/Build-instructions-%E2%80%90-XiangShan) and the following files to create the measurements:

```c
// asm.S
.text
.balign 8

#define UNROLL 8
#define LOOP 64

.global bench_add
bench_add:
        li a0, LOOP
        csrr a1, cycle
1:
.rept UNROLL
add t0,t1,t2
add t3,t4,t5
add t6,a2,a3
add a4,a5,a6
add t0,t1,t2
add t3,t4,t5
add t6,a2,a3
add a4,a5,a6
.endr
        addi a0, a0, -1
        bnez a0, 1b
        fence.i
        csrr a0, cycle
        sub a0, a0, a1
ret


.global bench_mul
bench_mul:
        li a0, LOOP
        csrr a1, cycle
1:
.rept UNROLL
mul t0,t1,t2
mul t3,t4,t5
mul t6,a2,a3
mul a4,a5,a6
mul t0,t1,t2
mul t3,t4,t5
mul t6,a2,a3
mul a4,a5,a6
.endr
        addi a0, a0, -1
        bnez a0, 1b
        fence.i
        csrr a0, cycle
        sub a0, a0, a1
ret

.global bench_vaddvv_m1
bench_vaddvv_m1:
        vsetvli t0, x0, e32, m1, ta, ma
        li a0, LOOP
        csrr a1, cycle
1:
.rept UNROLL
vadd.vv v8,v16,v24
vadd.vv v9,v17,v25
vadd.vv v10,v18,v26
vadd.vv v11,v19,v27
vadd.vv v12,v20,v28
vadd.vv v13,v21,v29
vadd.vv v14,v22,v30
vadd.vv v15,v23,v31
.endr
        addi a0, a0, -1
        bnez a0, 1b
        fence.i
        csrr a0, cycle
        sub a0, a0, a1
ret

.global bench_vaddvv_m2
bench_vaddvv_m2:
        vsetvli t0, x0, e32, m2, ta, ma
        li a0, LOOP
        csrr a1, cycle
1:
.rept UNROLL
vadd.vv v8,v16,v24
vadd.vv v10,v18,v26
vadd.vv v12,v20,v28
vadd.vv v14,v22,v30
.endr
        addi a0, a0, -1
        bnez a0, 1b
        fence.i
        csrr a0, cycle
        sub a0, a0, a1
ret

// main.c
#include <klib.h>

size_t bench_add(void);
size_t bench_vaddvv_m1(void);
size_t bench_vaddvv_m2(void);

int main(void) {
	for (size_t i = 0; i < 10; ++i) {
		printf("add:         %u\n", bench_add());
		printf("mul:         %u\n", bench_mul());
		printf("LMUL=1 vadd: %u\n", bench_vaddvv_m1());
		printf("LMUL=2 vadd: %u\n", bench_vaddvv_m2());
	}
	return 0;
}


// Makefile
SRCS = asm.S main.c
include $(AM_HOME)/Makefile.app
```

Executing this results in the following output:

```
$ make ARCH=riscv64-xs && /xs-env/XiangShan/build/emu --no-diff -i ./build/-riscv64-xs.bin 2>/dev/null
add:         1294
mul:         2125
LMUL=1 vadd: 4244
LMUL=2 vadd: 3136
```
Notice that the code above executes `add/mul/LMUL=1 vadd.vv` 4096 times, and `LMUL=2 vadd.vv` 2048 times.

* `add` is quad issue, so about 1024 cycles are expected, this matches roughly.
* `mul` is dual issue, so about 2048 cycles are expected, this matches roughly.
* `vadd.vv` is supposed to be dual issue, so about 2048 cycles are expected, but we get a cycle count of a single issue instruction at LMUL=1 and 4/3 issue at LMUL=2

### Environment

- XiangShan branch: master
- XiangShan commit id: ebd53cdba163c9ff5304107837ccd354892c11ad


### Additional context

_No response_
