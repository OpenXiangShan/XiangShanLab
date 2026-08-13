`q0_canSent` only means an entry is selected, not actually sent.
When `mem_acquire` does not fire, that entry is still not `inflight` in the next cycle, but a new request may merge and be marked waitSame.

This breaks old/new classification in forwarding: two entries that should match `isFwdOld` and `isFwdNew` separately can both be treated as `isFwdNew`, so forwarding loses ordering and may return wrong data.

Use real send handshake semantics to gate state/merge behavior, so unsent entries are not treated as sent and forwarding order remains correct.
