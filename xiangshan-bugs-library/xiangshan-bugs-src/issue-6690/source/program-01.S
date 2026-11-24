sd a1, 0x40(s2)     # A through VA1
sd a2, 0x40(s7)     # B through VA2
ld s4, 0x40(s2)     # through VA1: returns A, B is required
