### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A same-page store that crosses a 16-byte VWord boundary can fail to nuke a younger overlapping load. The StoreUnit sends the store nuke mask in the store/lower-VWord coordinate, while the LoadUnit OctaWord paddr matching also allows the next VWord but keeps the later mask intersection in the unshifted coordinate.

The minimal sequence is:

```asm
la      s0, test_buf             # test_buf = 0x80001080
addi    s1, s0, 12               # s1 = 0x8000108c
li      s2, 0x1122334455667788

# Delay store address so the younger load can execute first.
li      t5, 1
divu    t2, s1, t5
divu    t2, t2, t5
divu    t2, t2, t5
divu    t2, t2, t5
divu    t2, t2, t5
divu    t2, t2, t5

sd      s2, 0(t2)                # older store: 0x8000108c..0x80001093
lwu     a0, 16(s0)               # younger load: 0x80001090..0x80001093

li      a1, 0x11223344
bne     a0, a1, fail
```

For the `lwu a0, 16(s0)`, sequential semantics require:

```text
store addr       = 0x8000108c
load addr        = 0x80001090
store data       = 0x1122334455667788
expected a0      = 0x11223344
```

Little-endian byte layout of the store is:

```text
0x8000108c: 0x88
0x8000108d: 0x77
0x8000108e: 0x66
0x8000108f: 0x55
0x80001090: 0x44
0x80001091: 0x33
0x80001092: 0x22
0x80001093: 0x11
```

The reproduced XiangShan run instead writes back:

```text
default PoC actual a0 = 0x00000000
stale-data PoC actual a0 = 0xdeadbeef
```

The target load's memory address is `0x80001090`. In the default PoC the target load instruction PC is `0x80000060`; in the stale-data variant it is `0x80000078`.

The affected code path appears to be the StoreUnit-to-LoadUnit store-load nuke check, not the StoreQueue forward path in #5998.

StoreUnit sends an OctaWord nuke for same-page cross-16B stores:

```scala
// src/main/scala/xiangshan/mem/pipeline/NewStoreUnit.scala
val nukeQueryReq = Wire(new StoreNukeQueryReq)
nukeQueryReq.robIdx := robIdx
nukeQueryReq.paddr := paddr
nukeQueryReq.mask := mask
nukeQueryReq.matchType := Mux(
  isCbo,
  StLdNukeMatchType.CacheLine,
  Mux(
    cross16Byte && !cross4KPage,
    StLdNukeMatchType.OctaWord,
    StLdNukeMatchType.Normal
  )
)
```

LoadUnit then allows OctaWord paddr matching against either the store VWord or the next VWord:

```scala
// src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
def nukePAddrMatch(storePAddr: UInt, storeMatchType: UInt, loadPAddr: UInt): Bool = {
  val storeVWordAddr = storePAddr >> DCacheVWordOffset
  val loadVWordAddr = loadPAddr >> DCacheVWordOffset
  Mux(
    StLdNukeMatchType.isCacheLine(storeMatchType),
    (storePAddr >> blockOffBits) === (loadPAddr >> blockOffBits),
    Mux(
      StLdNukeMatchType.isOctaWord(storeMatchType),
      storeVWordAddr === loadVWordAddr || (storeVWordAddr + 1.U) === loadVWordAddr,
      storeVWordAddr === loadVWordAddr
    )
  )
}
```

However, the mask match is still a direct unshifted intersection:

```scala
// src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
val nukePAddrMatches = nukeQueryReqs.map(req => nukePAddrMatch(req.paddr, req.matchType, paddr))
val nukeStoreOlders = nukeQueryReqs.map(req => isAfter(robIdx, req.robIdx))
val nukeMaskMatches = nukeQueryReqs.map(req => (req.mask & in.mask).orR)
val nuke = Cat((nukeQueryValids lazyZip nukePAddrMatches lazyZip nukeStoreOlders lazyZip nukeMaskMatches).map {
  case (valid, paddrMatch, storeOlder, maskMatch) => valid && paddrMatch && storeOlder && maskMatch
}).orR && paddrEffective
```

In the failing case:

```text
store nuke paddr = 0x8000108c
store nuke mask  = 0xf000
store matchType  = 1 (OctaWord)

load paddr       = 0x80001090
load mask        = 0x000f

paddr match      = true, because OctaWord allows store VWord + 1
mask match       = false, because 0xf000 & 0x000f == 0
```

Physically, the store and load overlap at `0x80001090..0x80001093`. The miss happens because the store mask is still in the lower 16-byte coordinate while the load mask is in the upper VWord coordinate. The nuke should either shift the mask for the upper VWord match or otherwise check overlap in one consistent byte coordinate.


### Expected behavior

For a younger load that overlaps an older same-page cross-16B store, LoadUnit must either obtain the architecturally correct bytes or be nuked/replayed.

In this PoC, the expected architectural value for `lwu a0, 16(s0)` is:

```text
a0 = 0x11223344
```

LoadUnit should not allow this combination to pass without rollback:

```text
store nuke valid = 1
store paddr      = 0x8000108c
store mask       = 0xf000
store matchType  = OctaWord
load paddr       = 0x80001090
load mask        = 0x000f
store older      = 1
rollback/nuke    = 0
```

### Environment

