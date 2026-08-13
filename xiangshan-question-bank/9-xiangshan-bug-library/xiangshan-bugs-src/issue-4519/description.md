The RISC-V manual says that:
> In typical usage, software will invalidate a range of virtual addresses in the addresstranslation caches by executing an SFENCE.W.INVAL instruction, executing a series of SINVAL.VMA, HINVAL.VVMA, or HINVAL.GVMA instructions to the addresses (and optionally ASIDs or VMIDs) in question, and then executing an SFENCE.INVAL.IR instruction.

Some additional information was obtained through https://github.com/riscv/riscv-isa-manual/issues/1936

However, other instructions may still appear between SFENCE.W.INVAL and SFENCE.INVAL.IR.
> Translation of any memory accesses during that sequence are subject to the usual uncertainty as to which translation (among old and new ones) is used.

Moreover, these memory accesses are not entirely unpredictable either.
> Each subsequent memory access will unpredictably use either the old translation or the new translation. Other behaviors can't occur.
