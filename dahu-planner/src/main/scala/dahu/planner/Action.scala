package dahu.planner

import copla.lang.model.core._
import dahu.model.input.{Cst, Ident, Input, Tentative}
import dahu.utils.errors._

case class Action[F[_]](name: String,
                        start: F[Int],
                        end: F[Int],
                        args: List[F[Literal]],
                        chronicle: Chronicle) {}

object Action {
  var counter = 0

//  case class Context(pb: ProblemContext, trans: Id => Id, params: Array[(Arg, Instance)])
//
//  def updateScope(base: Scope, addLevel: String)(scope: Scope): Scope = {
//    scope match {
//      case `base` => base + addLevel
//      case RootScope => RootScope
//      case InnerScope(s, name) => updateScope(base, addLevel)(s) + name
//    }
//  }
//  def updateId(base: Scope, addLevel: String)(id: Id): Id = id match {
//    case Id(s, name) => Id(updateScope(base, addLevel)(s), name)
//  }
//
//  def encode(v: Var)(implicit ctx: Context): Tentative[Instance] =
//    v match {
//      case LocalVar(id, tpe) => Input(Ident(LocalVar(ctx.trans(id), tpe)))(ctx.pb.specializedTags(tpe))
//      case i @ Instance(id, tpe)  => Cst(i)(ctx.pb.specializedTags(tpe))
//      case x: Arg => ctx.params.find(_._1 == x) match {
//        case Some((_, instance)) => Cst(instance)(ctx.pb.specializedTags(instance.typ))
//        case None => unexpected
//      }
//    }
//  def encode(tp: TPRef)(implicit ctx: Context): Tentative[Int] = tp match {
//    case TPRef(TPId(id), delay) => ctx.pb.encode(TPRef(TPId(ctx.trans(id)), delay))
//  }
//
//  def eqv(v1: Var, v2: Var)(implicit ctx: Context): Tentative[Boolean] = ctx.pb.eqv(encode(v1), encode(v2))
//  def neq(v1: Var, v2: Var)(implicit ctx: Context): Tentative[Boolean] = ctx.pb.neq(encode(v1), encode(v2))
//
//  def extend(chronicle: Chronicle, statement: Statement)(implicit ctx: Context): Chronicle = statement match {
//    case StaticEqualAssertion(lhs, rhs) =>
//      chronicle.copy(constraints = eqv(lhs, rhs) :: chronicle.constraints)
//    case StaticDifferentAssertion(lhs, rhs) =>
//      chronicle.copy(constraints = neq(lhs, rhs) :: chronicle.constraints)
//  }

  def primitive(template: ActionTemplate, ctx: ProblemContext)(
      args: Array[Literal]): Action[Tentative] = {
    def lit2Cst(lit: Literal): Cst[Literal] = lit match {
      case x @ ObjLit(instance) => x.asConstant(ctx.specializedTags(instance.typ))
      case x: IntLit            => x.asConstant(ctx.intTag)
    }

    counter += 1
    val act = template.instance(s"${template.name}_$counter")
    assert(act.args.length == args.length)
    val argsRewrite: Arg => Tentative[Literal] = x => {
      val id = act.args.indexOf(x)
      lit2Cst(args(id))
    }

    val chronicle = act.content.foldLeft(Chronicle.empty(ctx)) {
      case (c, s) => c.extended(s)(argsRewrite)
    }

    Action(act.template.name,
           ctx.encode(act.start),
           ctx.encode(act.end),
           args.toList.map(lit2Cst),
           chronicle)
  }

  def instance(template: ActionTemplate, ctx: ProblemContext): Action[Tentative] = {
    counter += 1
    val act = template.instance(s"${template.name}_$counter")
    val argsRewrite: Arg => Tentative[Literal] = {
      case a @ Arg(_, tpe) => Input(Ident(a))(ctx.specializedTags(tpe))
    }

    val chronicle = act.content.foldLeft(Chronicle.empty(ctx)) {
      case (c, s) => c.extended(s)(argsRewrite)
    }

    Action(act.template.name,
           ctx.encode(act.start),
           ctx.encode(act.end),
           act.args.toList.map(argsRewrite),
           chronicle)
  }
}