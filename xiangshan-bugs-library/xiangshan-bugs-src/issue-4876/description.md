example： 
rdataPtr(0) is last element of vector store, rdataPtr(0) need to occupy two write port, which lead to vecExceptionFlag not cancel. (Because of `firstSplit`)
