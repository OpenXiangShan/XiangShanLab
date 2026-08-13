When OnlyS2 gpaddr (64 bits) should be same to vaddr (50 bits), no need to do any extend (to 56 bits) or truncate (to 48 bits).

This commit re-fixes the bug that #4913 attempted to fix.
