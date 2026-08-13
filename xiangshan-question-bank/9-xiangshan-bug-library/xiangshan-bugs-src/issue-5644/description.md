When G-stage translation is enabled, the format of the final paddr is like this:

```
| ===== PPN ====== |
| 55 .. 14 | 13 12 | 11 .. 3 | 2 .. 0 |
           | ==== offset === |
```

Other than S/VS-stage, the offset of G-stage is 11-bit rather than 9-bit, which means there is 2 bits overlap between the offset and the PPN.

However, the overlap bits (paddr[13:12]) could only form PPN or offset, but not both:

* When translation just starts (level=2 when Sv39x4, level=3 when Sv48x4):
  * PPN is from CSR hgatp, required to be aligned to 16-KiB
    * PPN[1:0] are always 0, so the two bits from PPN are always 0

* When translation is in the middle (not highest level):
  * offset is from the original gpaddr and is just 9-bit width
    * offset[10:9] are always 0, so the two bits from offset are always 0

Therefore, we could just use bitwise-OR to generate the final paddr, rather than use adder to add them together, which could save some critical path delay.
