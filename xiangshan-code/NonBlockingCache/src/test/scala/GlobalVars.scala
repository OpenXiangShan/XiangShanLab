
object GlobalVars {

  val TEST_NUM = 50 /*修改此处需要测试的样例数量*/ //全部测试结束后自动停止
  var randomSeed: Long = 6688L  /*修改此处的随机种子以生成 不同测试测样 及 不同随机延迟*/


  val TEST_CYCEL = 10000 //最多测试的周期数
  var tested_num: Int = 0 //记录已测试的数量
}

