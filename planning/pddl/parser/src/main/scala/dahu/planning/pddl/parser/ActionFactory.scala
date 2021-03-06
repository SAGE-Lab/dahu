package dahu.planning.pddl.parser

import dahu.planning.model.common._
import dahu.planning.model.full._
import Utils._
import dahu.planning.pddl.parser.TQual.Start
import dahu.utils.errors._
import fr.uga.pddl4j.parser.{Exp, Op}

import scala.collection.JavaConverters._
import scala.collection.mutable

class ActionFactory(actionName: String, parent: Resolver, model: Model) extends Factory {
  implicit def predef = parent.predef
  private var template = ActionTemplate(model.scope / actionName, model)

  private val start = LocalVar(resolver.id(predef.StartSym), resolver.predef.Time)
  private val end = LocalVar(resolver.id(predef.EndSym), resolver.predef.Time)

  rec(LocalVarDeclaration(start))
  rec(LocalVarDeclaration(end))

  override def context: ActionTemplate = template

  override def getTranslator(name: String): FunctionCompat = parent.getTranslator(name)

  private def duration: StaticExpr = BinaryExprTree(operators.Sub, end, start)

  def rec(block: InActionBlock): Unit = {
    template = template + block
  }

  def preprocess(op: Op): IntermediateAction = {
    require(op.getName.getImage == actionName)

    op.getParameters.asScala.foreach {
      case ast.TypedSymbol(name, tpe) =>
        rec(ArgDeclaration(Arg(resolver.id(name), resolver.typeOf(tpe))))
    }

    Option(op.getDuration) match {
      case Some(ast.Eq(ast.Duration(_), ast.Cst(cst))) =>
        rec(
          BooleanAssertion(BinaryExprTree(operators.Eq, duration, cst))
        )
      case Some(ast.Eq(ast.Duration(_), e @ ast.Fluent(f, args))) =>
        rec(
          TemporallyQualifiedAssertion(
            Equals(ClosedInterval(start, start)),
            TimedEqualAssertion(resolver.getTranslator(f).fluent(f, args, resolver),
                                duration,
                                Some(context),
                                model.scope.makeNewId())
          )
        )
      case x =>
        unexpected(x.toString)
    }
    val ass = assertions(op.getPreconditions, op.getEffects)
    IntermediateAction(context, start, end, ass)
  }

  def asEffectAss(e: Exp): TimedAssignmentAssertion = e match {
    case ast.AssertionOnFunction(funcName) =>
      resolver.getTranslator(funcName).effect(e, resolver)
  }
  def asCondAss(e: Exp): TimedEqualAssertion = e match {
    case ast.AssertionOnFunction(funcName) =>
      resolver.getTranslator(funcName).condition(e, resolver)
  }
  def assertions(conds: Exp, effs: Exp): Seq[Ass] = {
    def getPre(pre: Exp): Seq[Ass] = pre match {
      case ast.And(subs)  => subs.flatMap(getPre)
      case ast.AtStart(e) => Ass(TQual.Start, asCondAss(e)) :: Nil
      case ast.AtEnd(e)   => Ass(TQual.End, asCondAss(e)) :: Nil
      case ast.OverAll(e) => Ass(TQual.All, asCondAss(e)) :: Nil
    }
    def getEff(pre: Exp): Seq[Ass] = pre match {
      case ast.And(subs)  => subs.flatMap(getEff)
      case ast.AtStart(e) => Ass(TQual.Start, asEffectAss(e)) :: Nil
      case ast.AtEnd(e)   => Ass(TQual.End, asEffectAss(e)) :: Nil
      case ast.OverAll(e) => Ass(TQual.All, asEffectAss(e)) :: Nil
    }
    getPre(conds) ++ getEff(effs)
  }
}

object ActionFactory {

  def build(op: Op, resolver: Resolver, model: Model): ActionTemplate = {
    implicit val predef: PddlPredef = resolver.predef
    val pre = preProcess(op, resolver, model)
    postProcess(pre)
  }

  def preProcess(op: Op, resolver: Resolver, model: Model): IntermediateAction = {
    val fact = new ActionFactory(op.getName.getImage, resolver, model)
    fact.preprocess(op)
  }

  def postProcess(act: IntermediateAction)(implicit predef: PddlPredef): ActionTemplate = {
    var mod = act.base
    val start = act.start
    val end = act.end
    def add(t: TemporalQualifier, e: TimedAssertion): Unit = {
      mod = mod + TemporallyQualifiedAssertion(t, e)
    }

    import TQual._
    for(ass <- act.assertions) ass match {
      case Ass(Start, e: TimedEqualAssertion) =>
        add(Equals(ClosedInterval(start, start)), e)
      case Ass(End, e: TimedEqualAssertion) =>
        add(Equals(ClosedInterval(end, end)), e)
      case Ass(All, e: TimedEqualAssertion) =>
        add(Equals(ClosedInterval(start, end)), e)
      case Ass(Start, e: TimedAssignmentAssertion) =>
        add(Equals(LeftOpenInterval(start, BinaryExprTree(operators.Add, start, predef.Epsilon))),
            e)
      case Ass(End, e: TimedAssignmentAssertion) =>
        add(Equals(LeftOpenInterval(end, BinaryExprTree(operators.Add, end, predef.Epsilon))), e)
      case _ => unexpected
    }
    mod
  }

  type PASS = Seq[Ass] => Seq[Ass]
  case class Val(t: TQual, v: StaticExpr)
  case class Reqs(fluent: TimedExpr, cond: Seq[Val], effs: Seq[Val]) {
    override def toString: String = s"$fluent\n  ${cond.mkString("  ")}\n  ${effs.mkString("  ")}"
  }

}

sealed trait TQual
object TQual {
  case object Start extends TQual
  case object End extends TQual
  case object All extends TQual
}

case class Ass(qual: TQual, ass: TimedAssertion)

case class IntermediateAction(base: ActionTemplate,
                              start: LocalVar,
                              end: LocalVar,
                              assertions: Seq[Ass])
