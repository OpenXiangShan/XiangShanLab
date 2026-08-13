* Common Subexpression Elimination and optimize alloc timing.
   Eliminate duplicate calculations and optimize the area .
   Makes the allocation logic independent of merge and cancel.

* Delete isKeyword stuff. May lead to a decrease in performance.

* Allow up to 4 miss_req at the same cycle.
   Improve performance when dcache misses occur.
