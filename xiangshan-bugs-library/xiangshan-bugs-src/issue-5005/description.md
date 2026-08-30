A situation occurred where `prefetch_hit` > `total_prefetch`. It was found that there is a time lag in updating `prefetchArray`, but `prefetch_hit` was not properly filtered and counted.
