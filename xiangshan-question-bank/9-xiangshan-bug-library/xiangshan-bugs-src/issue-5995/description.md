### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

## Summary

`HLVX.HU/WU` correctly uses execute permission during page-table permission checks, but the final physical PMA/PMP check is still issued as a normal load request. As a result, a region configured as readable but non-executable (`R=1, X=0`) can still be read by `HLVX`, instead of raising a load access fault.

This violates the RISC-V H-extension requirement that `HLVX` uses execute permission semantics while still reporting load exceptions.

## Environment

- Branch: `kunminghu-v2`
- Commit: `4d1d56db9374d8163c1475e0ebf41265ec85d240`
- Config: `XSNoCTopConfig --enable-difftest`
- ISA area: RV64 + H extension, PMA/PMP, MMU/TLB load path

## Expected behavior

For `HLVX.HU/WU` targeting a final physical region configured as `R=1, X=0`:

- The access should not return data.
- The core should raise load access fault.
- `mcause` should be `5`.
- The exception should remain a load exception, not an instruction exception.

### Expected behavior

## Actual behavior

Before the fix, the IT observes that `HLVX` reads the target bytes successfully and no trap is taken:

```text
trap_count=0 mcause=0 mtval=0x0 mtval2=0x0 htval=0x0 hlvx=0xc33ca55a
FAIL hlvx bypassed PMA execute permission
HIT BAD TRAP at pc = 0x800002ca
```

The result is deterministic across repeated difftest-enabled runs.

### Environment

- Hardware
  - CPU:
  - Memory (GB):
  - Storage (GB):
- Software
  - Operating system:
  - gcc version: <!-- run `gcc --version 2>&1 | head -n 1` to get the version -->
  - clang version: <!-- run `clang --version 2>&1 | head -n 1` to get the version, only needed when you use clang -->
  - java version: <!-- run `java -version 2>&1 | head -n 1` to get the version -->
  - mill version: <!-- run `mill -i --version 2>&1 | head -n 1` to get the version -->
- Repo
  - XiangShan commit id: ``
  - NEMU commit id (if difftest failed with NEMU): ``
  - SPIKE commit id (if difftest failed with SPIKE): ``
- Build & Run
  - Build command: ``
  - Run command (if applicable): ``
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

## Minimal reproducer

The reproducer performs the following sequence:

1. Allocate a 4 KiB aligned target page and write known bytes to it.
2. Program a PMA entry as NAPOT 4 KiB over the target page with `R=1, W=1, C=1, X=0`.
3. Clear `hstatus.SPVP` so the effective HLVX virtual privilege is VU under bare translation.
4. Execute `hlvx.wu a0, (a1)` encoded as `.word 0x6835c573`.
5. Fail if no load access fault is observed.

Key test snippet:

```c
write_pmaaddr0(pma_napot_4kb((uintptr_t)target_page));
write_pmacfg0((old_pmacfg0 & ~0xffull) | PMA_C | PMA_A_NAPOT | PMA_W | PMA_R);
write_hstatus(old_hstatus & ~(1ull << 8));
asm volatile("fence rw, rw" ::: "memory");

hlvx_value = do_hlvx_wu((uintptr_t)target_page);

if (trap_count == 0) {
  printf("FAIL hlvx bypassed PMA execute permission\n");
  assert(0);
}
if (trap_mcause != 5) {
  printf("FAIL unexpected hlvx trap cause\n");
  assert(0);
}
```

## Reproduction commands

Build the AM workload:

```sh
source <env-setup-script>
make -C <test-dir> ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=0 clean image
```

Run on XiangShan emu with difftest enabled:

```sh
<emu> -i <test-image>.bin --diff=<nemu-ref-so> --max-instr=500000
```

A failing pre-fix run prints:

```text
Difftest enabled
trap_count=0 ... hlvx=0xc33ca55a
FAIL hlvx bypassed PMA execute permission
HIT BAD TRAP
```

## Root cause

The HLVX intent is lost before the final PMA/PMP check.

Relevant design chain:

1. `DecodeUnit` decodes `HLVX_HU/WU` to load-unit operations `LSUOpType.hlvxhu/hlvxwu`.
2. `LoadUnit` sends the TLB request with `cmd = TlbCmd.read` and carries HLVX intent separately through `req.bits.hlvx`.
3. `TLB` uses `hlvx` correctly for page-table permission checks, so HLVX checks `X` at the translation stage.
4. The old final PMP/PMA request path forwards only `cmd/addr/size`, not the HLVX intent.
5. `PMA` and `PMP` check execute permission only for `TlbCmd.isExec(cmd)`.

Because HLVX reaches PMA/PMP as `TlbCmd.read`, final physical permission checking validates `R` but ignores `X`.

## Why this is a real PMA/PMP issue

PMA/PMP being configurable is expected. The bug is that once software configures a final physical region as `R=1, X=0`, hardware must enforce that attribute. A normal load may read the region, but `HLVX` must also require execute permission and therefore must fault.

This is not a page-table issue: the reproducer uses bare translation and targets the final physical PMA/PMP permission check.

## Suggested fix

Carry HLVX intent into the final PMA/PMP checker while preserving load exception semantics:

- Add an `hlvx` sideband to the final PMP/PMA request bundle.
- Forward `req_out(i).hlvx` from TLB into the PMP/PMA checker.
- Treat `hlvx && !cfg.x` as a load access fault (`resp.ld`).
- Default `hlvx` to `false` for all non-HLVX request sources.

Do not simply change HLVX final checking to `TlbCmd.exec`; that would report instruction access fault instead of load access fault.

## Fix validation

After the local fix, the DUT raises the expected load access fault:

```text
trap_count=1 mcause=5 mtval=0x80002000 mtval2=0x0 htval=0x0 hlvx=0x15
hlvx-pma-xperm pass
HIT GOOD TRAP
```

With difftest enabled, the fixed DUT traps while the current reference simulator does not implement this dynamic PMA CSR update, so the expected mismatch is:

```text
mcause different ... right = 0x0, wrong = 0x5
mtval different ... right = 0x0, wrong = 0x80002000
```

The fixed DUT self-check passes when difftest is disabled.

## Impact

Software may rely on PMA/PMP attributes to make a physical region readable but non-executable. If `HLVX` can still read that region, the core violates the H-extension protection contract and may expose bytes from memory that software intended to be non-executable.

## Evidence

- Three deterministic pre-fix runs show `trap_count=0` and `hlvx=0xc33ca55a`.
- Two post-fix self-check runs show `trap_count=1`, `mcause=5`, and `HIT GOOD TRAP`.
- The source chain shows HLVX intent is used in TLB permission checks but was not forwarded to final PMA/PMP checks.


### Additional context

_No response_
