perf(DCache): optimize L1DCache set indexing with configurable `modeId`

**Problem:**
 - Original vaddr[13:6] indexing caused frequent evictions in bwaves 
   due to sequential access patterns

**Solution:**
1. New optimized mode (modeId=1， default):
   - set[7:6] = hash(vaddr[47:12])
   - set[5:0] = vaddr[11:6]
   - alias = hash(vaddr[47:12])
   
   Benchmark results:
   - 10.64% performance gain in bwaves

2. Configurable modes:
   - modeId=1 ( default ): optimized mode
   - modeId=2: Original vaddr[13:6] indexing (vaddr[13:12] as alias)

The configurable design allows:
1. Immediate performance benefit through modeId=2
2. Flexibility for future indexing schemes
3. Safe fallback to original behavior

<img width="524" alt="2f1cb6f068c5bd846831dce4dced123" src="https://github.com/user-attachments/assets/b5e3384c-5c91-4a52-82ba-a72ecd080555" />
