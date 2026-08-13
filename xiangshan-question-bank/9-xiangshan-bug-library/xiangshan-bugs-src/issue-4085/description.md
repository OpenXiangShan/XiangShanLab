1. Only if no `pf/af` occurs can it be considered a `mmio`. Thus allowing a non-aligned Load to generate a misalign exception.
The store also suffers from this problem, but I will modify `StoreUnit` later in some other way

2. Prefetching shouldn't produce non-alignment, and I previously placed the logic for prefetching processing in the wrong place.
