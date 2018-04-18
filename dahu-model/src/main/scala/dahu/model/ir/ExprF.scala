package dahu.model.ir

import cats.Functor
import cats.kernel.Hash
import dahu.model.functions.Fun
import dahu.model.input.Ident
import dahu.model.types.{ProductTag, Tag, Type, Value}

import scala.collection.mutable.ArraySeq
import scala.language.implicitConversions
import scala.runtime.ScalaRunTime

sealed trait ExprF[@specialized(Int) F] {
  def typ: Type

  override final val hashCode: Int = ExprF.hash(this)
}

sealed trait TotalOrOptionalF[@specialized(Int) F] { self: ExprF[F] =>
  def typ: Type
}
sealed trait TotalOrPartialF[@specialized(Int) F] { self: ExprF[F] =>
  def typ: Type
}
object TotalOrOptionalF {
  implicit val functor: Functor[TotalOrOptionalF] = new Functor[TotalOrOptionalF] {
    override def map[A, B](fa: TotalOrOptionalF[A])(f: A => B): TotalOrOptionalF[B] = fa match {
      case fa: Total[A] => Total.functor.map(fa)(f)
      case OptionalF(value, present, typ) =>
        OptionalF(f(value), f(present), typ)
    }
  }
}

object ExprF {
  implicit val functor: Functor[ExprF] = new Functor[ExprF] {
    override def map[A, B](fa: ExprF[A])(f: A => B): ExprF[B] = fa match {
      case fa: Total[A] => Total.functor.map(fa)(f)
      case Partial(value, condition, typ) =>
        Partial(f(value), f(condition), typ)
      case OptionalF(value, present, typ) =>
        OptionalF(f(value), f(present), typ)
      case PresentF(v) => PresentF(f(v))
      case ValidF(v)   => ValidF(f(v))
    }
  }

  def hash[A](exprF: ExprF[A]): Int = exprF match {
    case x: ComputationF[A] => ScalaRunTime._hashCode(x)
    case x: InputF[A]       => ScalaRunTime._hashCode(x)
    case x: CstF[A]         => ScalaRunTime._hashCode(x)
    case x: Partial[A]      => ScalaRunTime._hashCode(x)
    case x: OptionalF[A]    => ScalaRunTime._hashCode(x)
    case x: PresentF[A]     => ScalaRunTime._hashCode(x)
    case x: ValidF[A]       => ScalaRunTime._hashCode(x)
    case x: ITEF[A]         => ScalaRunTime._hashCode(x)
    case x: ProductF[A]     => ScalaRunTime._hashCode(x)
  }
}

/** Pure expressions that always yield value if they are fed with pure expressions.
  *
  * A Fix[Pure] can always be evaluated to its value.
  * */
sealed trait Total[@specialized(Int) F]
    extends ExprF[F]
    with TotalOrOptionalF[F]
    with TotalOrPartialF[F]
object Total {
  implicit val functor: Functor[Total] = new Functor[Total] {
    override def map[@specialized(Int) A, @specialized(Int) B](fa: Total[A])(f: A => B): Total[B] =
      fa match {
        case x @ InputF(_, _)                 => x
        case x @ CstF(_, _)                   => x
        case ComputationF(fun, args, typ)     => ComputationF(fun, args.map(f), typ)
        case ProductF(members, typ)           => ProductF(members.map(f), typ)
        case ITEF(cond, onTrue, onFalse, typ) => ITEF(f(cond), f(onTrue), f(onFalse), typ)
      }
  }
}

/** An (unset) input to the problem.
  * Essentially a decision variable in CSP jargon. */
case class InputF[@specialized(Int) F](id: Ident, typ: Type) extends Total[F] {
  override def toString: String = s"$id"
}
object InputF {

  /** The type parameter of InputF is does not play any role beside allowing recursion scheme.
    * This implicit conversion, allows usin it interchangeably without creating new objects. or casting manually*/
  implicit def typeParamConversion[F, G](fa: InputF[F]): InputF[G] = fa.asInstanceOf[InputF[G]]
}

case class CstF[@specialized(Int) F](value: Value, typ: Type) extends Total[F] {
  override def toString: String = value.toString
}
object CstF {

  /** Leaf node, with  artificial type parameters, allow implicit conversion as for InputF. */
  implicit def typeParamConversion[F, G](fa: CstF[F]): CstF[G] = fa.asInstanceOf[CstF[G]]
}

final case class ComputationF[@specialized(Int) F](fun: Fun[_], args: ArraySeq[F], typ: Type)
    extends Total[F] {
  override def toString: String = s"$fun(${args.mkString(", ")})"
}
object ComputationF {
  def apply[F](fun: Fun[_], args: Seq[F], tpe: Type): ComputationF[F] =
    new ComputationF(fun, ArraySeq(args: _*), tpe)
}

final case class ProductF[@specialized(Int) F](members: ArraySeq[F], typ: ProductTag[Any])
    extends Total[F] {
  override def toString: String = members.mkString("(", ", ", ")")
}
object ProductF {
  def apply[F](args: Seq[F], tpe: ProductTag[Any]): ProductF[F] =
    new ProductF[F](ArraySeq(args: _*), tpe)
}

final case class ITEF[@specialized(Int) F](cond: F, onTrue: F, onFalse: F, typ: Type)
    extends Total[F] {
  override def toString: String = s"ite($cond, $onTrue, $onFalse)"
}

final case class PresentF[F](optional: F) extends ExprF[F] {
  override def typ: Type = Tag.ofBoolean

  override def toString: String = s"present($optional)"
}

final case class ValidF[F](partial: F) extends ExprF[F] {
  override def typ: Type = Tag.ofBoolean

  override def toString: String = s"valid($partial)"
}

/** An Optional expression, that evaluates to Some(value) if present == true and to None otherwise. */
final case class OptionalF[F](value: F, present: F, typ: Type)
    extends ExprF[F]
    with TotalOrOptionalF[F] {
  override def toString: String = s"$value? (presence: $present)"
}

/** A partial expression that only produces a value if its condition evaluates to True. */
final case class Partial[F](value: F, condition: F, typ: Type)
    extends ExprF[F]
    with TotalOrPartialF[F] {
  override def toString: String = s"$value? (constraint: $condition)"
}
