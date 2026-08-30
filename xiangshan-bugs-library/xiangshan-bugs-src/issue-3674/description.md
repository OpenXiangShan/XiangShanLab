In our design, when tlb returns miss, the exception returned by TLB is considered invalid. However, for the case where an exception related to high address truncation happens (preaf | prepf | pregpf), the returned exception message should be considered valid by tlb.miss = false.B

Also, in this condition, gpaddr should be vaddr.
