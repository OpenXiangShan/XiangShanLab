   li t0, 0x40000000
   li t1, 0x1122334455667788
   sd t1, 0(t0)      /* store to XWR = 001 */
   li t0, 0x40002000
   jr t0             /* fetch from XWR = 011 */
