* For `mtopi` / `stopi` :
> AIA Spec:

> If all bytes of the supervisor-level iprio array are read-only zeros, a simplified implementation of field `IPRIO` is allowed in which its value is always `1` whenever `stopi` is not zero.

* We are configurable and `do not need` to simplify the implementation.

* For `vstopi`: 
> AIA Spec:

> Ties in nominal priority are broken as usual by the default priority order from `Table 8`, unless `hvictl` fields `VTI = 1` and `IID ≠ 9` (last item in the candidate list above), in which case default priority order is determined solely by `hvictl.DPR`.

> If bit `IPRIOM` (IPRIO Mode) of `hvictl` is zero, `IPRIO` in `vstopi` is 1; else, if the priority number for the highest-priority candidate is within the range `1` to `255`, `IPRIO` is that value; else, `IPRIO` is set to either `0` or `255` in the manner documented for `stopi` in `Section 5.4.2`.
