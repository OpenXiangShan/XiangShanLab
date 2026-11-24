#include "encoding.h"

.global do_init
do_init:
  csrr t6, tdata2
  csrr t6, tdata2
  li a0, 0
  .word 0x0000006b

failure:
  li a0, 1
  .word 0x0000006b
