### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A misaligned scalar store that crosses a 4 KiB page has committed but still waits in StoreMisalignBuffer (`s_block`) because the StoreQueue drains slowly. If the ROB then allocates more than RobSize (160 in KunminghuV2Config) further entries, a later redirect can clear the buffer. The StoreQueue then writes the upper half of the store to the physical page right after the lower page, instead of the translated upper page. The translated upper page is not written.

After writeback, a cross-page store waits in `s_block` until the StoreQueue dequeues it:

```scala
// src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:304-305
} .elsewhen(writeBack && isCrossPage) {
  bufferState := s_block
// src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:311-314
is (s_block) {
  when (io.sqControl.toStoreMisalignBuffer.doDeq) {
    bufferState := s_idle
    req_valid := false.B
```

The flush check does not depend on the state or on whether the store has committed:

```scala
// src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:641-645
val flush = req_valid && req.uop.robIdx.needFlush(io.redirect)
...
when (flush || s2_needRevoke) {
  bufferState := s_idle
  req_valid := false.B
```

`needFlush` is an age comparison with a wrap flag. Once the ROB has allocated more than RobSize entries after the store, the committed store's `robIdx` compares as younger than the redirect:

```scala
// src/main/scala/xiangshan/backend/rob/RobBundles.scala:210-212
def needFlush(redirect: Valid[Redirect]): Bool = {
  val flushItself = redirect.bits.flushItself() && this === redirect.bits.robIdx
  redirect.valid && (flushItself || isAfter(this, redirect.bits.robIdx))
```

The StoreQueue treats the store as cross-page only while the buffer entry is valid:

```scala
// src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:227
io.sqControl.toStoreQueue.crossPageWithHit := sameSqPtr && isCrossPage && req_valid
// src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1205
val isCross4KPage = io.maControl.toStoreQueue.crossPageWithHit
```

Without it, the upper half goes to the next 8-byte word after the lower physical address, not to the address the buffer translated:

```scala
// src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1251
val paddrHigh = Cat(paddrModule.io.rdata(0)(paddrModule.io.rdata(0).getWidth - 1, 3), 0.U(3.W)) + 8.U
// src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1287-1300
} .otherwise {
  ...
  else {
    dataBuffer.io.enq(i).bits.addr      := paddrHigh
```

### Expected behavior

The upper four bytes are written to the translated upper page, and the physical page after the lower page keeps its contents.

### Environment

- XiangShan: kunminghu-v2 `9c53db086a`, KunminghuV2Config, 1 core, Verilator emu; runs with and without difftest (NEMU).

### To Reproduce

[smb-crosspage-flush.zip](https://github.com/user-attachments/files/35227565/smb-crosspage-flush.zip)

Attachment: `smb-crosspage-flush.zip`. It contains `src/` (`rt.S` and the four case files), the images in `bin/`, `build.sh` (rebuilds the images with GCC and checks `SHA256SUMS`), and a short README.

The program runs in S mode with Sv39. The tested store is `sd 0x8877665544332211` to VA 0x100010FFC. VA 0x100010000 maps to PA 0x80048000 and VA 0x100011000 maps to PA 0x8004A000; the two pages are not physically adjacent, and PA 0x80049000 is not mapped. Before the store, 32 (or 48) older stores to a PBMT=NC page slow down the StoreQueue drain. After it, 40 groups of 48 `addi` and one taken forward branch provide the ROB allocation and the redirects. At the end the program executes `fence rw,rw` and prints three physical locations: `p` = PA 0x80048FF8, `q` = PA 0x80049000, `r` = PA 0x8004A000. Correct result: `p4433221111111111 q2222222222222222 r3333333388776655`.

| Image | Older stores | After the store | Role |
|---|---|---|---|
| `smbs2_ncbr` | 32, PBMT=NC | 40 x (48 `addi` + taken forward branch) | repro |
| `smbs2_nc48br` | 48, PBMT=NC | same | repro |
| `smbs2_ncnb` | 32, PBMT=NC | 2000 `addi`, no redirect | control |
| `smbs2_cbr` | 32, cacheable | same as `smbs2_ncbr` | control |

```bash
./build/emu -i bin/smbs2_ncbr.bin --no-diff -C 3000000
./build/emu -i bin/smbs2_ncbr.bin -C 3000000
```

| Image | `--no-diff` | with difftest |
|---|---|---|
| `smbs2_ncbr` | `HIT GOOD TRAP`; `p4433221111111111 q2222222288776655 r3333333333333333` | `Mismatch for store commits`: REF addr `0x8004a000`, DUT addr `0x80049000`, data `0x88776655`, mask `0x0f` |
| `smbs2_nc48br` | `HIT GOOD TRAP`; same `p q r` as above | same mismatch |
| `smbs2_ncnb` | `HIT GOOD TRAP`; correct `p q r` | `HIT GOOD TRAP` |
| `smbs2_cbr` | `HIT GOOD TRAP`; correct `p q r` | `HIT GOOD TRAP` |

### Additional context

_No response_
