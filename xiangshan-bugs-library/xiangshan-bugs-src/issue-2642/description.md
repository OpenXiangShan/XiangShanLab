**Describe the bug**
When executing the **fmadd.d fa5, ft0, fa3, fa1, rtz i**nstruction in the XS, the resulting value in fa5 for a NaN outcome does not conform to the expected canonical NaN representation for double-precision floating-point numbers as specified in the RISC-V ISA. Instead of getting the canonical NaN value (0x7ff8000000000000), a different NaN value is observed.

**To Reproduce**

- Initialize the ft0 and fa3 registers with e.g.. 0x00092afc56e7eaeb, and fa1 with e.g.. 0xffffffff00000000.
- Execute the **fmadd.d fa5, ft0, fa3, fa1, rtz instruction.**
- Observe the value in the fa5 register.

**Expected behavior**
The expected result for a NaN outcome in double-precision floating-point operation, according to the IEEE 754
standard and RISC-V ISA, should be the canonical NaN value. For double-precision, this is 0x7ff8000000000000, which represents a NaN with a positive sign, all exponent bits set to 1, and the most significant bit of the significand set to 1 (quiet bit), with all other significand bits clear.

**Screenshots**
![image](https://github.com/OpenXiangShan/XiangShan/assets/62980522/f2367594-b07e-433f-b6b4-a42524e5d273)

**Environment (optional, if necessary):**

- OS: Ubuntu 22.04.3 LTS
- Compiler: gcc 11.4.0

**Additional context**
None
