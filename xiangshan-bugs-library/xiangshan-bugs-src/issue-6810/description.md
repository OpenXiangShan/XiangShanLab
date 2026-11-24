### Branch

`kunminghu-v3`

### Describe the bug

We observed a potential false-negative in the vector-state comparison of the NEMU-based DiffTest integration.

On the tested XiangShan RTL, several scalar-to-vector instructions produce incorrect results, but the current vector DiffTest path does not reliably expose these mismatches.

Independent memory readback and Spike comparisons confirm the incorrect RTL results.

**Observed behavior:**

- `vmv.v.x`, `vmv.s.x`, `vadd.vx`, and `vxor.vx` receive an incorrect zero scalar operand.
- Immediate and pure-vector operations execute correctly.
- The incorrect results are observable through memory readback.
- Direct vector-state DiffTest comparison is insufficient to detect these errors reliably in the tested configuration.

### Expected behavior

DiffTest should detect incorrect committed vector results or explicitly indicate when vector-state comparison is incomplete.

### Environment

- CPU: Intel Xeon 6731P (32 cores, 64 threads)
- Memory: 251 GiB
- OS: Ubuntu 24.04 LTS
- GCC: 13.3.0
- Java: OpenJDK 21.0.5
- Mill: 0.11.12
- XiangShan commit: `f85a1f71da8c1522035744c71e2cb266bfed1a7d`
- Configuration: `MinimalConfig`, VLEN=128
