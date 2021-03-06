package dahu

import algebra.Order

import scala.reflect.ClassTag

package object maps {

  private[maps] type sp = scala.specialized
  private[maps] type ClassTag[A] = scala.reflect.ClassTag[A]

  /** Alias to useful instances. */
  private val intClassTag: ClassTag[Int] = implicitly[ClassTag[Int]]
  private val intAlgebra: Order[Int] = spire.implicits.IntAlgebra

  trait IntSubset { self: Int =>
  }
  type SubInt = Int with IntSubset
  type SInt[T] = SubInt with T

  /** Simply wraps an existing class to make sure we do not mix classes with subint. */
  trait Wrapped[T] { self: SubInt =>
  }
  type SubSubInt[X <: SubInt, AdditionalTag] = X with Wrapped[AdditionalTag]

  implicit def classTagIS[T <: SubInt]: ClassTag[T] = tagged(intClassTag)
  implicit def orderingIS[T <: SubInt]: Ordering[T] = tagged(implicitly[Ordering[Int]])
  implicit def orderIS[T <: SubInt]: Order[T] = tagged(intAlgebra)

  def untagged[T <: SubInt, F[_]](v: F[T]): F[Int] = v.asInstanceOf[F[Int]]
  private def tagged[T <: SubInt, F[_]](v: F[Int]): F[T] = v.asInstanceOf[F[T]]

  class Counter {
    type ID = Int with Wrapped[this.type]
    private var nextValue: Int = 0
    def next(): ID = { nextValue += 1; nextValue.asInstanceOf[ID] }
  }
  object Counter {
    type Aux[ID0] = Counter { type ID = ID0 }
  }
}
