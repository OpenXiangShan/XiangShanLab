### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug


When a VS-mode store instruction causes a G-stage (second-stage) page fault and the trap is taken to M-mode or HS-mode, the `mtinst` (M-level, CSR 0x34A) or `htinst` (HS-level, CSR 0x64A) register should encode a transformed pseudo-instruction that allows the hypervisor to identify the access as a **store**. According to the RISC-V Privileged Architecture specification (Section 18.6.3), for store-type accesses the transformed instruction must have bit 5 set (`0x0020`).
Currently, both `TrapEntryMEvent.scala` and `TrapEntryHSEvent.scala` hardcode the value `0x3000` regardless of whether the access is a load or a store. The correct value for a store G-stage page fault should be `0x3020`.

**Affected RTL files:**
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala:134`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala:147`
Both contain:
```scala
out.mtinst.bits.ALL := Mux(
  isFetchGuestExcp && in.trapIsForVSnonLeafPTE ||
  isLSGuestExcp && in.memExceptionIsForVSnonLeafPTE,
  0x3000.U,   // always load encoding; 0x3020.U for store
  0.U
)
```
The `isLSGuestExcp` covers both load (`EX_LGPF=21`) and store (`EX_SGPF=23`) guest page faults, but the encoding always produces the load form.

**Logs from NEMU reference model (same bug exists in NEMU):**
The PoC outputs (when mtinst is correctly read at CSR 0x34A):
```
[*] mcause = 0x17 (EX_SGPF=23, store guest-page fault)
[*] mtinst = 0x3000
[*] BUG CONFIRMED: mtinst = 0x3000, bit 5 CLEAR
```
The expected value is `0x3020` (bit 5 = 0x0020 set for store).

### Expected behavior

Per the RISC-V Privileged Architecture specification (Section 5.6.3, Transformed Instruction or Pseudoinstruction for mtinst or htinst):
- When a G-stage page fault is caused by a **load-type** access (EX_LGPF), `mtinst`/`htinst` should encode `0x3000` (transformed store with no store bit).
- When a G-stage page fault is caused by a **store-type** access (EX_SGPF), `mtinst`/`htinst` should encode `0x3020` (transformed store with bit 5 set).
Bit 5 (`0x0020`) serves as the store/AMO indicator in the transformed instruction encoding.

### Environment

- **Hardware**: x86_64, Intel Xeon
- **OS**: Ubuntu 20.04
- **gcc**: 9.4.0
- **Java**: OpenJDK 11.0.26
- **XiangShan**: commit `7be121c71ff0534982ee0521e0b7fe8f2605a67c`
- **NEMU**: commit `53bcb5686f8fd05248ae98546b7dc04bdca1bbb0`
- **Spike**: /opt/riscv/bin/spike (Spike 1.1.1-dev)


### To Reproduce

### Step 1: Obtain the PoC
The PoC is located at `/testcase/gpf-htinst/` on the development machine. It consists of:
- `Makefile` — AM-based build
- `src/main.c` — M-mode initialization, G-stage (hgatp) and VS-stage (vsatp) page table setup
- `src/entry.S` — M-mode trap handler that reads mtinst (0x34A), and VS-mode test code
**Build:**
```bash
source /xs-env/env.sh
cd /testcase/gpf-htinst
make ARCH=riscv64-xs
```
This produces `build/gpf-htinst-riscv64-xs.elf`.
### Step 2: Run on XiangShan emu with NEMU difftest
```bash
cd $NOOP_HOME
./build/emu -i /testcase/gpf-htinst/build/gpf-htinst-riscv64-xs.elf \
  --diff=$NOOP_HOME/ready-to-run/riscv64-nemu-interpreter-so \
  -C 100000
```
### Step 3: Expected output
```
[*] PoC: tinst store bit 5 missing for G-stage faults
[*] hgatp = 0x8000000000080008
[*] vsatp = 0x8000000000080004
[*] Entering VS-mode...
[*] Back in M-mode after exception
[*] mcause = 0x17 (NEMU EX_SGPF=0x17)
[*] mtinst = 0x3000          <-- should be 0x3020
[*] Store guest-page fault confirmed
[*] BUG CONFIRMED: mtinst = 0x3000, bit 5 CLEAR
HIT BAD TRAP at pc = 0x80000340
```
The test binary is available at `/testcase/gpf-htinst/build/gpf-htinst-riscv64-xs.elf`.
Source files are at `/testcase/gpf-htinst/src/main.c` and `/testcase/gpf-htinst/src/entry.S`.

[gpf-htinst-poc.tar.gz](https://github.com/user-attachments/files/28243706/gpf-htinst-poc.tar.gz)

### Additional context

### Root cause
In `TrapEntryMEvent.scala:134` and `TrapEntryHSEvent.scala:147`, the `mtinst`/`htinst` assignment uses the constant `0x3000.U` unconditionally. The encoding does not check whether the originating access was a store (EX_SGPF) versus a load (EX_LGPF).

The fix should differentiate between load and store:
```scala
// TrapEntryMEvent.scala, TrapEntryHSEvent.scala
val isStoreGPF = in.memExceptionIsForVSnonLeafPTE && highPrioTrapNO === ExceptionNO.EX_SGPF.U
out.mtinst.bits.ALL := Mux(
  isFetchGuestExcp && in.trapIsForVSnonLeafPTE ||
  isLSGuestExcp && in.memExceptionIsForVSnonLeafPTE,
  Mux(isStoreGPF, 0x3020.U, 0x3000.U),
  0.U
)
```

### Same bug in NEMU
The NEMU reference model (`/xs-env/NEMU/src/isa/riscv64/system/mmu.c:182`) has the identical bug:
```c
tinst |= is_support_vs ? 0x3000 : 0;
// Missing:
// if (type == MEM_TYPE_WRITE || cpu.amo) tinst |= 0x0020;
```

### Impact
Any hypervisor that relies on `mtinst` bit 5 to distinguish load vs. store guest-page-faults will receive incorrect information. For example, KVM RISC-V's MMIO emulation path reads `htinst` to decode the access type. With this bug, store MMIO accesses would be decoded as loads, potentially causing silent data corruption.
