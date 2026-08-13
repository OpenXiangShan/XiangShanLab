For "unit-stride access with element granularity misaligned and emul<0", it could be the case that: 
has only once valid elements, but splits into two flows(misaligned), which would result in the `elemidx` being the same, making it impossible for the exception handling logic in the `mergebuffer` to recognise the correct order.

Instead of adding a new variable, we have chosen to reuse `elemidx` as a marker.
But this does pollute the original semantics of `elemidx`.
