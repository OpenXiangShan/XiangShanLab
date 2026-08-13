In the previous exception handling in LLPTW, both isAf and isGpf were checked for all cases, including allStage, onlyStage1, and noS2xlate.

In fact, for allStage, only isPf & isGpf needs to be checked, while for onlyStage1 and noS2xlate, only isPf & isAf should be checked.

This commit fixes this issue.
