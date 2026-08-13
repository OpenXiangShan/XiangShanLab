When an exception is thrown by vector store:

    * If not the last flow triggers an exception, then pull up the vecExceptionFlag and do not allow subsequent flows to actually 
    * write to the sbuffer, but can exit the queue from the sq normally.

    * If it is the last flow that triggered the exception, then there is no need to pull up vecExceptionFlag.

The vecExceptionFlag affects the vecvalid signal passed into the sbuffer, and only when vecvalid is high can data actually be written to the sbuffer.

Based on the current ports of the sbuffer, we list the cases as shown in the implementation:

    * When only the first port is valid, we only need to see if the first port is lastflow.

    * When both ports are valid, we need to judge based on whether robidx is equal or not:
           * When equal, the first port is definitely not lastflow, so we only need to judge whether the second port is lastflow.

           * When unequal, the first port is definitely lastflow, so we need to pull up vecCommitLastFlow when the second port 
           * doesn't trigger an exception, and we need to judge whether the second port is lastflow when the second port triggers 
           * an exception.
