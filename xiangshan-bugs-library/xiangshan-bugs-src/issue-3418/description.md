In this PR, the main goal is to fix the bug encountered during ROB commit. However, resolving this issue requires information about `iretire` and `ilastsize`, which need be collected by the trace. Therefore, I have also included the trace interface in this PR.

The specific changes are as follows:
 * When rob commit, update the ftqIdx and ftqOffset to correctly notify the frontend which instructions have been committed.
 * In each robentry, the ftqIdx and ftqOffset belong to the first instruction that was compressed, that is Necessary when exceptions happen.
 * Add trace Interface in hart.
 * Add trace parameter in parameter.scala.
 * Collect trace infomation in backend pipeline.
