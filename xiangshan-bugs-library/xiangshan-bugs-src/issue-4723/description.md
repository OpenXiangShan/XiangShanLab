The final result of both approaches is read-only 0, but in the former case, ssstateen[1|2|3] will be optimized away during Verilog generation, while in the latter case, it is preserved in the form shown in the diagram below. This has no impact on the actual circuit.

![image](https://github.com/user-attachments/assets/49c07c60-536b-4f70-a036-818b28f8d93b)
