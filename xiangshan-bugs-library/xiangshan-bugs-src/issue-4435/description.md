when rd is equal to rs1, uop1 will write rd, while uop2 and uop3 of amocas.q need rs1 as src, which cause a RAW stalls.

However, rs1, the address of load and store, is used in uop1, we donot need rs1 in other uops.
