spec:

> Since CSRRS and CSRRC perform a read-modify-write operation, any bits that read as a different value to their underlying value may be modified by these instructions even if the corresponding bit is not set in rs1. For example, pmpaddrn[G-1] may have an underlying value of 1 but read as 0. Executing CSRRC or CSRRS to modify a different bit will cause 0 to be read from pmpaddrn[G-1] and then written back, updating the underlying value to 0.

For `PMP` and `PMA`, `CSRRS/CSRRC` operates on the `rdata` of `PMP` and `PMA`, rather than the values of registers.
