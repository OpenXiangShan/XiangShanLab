// asm.S
.text
.balign 8
.global saxpy
saxpy:
    vsetvli a4, a0, e32, m8, ta, ma
    vle32.v v0, (a1)
    sub a0, a0, a4
    slli a4, a4, 2
    add a1, a1, a4
    vle32.v v8, (a2)
    vfmacc.vf v8, fa0, v0
    vse32.v v8, (a2)
    add a2, a2, a4
    bnez a0, saxpy
    ret
// hello.c
#include <klib.h>
void saxpy(size_t n, float a, float *b, float *c);
int main(void) {
        static float src[128] = { 1, 2, 3, 4, 5 }, dst[128] = { 0 };
        printf("beg\n");
        saxpy(128, 0.3, src, dst);
        printf("end\n");
        return 0;
}
