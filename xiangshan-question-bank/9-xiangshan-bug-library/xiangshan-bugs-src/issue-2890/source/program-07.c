// asm.S
.text
.balign 8
.global foo
foo:
        vsetvli t0, x0, e8, m1, ta, ma
        ret
// hello.c 
#include <klib.h>
void foo(void);
int main(void) {
        printf("beg\n"); foo(); printf("end\n");
        return 0;
}
