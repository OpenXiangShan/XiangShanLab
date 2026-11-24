# [kunminghu-v3] uTAGE BypassShadowBuffer can drop the newest training update

## Before start

- [x] I have read the RISC-V ISA Manual and XiangShan Documents, and I believe this is a XiangShan RTL issue. (For the submitter to review.)
- [x] I searched previous uTAGE/BypassShadowBuffer issues and PRs and did not find this specific same-slot dirty-state conflict.
- [x] I reproduced the behavior on the real `BypassShadowBuffer` module with an original/fixed comparison.
- [x] If this report was generated with AI assistance, I have verified the correctness of its content.

## Branch and environment

`kunminghu-v3`

Current default-branch HEAD checked during follow-up: `e716c765824900ff78816b92ac827fd4aa2cab35`.

- Configuration: `DefaultConfig`-derived uTAGE parameters
- DUT: real `BypassShadowBuffer(512, 0, 4)`, matching the `MicroTageTable.scala` instantiation
- Chisel 7.13.0, firtool 1.149.0, OpenJDK 21, Linux x86_64
- Verilator module simulation with the original and fixed RTL variants

All main XiangShan Scala sources at `e716c765824900ff78816b92ac827fd4aa2cab35` were freshly compiled into a separate classes directory. The real module and both simulators were then regenerated. Unchanged pinned dependency artifacts were reused. The original still produces no additional writeback for A; the fixed variant writes the updated counter 4.

Source location:

[`BypassShadowBuffer.scala`](https://github.com/OpenXiangShan/XiangShan/blob/e716c765824900ff78816b92ac827fd4aa2cab35/src/main/scala/xiangshan/frontend/bpu/utage/BypassShadowBuffer.scala#L248-L259)

## Describe the bug

`BypassShadowBuffer` coalesces recent uTAGE training updates before writing them back to SRAM. When a new training update hits an old shadow entry in the same slot currently pointed to by `enqPtr`, the same-cycle dirty updates conflict:

```scala
when(t1_hasWrite) {
  statusEntries(enqPtr.value).valid := true.B
  statusEntries(enqPtr.value).dirty := true.B
}

when(t1_hasWrite && t1_hasHit) {
  statusEntries(t1_cleanId).dirty := false.B
}
```

If `t1_cleanId == enqPtr.value`, both assignments target the same `dirty` bit. The second assignment clears the dirty bit set by the first assignment. The newly merged training value is then never selected for SRAM writeback.

This violates the buffer's own write-coalescing intent: the newest update should eventually reach the backing SRAM entry.

## Expected behavior

When a training update hits an existing shadow entry, the newest merged value remains dirty and is eventually written to SRAM, including when the hit entry and `enqPtr` refer to the same slot.

## Reproduction

The DUT is the real upstream `BypassShadowBuffer(512, 0, 4)`, matching the `MicroTageTable.scala` instantiation. The test drives the real training and allocation ports and records the SRAM writeback index and counter value.

Sequence:

1. Train index A with `taken=0`.
2. Train seven other indices so the buffer pointer wraps to A's slot.
3. Train A again with `taken=1`.

| Variant | Result after retraining A |
|---|---|
| Original | `0` additional SRAM writebacks; latest counter remains `3` — **FAIL** |
| One-line fix | `1` SRAM writeback; latest counter becomes `4` — **PASS** |

The original and fixed variants use the same real module and testbench. The failure is not a hand-written replacement-policy model. The same result was reproduced after rebuilding the latest HEAD listed above.

## Suggested fix

Do not clear the dirty bit when the clean target is the same slot as the new enqueue slot:

```diff
when(t1_hasWrite && t1_hasHit) {
-  statusEntries(t1_cleanId).dirty := false.B
+  when(t1_cleanId =/= enqPtr.value) {
+    statusEntries(t1_cleanId).dirty := false.B
+  }
}
```

The exact implementation can be chosen by the maintainers; the required behavior is that the newest same-slot update remains dirty.

## Scope and limitations

- This confirms a real module-level training-state loss in the default uTAGE path.
- The demonstrated consequence is predictor training loss and potentially reduced prediction quality; no IPC or workload-frequency measurement is included.
- No architectural data corruption or incorrect architectural execution is claimed.
- The test does not run the complete frontend or full core.

## Related work

Searches for `BypassShadowBuffer`, `MicroTage`, `uTAGE`, and the same-slot dirty-state pattern found related items such as #5513, #6048, #6155, #6159, and PRs #5513/#5517/#6415/#6518, but none covers this same-slot dirty-bit conflict. No open PR touching `BypassShadowBuffer.scala` was found during the review.

Tests and report prepared with AI assistance (OpenCode / GPT-6-Astra).
