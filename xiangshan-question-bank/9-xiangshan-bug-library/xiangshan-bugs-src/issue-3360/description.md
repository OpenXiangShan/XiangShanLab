* The injected interrupts for HS mode can set some bits in mIRVec and hsIRVec.
* `mIRVec` holds the highest priority interrupt numbered from 1 to 63. Only interrupt 1-13 can trap in M mode. And interrupt 14-63 must trap in HS mode or VS mode, since bits in mideleg(63,14) are read-only 0.
* `hsIRVec` holds the mip parts(by mIRVec & mideleg) and mvip parts(by mIRVec & ~mideleg & mvien) interrupts.
* `vsIRVec` holds the sip|hip parts(by hsIRVec & hideleg) and hvip parts(by hsIRVec & ~hideleg & hvien) interrupts.
