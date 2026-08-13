### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

The StoreQueue forward path can accept a cross-16B partial store-to-load forward as a safe full-overlap forward when the load has multiple matching in-flight stores.

The minimal sequence is:

```asm
sd   zero, 0(t0)
sd   zero, 8(t0)
sd   zero, 16(t0)
sd   zero, 24(t0)
fence rw, rw

li   t1, 0xaabbccdd
sw   t1, 20(t0)                 # older store: bytes 20..23

li   t2, 0x1122334455667788
sd   t2, 12(t0)                 # younger store: bytes 12..19, crosses the 16B boundary

ld   a0, 16(t0)                 # load bytes 16..23
li   t3, 0xaabbccdd11223344
bne  a0, t3, fail
```

For the `ld a0, 16(t0)`, sequential semantics require:

```text
bytes 16..19: from the younger sd 12(t0) = 0x11223344
bytes 20..23: from the older sw 20(t0)  = 0xaabbccdd
expected a0  = 0xaabbccdd11223344
```

The reproduced XiangShan run instead writes back:

```text
actual a0 = 0x0000000011223344
```

The target load's memory address is `0x800000d0` (`test_area + 16`). The load instruction PC is `0x80000054`.

Affected code path:

```scala
// src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
val s2OutMask     = ParallelLookUp(s2ByteSelectOffset, s2SelectMask) & s2LoadMaskEnd
val s2FullOverlap = (s2SelectDataEntry.byteMask & s2LoadMaskEnd) === s2LoadMaskEnd
val s2SafeForward = !s2MultiMatch || s2FullOverlap
s2Resp.bits.forwardInvalid := !s2SafeForward || s2Cross4KPage
```

`s2OutMask` is the selected store byte mask after rotation into the load coordinate system. In the failing case it is `0x000f`, so only the low four bytes of the 8-byte load are actually forwarded. However, `s2FullOverlap` compares the unrotated selected-store `byteMask` against `s2LoadMaskEnd`. For this cross-16B case, that unrotated comparison reports full overlap even though the rotated forward mask only covers half of the load. Consequently, `s2SafeForward` is asserted and `forwardInvalid` remains low, so the load is not replayed.

The current configuration makes this a real path, not an artificial configuration mismatch:

* `XSCoreParameters.VLEN = 128`, so StoreQueue forwarding uses a 16-byte (`VLEN/8`) coordinate.
* `SQDataEntryBundle.byteMask` is `(VLEN/8).W`.
* `TLMinimalConfig` has `StoreQueueForwardWithMask = true`.
* `NewStoreQueue.scala` explicitly handles `cross16Byte`, `s1Next16BMatchVec`, `s2ByteSelectOffset`, and rotated forward data/masks.


### Expected behavior

For a multi-match load, if the selected youngest store only covers part of the load after rotation into the load coordinate system, StoreQueue must not treat the forward as safe. It should replay the load or otherwise ensure that all bytes are supplied according to store age order.

In this PoC, the expected architectural value for `ld a0, 16(t0)` is:

```text
a0 = 0xaabbccdd11223344
```

StoreQueue should not allow:

```text
s2MultiMatch  = 1
s2LoadMaskEnd = 0x00ff
s2OutMask     = 0x000f
s2SafeForward = 1
forwardInvalid = 0
```

because this combination means the load needs 8 bytes, only 4 bytes are forwarded, another matching store exists, and yet the load is allowed to consume the partial result.


### Environment

* Software
  * RISC-V GCC used for the PoC workload: `riscv64-unknown-elf-gcc 15.1.0`
  * XiangShan checkout HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`

### To Reproduce

The attachment  [xiangshan-sq-cross16-forward-poc.zip](https://github.com/user-attachments/files/28091274/xiangshan-sq-cross16-forward-poc.zip) contains:

1. `cross16_multistore_forward.S`: minimal bare-metal PoC.
2. `cross16_stale_secret_forward.S`: variant that initializes old memory bytes to `0xdeadbeef` to demonstrate stale-data exposure.
3. `link.ld`: linker script placing `_start` at `0x80000000`.
4. `parse_cross16_vcd.py` and `parse_cross16_secret_vcd.py`: VCD monitors for the main and stale-data variants.
5. `monitor_repro_0_11550.json` and `monitor_secret_0_11700.json`: reproduced monitor results.
6. `cross16_repro_hier_16740_16770.{vcd,fst}` and `cross16_secret_hier_16738_16768.{vcd,fst}`: small hierarchy-preserving waveforms containing the relevant SQ/writeback signals around the failing event.
7. `run_repro_0_11550.log` and `run_secret_0_11700.log`: emulator logs.

Build the main PoC:

```bash
riscv64-unknown-elf-gcc \
  -nostdlib -nostartfiles \
  -march=rv64gc_zicsr_zifencei -mabi=lp64 \
  -T link.ld \
  -o cross16_multistore_forward.elf \
  cross16_multistore_forward.S

