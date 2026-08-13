* For #3003, there is a situation where the frontend raise an exception to backend and backend raise interrupt.

* The priority of the interrupt is higher than the exception, so the interrupt needs to be processed first.

* Backend will use 0 to write xtval and use the invalid target in the register to write xepc.

* This patch fixes this bug.
