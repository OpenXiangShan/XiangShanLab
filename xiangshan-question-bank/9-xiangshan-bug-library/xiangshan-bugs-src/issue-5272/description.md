1. Removed the outdated fdpmonitor and migrated to prefetchmonitor
2. Added performance counters such as `prefetch_miss` and `load_miss`
3. Added `nack_prefetch` to count the number of prefetch requests that were nacked
4. Fixed some statistical bugs, such as the `good_prefetch`
