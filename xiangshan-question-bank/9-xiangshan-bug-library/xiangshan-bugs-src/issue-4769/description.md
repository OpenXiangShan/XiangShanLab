When leaveHitMux is enabled, pmp will take a beat for the vast majority of io.req.bits. 
However, io.req.bits.cmd didn't take a beat.

Previously, a pipeline would only generate one cmd request, so even if the beat count was not aligned, no bugs would occur.
However, currently, loadunit issues both write and read requests, which causes permission checks to fail.
