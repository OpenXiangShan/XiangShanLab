Before this, we assumed that all possible exceptions during CSR read and write operations should be handled according to their priority.

Therefore, we ensured that all illegal instruction exceptions take precedence over virtual instruction exceptions.

However, with the implementation of certain extensions like Smcsrind and Smstateen, we encounter scenarios where virtual instruction exceptions must take precedence over illegal instruction exceptions triggered.

For instance, when mstateen0.csrind is set to 1 and hstateen0.csrind is 0, a virtual instruction exception should be raised if VS mode attempts to access sireg. However, if the vsiselect value is reserved in this situation, an illegal instruction exception will be raised instead. If these checks are treated as being at the same priority level, an illegal instruction exception would ultimately be raised.

In reality, a virtual instruction exception should take precedence because when the extension is disabled, we should not even evaluate the value of vsiselect.

Therefore, we divided the sources of exceptions caused by CSR access into several categories: M-level, S-level, privilege level, virtualization level, and indirect access level.

Among them, M-level and S-level will only raise illegal instruction exceptions, the privilege level will raise both illegal instruction and virtual instruction exceptions, the virtualization level will raise virtual instruction exceptions, and indirect access will raise both illegal instruction and virtual instruction exceptions. Therefore, we handle the exceptions from the previous levels in the same way, and only check for exceptions caused by indirect access after ensuring that no exceptions were raised earlier.
