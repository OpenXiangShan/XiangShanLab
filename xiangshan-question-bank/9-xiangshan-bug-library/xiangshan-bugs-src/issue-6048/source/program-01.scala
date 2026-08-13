  private val debug_s1UseUbtb      = s1_taken && !useAbtb
  private val debug_s1UseUbtbUtage = s1_taken && !useAbtb
  private val debug_s1UseAbtb      = s1_taken && useAbtb && !s1_utageHitMask.reduce(_ || _)
  private val debug_s1UseAbtbUtage = s1_taken && useAbtb && s1_utageHitMask.reduce(_ || _)
