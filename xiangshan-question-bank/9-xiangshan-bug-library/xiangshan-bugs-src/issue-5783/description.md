if unaligned head was waked up by ls_hint/d_channel but unaligned tail still cache miss, the load need to replay. The `handledByMSHR` need to use unaligned head `handledByMSHR` to prevent stuck.
