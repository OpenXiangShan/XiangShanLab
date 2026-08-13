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
