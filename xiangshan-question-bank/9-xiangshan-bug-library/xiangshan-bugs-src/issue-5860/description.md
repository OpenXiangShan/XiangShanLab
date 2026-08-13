1. When modifying `Satp`, save the `old mode` of `satp/vsatp` and the `old privilege mode`.
2. Use the `satpFlush` signal to notify the `frontend` that the redirection is caused by modifying `satp`.
3. Upon receiving the flag signal, if an exception occurs in the execution of the `first` instruction, the `frontend` will send a `satpFlushFirstFetchFault` signal to the backend.
4. Upon receiving this `satpFlushFirstFetchFault` signal, the backend updates `epc/tval/tval2` using the previously stored `old satp/vsatp mode` and `old privilege mode`.
