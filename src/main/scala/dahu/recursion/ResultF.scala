package dahu.recursion

import dahu.expr.Fun
import matryoshka.instances.fixedpoint.Cofree

import scala.reflect.runtime.universe.{typeOf, Type, TypeTag}

object TypeAlias {
  type TT  = Type
  type Top = Any
}

import TypeAlias._

sealed abstract class ResultF[F] {
  def typ: TT
}

case class InputF[F](name: String, typ: TT)                           extends ResultF[F]
case class CstF[F](value: Top, typ: TT)                               extends ResultF[F]
final case class ComputationF[F](fun: Fun[_], args: List[F], typ: TT) extends ResultF[F]

object Test {

  import matryoshka._
  import matryoshka.data._

}
