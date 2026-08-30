to prevent selecting stale s1_hitEntry

i.e. we do tag comparison in s0, if in the same cycle t1 writes to entries, we can read a overwritten entry in s1 and treat it as a hit. This false-hit entry can have a `position` before `startPc`, causing adder overflow and equivalently predicts a oversized (size=position-start) fetch block. Eventually causing a ICache bank conflict and sending wrong instruction data to backend.

also change `entries(idx)` to `Mux1H(oh, entries)` for (maybe) better timing

a slight performance change is expected
