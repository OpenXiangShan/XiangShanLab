val touchWays = Seq(writeTouchVec(nRows)) ++ hitTouchVec(nRows).filter(_.valid == true.B).take(numPorts)
replacerWay(nRows) := replacer.way
replacer.access(touchWays)
