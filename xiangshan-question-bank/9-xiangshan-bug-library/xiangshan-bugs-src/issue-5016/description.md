`backendException` is a Reg and `backendRedirect` is a RegNext, we have:
1. cycle 0: `backendRedirect.valid && (backendRedirect.bits.backendIPF || backendRedirect.bits.backendIPF || backendRedirect.bits.backendIPF)`
2. cycle 1: `backendException.hasException`, but `!backendRedirect.valid`

So `backendExceptionPtr` is not written unless we have a old backendException
