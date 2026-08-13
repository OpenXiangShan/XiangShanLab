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
