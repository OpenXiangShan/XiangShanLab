* All MOP.R.n and MOP.RR.n only update rd with 0s. This would be changed when any MOP redefined by some other extensions.
* Define all MOP.R.n and MOP.RR.n seperated instruction name for future easier modification, since any one of MOP could be meaningful instruction in the future.
* If rd is not 0, mop instructions will convert to a move instruction, which move x0 to rd.
* If rd is 0, mop instructions will convert to a addi intruction, whose rs0 is x0 and imm is 0.
