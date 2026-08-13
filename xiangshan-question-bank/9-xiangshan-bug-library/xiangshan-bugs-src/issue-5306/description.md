Breaking change to TageTableInfo config: `(NumTotalSets, HistLen)` -> `(Size, NumWays, HistLen)` (Size = NumWays * NumTotalSets = NumWays * NumBanks * NumSets)

Use `implicit val info` to pass tableInfo, so we can use `NumSets`, `NumWays`, etc. as usual.

NOTE: this also fixed a typo in `TageBaseAlignBank`, causing performance change:
```diff
-  private val t1_bankIdx  = getBankIndex(t1_startVAddr)
+  private val t1_bankIdx  = getBaseTableBankIndex(t1_startVAddr)
```
