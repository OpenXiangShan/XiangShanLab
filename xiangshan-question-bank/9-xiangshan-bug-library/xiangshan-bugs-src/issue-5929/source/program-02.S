800002da: 01707457    vsetvli s0,zero,e32,mf2,tu,mu
800002de: 0090079b    addiw   a5,zero,9
800002e2: 07f2        slli    a5,a5,0x1c
800002e4: fb878793    addi    a5,a5,-72
800002e8: 5e03b9d7    vmv.v.i v19,7
800002ec: 8f37fa07    vloxseg5ei64.v v20,(a5),v19
