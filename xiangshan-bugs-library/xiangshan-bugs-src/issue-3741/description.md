[6e4a3d5](https://github.com/OpenXiangShan/XiangShan/pull/3741/commits/6e4a3d51e29a114d2d2697cd44efd733252740fc) Now we use the more fine-grained canAccept with ready feedback.
Separating the vector load from the vector store to determine the canAccept reduces the number of cases that can't be queued, and this also fixes the problem of deadlocks caused by a full RAWQueue.

* Ignore [fix(VMergeBuffer): vl of fof only allows setting smaller values](https://github.com/OpenXiangShan/XiangShan/pull/3741/commits/ e7e1bcabb679b8b77fdcc52bcdc1b2447a40c0f9). This commit is already in the master cbbad3d9821348664612590be62c941195db2035.

* Waiting for regression testing before merging.
