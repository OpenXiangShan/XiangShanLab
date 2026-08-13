Following lr should be blocked when previous lr's resv_set is still valid, which means `lrsc_count > 0`.

In previous PR #3017 and #4117, `lrsc_count > 8` is used as block condition, and stop update `lrsc_count` when it reaches 8, fix it now.
