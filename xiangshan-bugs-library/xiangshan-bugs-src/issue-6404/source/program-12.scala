// src/main/scala/xiangshan/frontend/icache/Parameters.scala:68-70
  // this is used to prevent magic number in the code, DO NOT CHANGE, it won't work other than 2
  // explanation: we allow concurrent access to consecutive cachelines
  def PortNumber: Int = 2
