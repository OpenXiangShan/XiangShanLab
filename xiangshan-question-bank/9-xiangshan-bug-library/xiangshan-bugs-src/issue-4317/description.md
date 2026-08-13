1. Predictor pipeline stalls exhibit poor fault tolerance.
2. Speculative queue overflow (requiring 32 uncommitted call/return instructions) is an extreme scenario where disabling return stack prediction incurs negligible performance impact.
3. Queue overflow often indicates recursion. In such cases, using top-of-stack data (static return addresses) may outperform IT-TAGE predictions despite disabled return stack.
