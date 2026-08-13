The preivous GenExceptionVa function makes some mistakes in selecting the exception vaddr when exception. The check for S-stage, VS-stage and G-stage are mixed together, which causes the one-hot signal to be wrong.

This patch rewrites the relative logic and fixes the bug.
