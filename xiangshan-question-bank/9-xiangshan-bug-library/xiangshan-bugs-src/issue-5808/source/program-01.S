li x19, 0xffffffffffff8000
vsetvli x9, x0, e64, m1
li x8, 0x0
vmv.v.x v22, x8
li x10, 2147491770
vsse16.v v22, (x10), x19
