When vlmax = 1 and split the misaligned unit-stride into two memory accesses.
Then after `elemidx = elemidx & vlmax - 1.U`, the elemidx of both memory accesses will become 0, making it impossible to select the exception that was triggered first by comparing elemidx.
