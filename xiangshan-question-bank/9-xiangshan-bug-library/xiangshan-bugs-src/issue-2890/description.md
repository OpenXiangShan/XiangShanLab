Recently RVV support was merged into the master branch, and I tried running a few of my benchmarks on it, but ran into problems. Only very basic RVV functions worked, the others seem to silently hang the simulation.

For the following I've modified the $AM_HOME/apps/hello example code, and added asm.S to SRCS in the Makefile.
I've attached my entire reproducible docker setup at the end of the issue.

Here are two of the programs that hang the simulation indefinitely:

```c
// asm.S
.text
.balign 8
.global ascii_to_utf16
ascii_to_utf16:
1:
	vsetvli t0, a2, e8, m1, ta, ma
	vle8.v v0, (a1)
	vsetvli x0, x0, e16, m2, ta, ma # this originally had a bug, and used mf2
	vzext.vf2 v8, v0
	vse16.v v8, (a0)
	add a1, a1, t0
	sub a2, a2, t0
	slli t0, t0, 1
	add a0, a0, t0
	bnez a2, 1b
	ret
// hello.c
#include <klib.h>
size_t ascii_to_utf16(uint16_t *dst, uint8_t *src, size_t n);
int main(void) {
	static uint8_t src[100] = {1,2,3,4,5,6,7,8,9,0,1,2,3,4,5,6,7,8,9,0};
	static uint16_t dst[sizeof src]={};
	printf("beg\n");
	ascii_to_utf16(dst, src, sizeof src);
	printf("end\n");
	return 0;
}
```

```c
# asm.S
.text
.balign 8
.global LUT4
LUT4:
	li t0, 16
	vsetvli zero, t0, e8, m1, ta, ma
	vle8.v v0, (a0)
1:
	vsetvli a0, a2, e8, m1, ta, ma
	vle8.v v8, (a1)
	vand.vi v8, v8, 15
	vrgather.vv v16, v0, v8
	vse8.v v16, (a1)
	sub a2, a2, a0
	add a1, a1, a0
	bnez a2, 1b
	ret
// hello.c
#include <klib.h>
size_t LUT4(uint8_t lut[16], uint8_t *ptr, size_t n);
int main(void) {
	static uint8_t mem[100];
	static uint8_t lut[16] = { 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 1, 2, 3, 4, 5, 6 };
	printf("beg\n");
	LUT4((uint8_t *)lut, mem, sizeof mem);
	printf("end\n");
	return 0;
}
```

The problems only seem to occur with a larger iteration counts, e.g. the ascii_to_utf16 code works fine when processing 80 instead of 100 elements. This seems to indicate that there might be a problem with a scheduler or internal buffer filling up?

Since I also ran into problems on other implementations, I've got a quick instruction testing script that executes random instructions. However, the ~50 trials of short random instruction streams I've tested didn't run into any problems.
That's good and points towards this being a single problem, that seems to only occur with longer runs.


# Environment Reproduction

I've used the following Dockerfile to build the repository on top of the latests commit to master.
It was run when 0c00289 was the latest commit, since they there is only a single new one, that doesn't look like it would fix the problem, since it's a tiny adjustment to the LSU.

```Dockerfile
FROM ubuntu:22.04
RUN apt-get update && apt-get install -y build-essential clang libclang-dev llvm-dev cmake libspdlog-dev vim git libmlpack-dev curl wget time default-jre default-jdk
RUN git clone --recursive https://github.com/OpenXiangShan/xs-env

WORKDIR /xs-env
RUN sed 's/apt\S* install/\0 -y/g;s/source /. /g;s/sudo //g' -i ./*.sh && echo 1
RUN . ./env.sh && sed 's/$/; cd \/xs-env/g' -i ./update-submodule.sh && ./update-submodule.sh
RUN . ./env.sh && ./setup-tools.sh
RUN . ./env.sh && . ./install-verilator.sh
RUN . ./env.sh && sed 's/^git submodule.*$//g;s/env.*$//g' -i ./setup.sh && . ./setup.sh

RUN . ./env.sh && make -C XiangShan init
RUN . ./env.sh && make -C XiangShan emu CONFIG=DefaultConfig MFC=1 -j 8
RUN . ./env.sh && sed 's/unknown-//g;s/rv64gc/rv64gcv/g' -i $AM_HOME/am/arch/isa/riscv64.mk
# Once in the docker enviroment, I used the following to build and simulate the programs:
# source env.sh; cd $AM_HOME/apps/hello
# make ARCH=riscv64-xs && $NOOP_HOME/build/emu --no-diff -i ./build/hello-riscv64-xs.bin 2>/dev/null
```

PS: I've also ran into problems with rdcycle not working properly with vector instructions, a loop with 10x more iterations took fewer cycles than one with fewer iterations. Is rdcycle supposed to work with vector instruction in the current implementation? I'll have to investigate this further, and share reproducible code.
