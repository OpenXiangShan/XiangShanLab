This PR adds hardware synthesizable three-level categorized TopDown performance counters.
Level-1: Retiring, Frontend Bound, Bad Speculation, Backend Bound.
Level-2: Fetch Latency Bound, Fetch Bandwidth Bound, Branch Missprediction, machine clears, Core Bound, Memory Bound.
Leval-3: L1 Bound, L2 Bound, L3 Bound, Mem Bound, Store Bound.
