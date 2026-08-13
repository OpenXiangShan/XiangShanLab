Fix 3 bugs:
1. `t1_hitMask` is not passed to replacer, when indirect jumps mispredicted & updated to mbtb, replacer victim way (instead of hit way) is touched, causing PLRU strategy to fail.
   - now we pass actual written wayMask to replacer
2. `t1_hitMask` can be multi-hit, replacer train touch cannot support this. Also, writeBuffer may fail due to trying to write multiple way.
   - uses a PriorityEncoderOH to fix this
3. consider an attribute mismatch as a miss, allocating a new entry for it, causing multi-hit.
   - consider it a hit, and overwrite entry

Also:
- Flush the second (instead of the first) hit entry when detecting multi-hit, to prevent flushing the LRU entry.