riscv64-unknown-elf-objcopy \
  -O binary \
  cross16_multistore_forward.elf \
  cross16_multistore_forward.bin

riscv64-unknown-elf-objdump \
  -dr cross16_multistore_forward.elf \
  > cross16_multistore_forward.objdump
```

Run the waveform-enabled XiangShan emulator:

```bash
  emu \
  -C 11550 -b 0 -e 11550 \
  -i cross16_multistore_forward.bin \
  --no-diff \
  --dump-wave-full \
  --wave-path=cross16_repro_0_11550.vcd \
  --force-dump-result \
  > run_repro_0_11550.log 2>&1
```

Parse the VCD:

```bash
python3 parse_cross16_vcd.py \
  cross16_repro_0_11550.vcd \
  --json-out monitor_repro_0_11550.json \
  > monitor_repro_0_11550.log 2>&1
```

The reproduced result is:

```text
predicate_result = reproduced
expected_load_data = 0xaabbccdd11223344
bad_writebacks = 0x0000000011223344, 0x0000000011223344
good_writebacks = []
```

At VCD time `16758`, the StoreQueue monitor sees:

```text
s2Valid        = 1
s2Resp_valid   = 1
s2ForwardValid = 1
s2MultiMatch   = 1
s2LoadMaskEnd  = 0x00ff
s2OutMask      = 0x000f
s2SafeForward  = 1
forwardInvalid = 0
s2LoadPaddr    = 0x800000d
s2LoadStart    = 0
```

At VCD time `16760`, integer writeback port 0 writes:

```text
toIntRf_valid                = 1
toIntRf_bits_isFromLoadUnit  = 1
toIntRf_bits_pdest           = 0x0f
toIntRf_bits_data            = 0x0000000011223344
toRob_bits_debugInfo_vaddr   = 0x800000d0
toRob_bits_debugInfo_paddr   = 0x800000d0
```

<img width="2610" height="430" alt="Image" src="https://github.com/user-attachments/assets/26241e38-aa6d-4b3f-9db5-dbbb7f1dec53" />

This connects the StoreQueue partial-forward decision to an architectural integer-register writeback for the target load.

### Additional context

The stale-data variant changes only the initial bytes at `test_area + 16` before the older `sw`:

```asm
li   t4, 0xdeadbeefcafebabe
sd   t4, 16(t0)
...
sw   t1, 20(t0)
sd   t2, 12(t0)
ld   a0, 16(t0)
```

Sequential semantics still require:

```text
a0 = 0xaabbccdd11223344
```

The reproduced stale-data variant instead writes back:

```text
a0 = 0xdeadbeef11223344
```

The stale high 32 bits should have been overwritten by the older `sw 20(t0)`. This shows that the bug can expose stale bytes architecturally when software relies on an in-flight store to overwrite previous memory contents.

This is not a branch-prediction or Spectre-style transient-execution issue. It is an out-of-order load/store forwarding correctness bug in the speculative memory-execution machinery. The incorrect value is not merely transient; it reaches integer-register writeback and changes the architectural path (`bne a0, t3, fail`).

In principle, Spike/NEMU difftest should also catch this: Spike executes the sequence sequentially and obtains `a0 = 0xaabbccdd11223344`, while the reproduced XiangShan run writes `a0 = 0x0000000011223344`. In this workspace, the available `build_vcd/emu` was used with `--no-diff` for waveform evidence; passing `--diff` to this particular binary prints the help text and exits, so I used VCD-based validation instead.
