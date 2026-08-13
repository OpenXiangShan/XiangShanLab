This PR is a performance fix patch for PR #5762.

The purpose of #5762:
The purpose of #5762 is to fix the performance counters `mis_pred` and `total_flush`.
The performance counter `mis_pred` only counts when the `Branch` and `Jump` instructions experience `misPred`, removing the impact of `CSR`.
The performance counter `total_flush` only counts the oldest redirect.

The impact caused:
When modifying the `redirect valid` signal written back by the functional unit, the `redirect Valid` of `Branch` and `Jump` instructions were generated and placed inside the functional unit. However, #5762 missed the `Jump` instruction, causing each `Jump` instruction except `Auipc` to redirect and causing `IPC` to crash.

How to fix it:
The current PR has fixed the correct logic for the `Jump` instruction 'redirectValid'. For the `Jump` instruction, only when `misPred` or `backendFault` occurs will a redirect be initiated.

`IPC` returns to normal values.
