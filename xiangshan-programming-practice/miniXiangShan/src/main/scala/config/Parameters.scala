package minixiangshan.config

import chisel3._
import chisel3.util._

// 1. 自定义简单的Field类
class Field[T](val default: T)

// 2. 自定义简单的Parameters类
class Parameters(val settings: Map[Field[_], Any]) {
  def apply[T](key: Field[T]): T = 
    settings.getOrElse(key, key.default).asInstanceOf[T]
  
  def getOrElse[T](key: Field[T], default: T): T = 
    settings.get(key).map(_.asInstanceOf[T]).getOrElse(default)
}
