* Background
We use rdataPtr(0) and rdataPtr(1) to determine whether we need to set `vecExceptionFlag`. Unfortunately, when rdataPtr(0) is a request that is misaligned and cross-16Byte and has exception, the request need to split and occupy two write ports of dataBuffer, which lead to different perspectives: `rdataPtr` consider Vector Store can't `Deq`; `vecExceptionFlag` consider Vector Store can `Deq`. In conclusion, this issue will result in write data that should not be written.

---

* Example
1. vle16, Addr = 0xF, LMUL = 1/4, SEW = 16-bit, EEW = 16-bit, EMUL = 1/4, vstart = 0, vl = 2. 

First flow in Store Queue is misaligned and cross-16Byte and has exception, in order to store datas of vle16 (we will not store data to sbuffer when flow have exception, but need to `Deq`), we need to split flow and occupy two write ports of dataBuffer, which lead to last flow not `Deq`, but `vecExceptionFlag` not set, this situation results in writing second flow that should not to be written.

2. vloxei16, Addr = 0x0, LMUL = 1, SEW = 16-bit, EEW = 16-bit, EMUL = 1, vstart = 0, vl = 4, index0 = 0x0, index1 = 0x2, index2 = 0xF, index3 = 0x4.

The second flow have exception, `vecExceptionFlag` was set, when the third flow was split and occupied two write ports of dataBuffer, which led to last flow not `Deq`, but `vecExceptionFlag` was cancelled, this situation results in writing fourth flow that should not to be written.

---

This PR also fixes the store event of vector unit-stride store which is misaligned. However, In this PR we use a dirty implementation to align the reference module. Besides, this PR should not affect difftest for instructions other than the Unit-Stride instruction.

Add two new signals to the Sbuffer, `offset` and `start`. `offset` is address offset, `start` is the position of first element. 
e.g. 
1.  emul = 1, eew = 16-bit, address = 0xF1, vstart = 0, vl = 8. 
    This Unit-stride Store is inside 16-Byte.
    `offset` = 0x1, `start` = 0x1,
2. emul = 1,  eew = 16-bit, address = 0xFF, vstart = 0, vl = 8. 
    This request is cross 16-Byte, need to split into two request.
    (1) `offset` = 0xF, `start` = 0xF.
    (2) `offset` = 0xF, `start` = 0x0.
3. emul = 1, eew = 16-bit, address = 0x0, vstart = 0, vl = 8. 
    This Unit-stride Store is inside 16-Byte and aligned.
    `offset` = 0x0, `start` = 0x0,



**TODO**: refactor vector store event difftest.
