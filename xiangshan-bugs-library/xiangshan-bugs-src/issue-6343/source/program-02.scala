val canTriggerCacheError =
  dcache_resp_tl_error.asUInt.orR && io.csrCtrl.cache_error_enable

exceptionVec(hardwareError) :=
  canTriggerCacheError &&
  dcache_resp_tl_error.tl_corrupt &&
  !dcache_resp_tl_error.tl_denied
