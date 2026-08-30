## Motivation

Currently, we lack performance counters to evaluate `StoreSet` prediction accuracy, making it difficult to determine whether `StoreSet` introduces additional performance bottlenecks. This PR adds a set of accuracy counters triggered on `StoreSet` hit, together with `ChiselDB` traces covering prediction, training, and load execution. Based on the resulting analysis, this PR also fixes two performance issues: stale strict predictions are now cleared through a fast periodic refresh mechanism, and strict predictions are filtered by `LFST` when only one un-issued store remains and the normal `LFST` dependency is sufficient.


## Modified

* Move `SSIT` strict bits into a dedicated register array and start an aging window when strict training occurs. Clear all strict state in one cycle when the 8192-cycle window expires, enabling fast refresh without walking the `SSIT` entries.

* Count un-issued stores of the same `SSID` in `LFST`, including older stores in the current dispatch bundle. Keep the strict attribute only when more than one candidate store remains; when `LFST` already identifies a single latest dependency, filter strict while preserving the normal wait.

* Add `StoreSet` `ChiselDB` traces and address-match performance counters to observe prediction, training, refresh, filtering, and replay behavior.

## Performance

<img width="519" height="996" alt="image" src="https://github.com/user-attachments/assets/7533d9b5-cbfb-43a5-b4b1-547ce9b4d9c3" />
