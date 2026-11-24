csrrwi x0, 0x100, 7         # write 7 to writable sstatus fields; FS becomes Off
addi x16, x15, 0
fmul.d f0, f15, f9, rmm     # illegal (FS=0), delegated to S
csrr x25, 0x306             # next sequential instruction; illegal if reached in S