* Software
  * RISC-V GCC used for the PoC workload: `riscv64-unknown-elf-gcc 15.1.0`
  * XiangShan checkout branch: `kunminghu-v3`
  * XiangShan checkout HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`
  * Waveform-enabled emulator used for reproduction: `XiangShan/build_vcd/emu`, TLMinimalConfig


### To Reproduce

The attachment [xiangshan-cross16-octaword-nuke-mask-default-fst.zip](https://github.com/user-attachments/files/28131599/xiangshan-cross16-octaword-nuke-mask-default-fst.zip) package contains:

1. `poc.S`: minimal bare-metal PoC.
2. `poc_stale.S`: stale-data variant that initializes `test_buf + 16` to `0xdeadbeef` before the target store/load sequence.
3. `linker.ld`: linker script placing `_start` at `0x80000000`.
4. `run_poc.sh`: build/run/parse helper.
5. `parse_cross16_nuke_vcd.py`: VCD monitor for StoreUnit nuke query, target load writeback, and rollback absence.
6. `poc_7000_15000.monitor.json` and `poc_stale_7000_15000.monitor.json`: reproduced monitor results.
7. `poc_7000_15000.{vcd,fst}` and `poc_stale_7000_15000.{vcd,fst}`: waveforms containing the relevant nuke/writeback/rollback signals.
8. `poc_7000_15000.run.log` and `poc_stale_7000_15000.run.log`: emulator logs.

Build the main PoC:

```bash
riscv64-unknown-elf-gcc \
  -march=rv64gc \
  -mabi=lp64 \
  -mcmodel=medany \
  -nostdlib \
  -nostartfiles \
  -T linker.ld \
  -o poc.elf \
  poc.S

riscv64-unknown-elf-objcopy -O binary poc.elf poc.bin
riscv64-unknown-elf-objdump -d -s poc.elf > poc.objdump
```

Run the waveform-enabled XiangShan emulator:

```bash
emu \
  --max-cycles=17000 \
  -b 7000 \
  -e 15000 \
  --dump-wave-full \
  --wave-path=poc_7000_15000.vcd \
  -i poc.bin \
  > poc_7000_15000.run.log 2>&1
```

Parse the VCD:

```bash
python3 ./parse_cross16_nuke_vcd.py \
  poc_7000_15000.vcd \
  poc_7000_15000.monitor.json \
  > poc_7000_15000.monitor.log 2>&1
```

The reproduced result is:

```text
predicate_result = reproduced
expected_load_data = 0x11223344
target store nuke = time 16798, paddr 0x8000108c, mask 0xf000, matchType 1, rob [0,149]
target load wb    = time 16798, paddr 0x80001090, data 0x00000000, rob [0,150]
rollbacks         = []
```

### Additional context

The stale-data variant [xiangshan-cross16-octaword-nuke-mask-stale-fst.zip](https://github.com/user-attachments/files/28131602/xiangshan-cross16-octaword-nuke-mask-stale-fst.zip) changes only the initial bytes at `test_buf + 16` before the target store/load race:

```asm
li      t3, 0xdeadbeef
sw      t3, 16(s0)
fence   rw, rw
...
sd      s2, 0(t2)
lwu     a0, 16(s0)
```

Sequential semantics still require:

```text
a0 = 0x11223344
```

The reproduced stale-data variant instead writes back:

```text
a0 = 0xdeadbeef
```

Relevant stale-data waveform events:

```text
target load wb    = time 16896, paddr 0x80001090, data 0xdeadbeef, rob [0,155]
target store nuke = time 16990, paddr 0x8000108c, mask 0xf000, matchType 1, rob [0,154]
rollbacks         = []
```

This shows that the bug can expose stale bytes architecturally when software relies on an older in-flight store to overwrite previous memory contents.

The stale-data variant also shows the wrong value changing architectural control flow:

```asm
80000078 <target_load>:
    80000078: 01046503   lwu   a0,16(s0)
    8000007c: 112235b7   lui   a1,0x11223
    80000080: 3445859b   addiw a1,a1,836 # 11223344
    80000084: 00b51863   bne   a0,a1,80000094 <fail>

80000094 <fail>:
    80000094: 000011b7   lui   gp,0x1
    80000098: bad1819b   addiw gp,gp,-1107 # bad
```

Waveform evidence for executing the fail path:

```text
time 16902/16903:
  backend redirect target = 0x80000094
  taken = 1
  isMisPred = 1

time 16928/16929:
  TOP.SimTop.cpu.l_soc.core_with_l2.core.backend._inner_intRegion_io_wbDataPathToCtrlBlock_writeback_6_valid             = 1
  TOP.SimTop.cpu.l_soc.core_with_l2.core.backend._inner_intRegion_io_wbDataPathToCtrlBlock_writeback_6_bits_robIdx_flag  = 0
  TOP.SimTop.cpu.l_soc.core_with_l2.core.backend._inner_intRegion_io_wbDataPathToCtrlBlock_writeback_6_bits_data         = 0x0000000000000bad

time 17000/17001:
  TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock._rob_io_commits_commitValid_5     = 1
  TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock._rob_io_commits_robIdx_5_flag     = 0
```

It is an out-of-order load/store ordering correctness bug in the speculative memory-execution machinery. The incorrect value is not merely transient; it reaches integer-register writeback, changes the branch outcome, and reaches the fail-path `gp = 0xbad` writeback/commit in the stale-data run.
