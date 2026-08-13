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
