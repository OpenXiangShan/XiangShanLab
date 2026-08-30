According PMM spec, PMM is not used for instruction fetch, including ifetch, hlvx and MXR. Previous implementation of PMM didn't handle MXR at all.

This patch introduces MXR into PMM handling procedure. Note:
* MXR only works when translation is enabled, i.e. effective mode is less than M. So MXR should be considered after M mode is identified.
* There is 2 MXR: MXR (mstatus/sstatus.MXR) and vMXR (vsstatus.MXR). When V=1, for VS stage, both of then are effective.
* The modification is inspired by spike.
