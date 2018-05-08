package dahu.planning.pddl.ast

import dahu.planning.model.common.{Cst, IntLiteral}
import dahu.planning.pddl.PddlPredef
import fr.uga.pddl4j.parser._
import dahu.utils.errors._

import scala.collection.JavaConverters._

object AssertionOnFunction {
  def unapply(e: Exp): Option[String] = e match {
    case Fluent(name, _)        => Some(name)
    case Eq(Fluent(name, _), _) => Some(name)
    case _                      => None
  }
}

object Fluent {
  def unapply(exp: Exp): Option[(String, List[String])] = {
    if(exp.getConnective == Connective.ATOM || exp.getConnective == Connective.FN_HEAD) {
      exp.getAtom.asScala.toList.map(_.getImage) match {
        case head :: tail => Some((head, tail))
        case _            => None
      }
    } else {
      None
    }
  }
}

object Cst {
  def unapply(e: Exp): Option[Cst] = {
    if(e.getConnective == Connective.NUMBER)
      Some(IntLiteral(PddlPredef.discretize(e.getValue)))
    else
      None
  }
}

object Eq {
  def unapply(e: Exp): Option[(Exp, Exp)] = {
    if(e.getConnective == Connective.FN_ATOM) {
      e.getChildren.asScala.toList match {
        case lhs :: rhs :: Nil => Some((lhs, rhs))
        case _                 => unexpected
      }
    } else {
      None
    }
  }
}

case class Tpe(name: String, parent: Option[String])

object ReadTpe {
  def unapply(e: TypedSymbol): Option[Tpe] =
    if(e.getKind.name() == "TYPE") {
      e.getTypes.asScala.map(_.getImage).toList match {
        case Nil           => Some(Tpe(e.getImage, None))
        case parent :: Nil => Some(Tpe(e.getImage, Some(parent)))
        case _             => None
      }
    } else
      None
}

object And {
  def unapply(e: Exp): Option[List[Exp]] = {
    if(e.getConnective == Connective.AND) {
      Some(e.getChildren.asScala.toList)
    } else {
      None
    }
  }
}

object TypedSymbol {
  def unapply(e: TypedSymbol): Option[(String, String)] = {
    if(e.getKind == Symbol.Kind.VARIABLE || e.getKind == Symbol.Kind.CONSTANT) {
      val name =
        if(e.getImage.startsWith("?"))
          e.getImage.drop(1)
        else
          e.getImage
      e.getTypes.asScala.toList match {
        case tpe :: Nil => Some((name, tpe.getImage))
        case _          => None
      }
    } else {
      None
    }
  }
}
