package dahu.planning.anml.parser

import java.io.{File, IOException}

import dahu.planning.model._
import ParserApi.baseApi._
import ParserApi.baseApi.Parsed.Success
import ParserApi.whiteApi._
import ParserApi.extendedApi._
import fastparse.core.Parsed.Failure
import dahu.planning.model.common._
import dahu.planning.model.common.operators.{
  Associativity,
  BinOperatorGroup,
  BinaryOperator,
  OperatorGroup,
  UnaryOperator,
  UniOperatorGroup
}
import dahu.planning.model.full._

import scala.annotation.tailrec
import scala.util.Try

abstract class AnmlParser(val initialContext: Ctx)(implicit predef: Predef) {

  /** Denotes the current context of this AnmlParser.
    * It is used by many suparsers to find the variable/fluent/type associated to an identifier.
    * Over the course of Parsing, the current context is likely to change (i.e. `ctx` will point to
    * a new context since [[Ctx]] is immutable. */
  protected var ctx: Ctx = initialContext
  protected def updateContext(newContext: Ctx) {
    ctx = newContext
  }

  val word: Parser[String] = {
    import fastparse.all._ // override sequence composition to ignore white spaces
    (CharIn(('a' to 'z') ++ ('A' to 'Z') ++ "_") ~
      CharsWhileIn(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "_", min = 0)).!.opaque("word")
  }
  val int: Parser[Int] = CharsWhileIn('0' to '9').!.map(_.toInt).opaque("int")

  val typeKW: Parser[Unit] = word.filter(_ == "type").silent.opaque("type")
  val withKW: Parser[Unit] = word.filter(_ == "with").silent.opaque("with")
  val instanceKW: Parser[Unit] = word.filter(_ == "instance").silent.opaque("instance")
  val fluentKW: Parser[Unit] = word.filter(_ == "fluent").silent.opaque("fluent")
  val constantKW: Parser[Unit] = word.filter(_ == "constant").silent.opaque("constant")
  val timepointKW: Parser[Unit] = word.filter(_ == "timepoint").silent.opaque("instance")
  val actionKW: Parser[Unit] = word.filter(_ == "action").silent.opaque("action")
  val durationKW: Parser[Unit] = word.filter(_ == "duration").silent.opaque("duration")
  val containsKW: Parser[Unit] = word.filter(_ == "contains").silent.opaque("contains")
  val keywords: Set[String] =
    Set("type", "instance", "action", "duration", "fluent", "variable", "predicate", "timepoint")
  val nonIdent: Set[String] = keywords

  val simpleIdent: Parser[String] =
    word.opaque("ident").namedFilter(!nonIdent.contains(_), "not-reserved")
  val ident: Parser[String] = simpleIdent.rep(min = 1, sep = ".").!.opaque("possibly-nested-ident")
  val typeName: Parser[String] = word.filter(!keywords.contains(_)).opaque("type-name")
  val variableName: Parser[String] = ident.opaque("variable-name")

  val freeIdent: Parser[String] =
    simpleIdent
      .namedFilter(id =>
                     ctx.findDeclaration(id) match {
                       case Some(_) => false
                       case None    => true
                   },
                   "unused")

  val declaredType: Parser[Type] =
    typeName.optGet(ctx.findType(_), "declared")

  val timepointDeclaration: Parser[LocalVarDeclaration] =
    timepointKW ~/
      freeIdent.map(name => LocalVarDeclaration(Timepoint(ctx.id(name)))) ~
      ";"

  protected val definedTP: Parser[LocalVar] =
    ident.optGet(ctx.findTimepoint(_), "declared-timepoint")

//  val timepoint: Parser[TPRef] = {
//    (int ~ "+").flatMap(d => timepoint.map(tp => tp + d)) |
//      (definedTP.map(TPRef(_)) ~ (("+" | "-").! ~ int).?).map {
//        case (tp, Some(("+", delay))) => tp + delay
//        case (tp, Some(("-", delay))) => tp - delay
//        case (tp, None)               => tp
//        case _                        => sys.error("Buggy parser implementation")
//      } |
//      int.flatMap(
//        i => // integer as a tp defined relatively to the global start, if one exists
//          ctx.root.findTimepoint("start") match {
//            case Some(st) => PassWith(TPRef(st) + i)
//            case None     => Fail.opaque("fail: no start timepoint in top level scope")
//        })
//  }.opaque("timepoint")

//  lazy val delay: Parser[Delay] =
//    (durationKW ~/ Pass)
//      .flatMap(
//        _ =>
//          ctx
//            .findTimepoint("start")
//            .flatMap(st => ctx.findTimepoint("end").map(ed => (TPRef(st), TPRef(ed)))) match {
//            case Some((st, ed)) => PassWith(Delay(st, ed))
//            case None           => sys.error("No start/end timepoint")
//        })
//      .opaque("duration") |
//      (timepoint ~ "-" ~ definedTP.map(TPRef(_)) ~ (("+" | "-").! ~ intExpr).?).map {
//        case (t1, t2, None)           => t1 - t2
//        case (t1, t2, Some(("+", i))) => (t1 - t2) + i
//        case (t1, t2, Some(("-", i))) => (t1 - t2) - i
//        case _                        => sys.error("Buggy parser implementation")
//      }
//
//  lazy val temporalConstraint: Parser[Seq[TBefore]] = {
//    (timepoint ~ ("<=" | "<" | ">=" | ">" | "==" | ":=" | "=").! ~/ timepoint ~ ";")
//      .map {
//        case (t1, "<", t2)                                     => Seq(t1 < t2)
//        case (t1, "<=", t2)                                    => Seq(t1 <= t2)
//        case (t1, ">", t2)                                     => Seq(t1 > t2)
//        case (t1, ">=", t2)                                    => Seq(t1 >= t2)
//        case (t1, eq, t2) if Set("=", "==", ":=").contains(eq) => t1 === t2
//        case _                                                 => sys.error("Buggy parser implementation")
//      } |
//      (delay ~ ("<=" | "<" | ">=" | ">" | "==" | ":=" | "=").! ~/ intExpr ~ ";")
//        .map {
//          case (d, "<", t)                                     => Seq(d < t)
//          case (d, "<=", t)                                    => Seq(d <= t)
//          case (d, ">", t)                                     => Seq(d > t)
//          case (d, ">=", t)                                    => Seq(d >= t)
//          case (d, eq, t) if Set("=", "==", ":=").contains(eq) => d === t
//          case _                                               => sys.error("Buggy parser implementation")
//        }
//  }

  val variable: Parser[Term] =
    ident.optGet(ctx.findVariable(_), "declared-variable")

  val fluent: Parser[FluentTemplate] =
    ident.optGet(ctx.findFluent(_), "declared-fluent")

  val constantFunc: Parser[ConstantTemplate] =
    ident.optGet(ctx.findConstant(_), "declared-fluent")

  /** Parses a fluent in the object oriented notation.
    * "x.f" where x is a variable of type T and f is a fluent declared in type T or in a supertype of T.
    * Returns the fluent T.f and x which is to be the first argument of T.f */
  val partiallyAppliedFunction: Parser[(FunctionTemplate, Term)] = {

    /** Retrieves a fluent template declared the the given type or one of its super types.*/
    def findInTypeFunction(typ: Type, fluentName: String): Option[FunctionTemplate] =
      ctx
        .findFunction(typ.id.name + "." + fluentName)
        .orElse(typ.parent.flatMap(p => findInTypeFunction(p, fluentName)))

    ident
      .map(str => str.split("\\.").toList)
      // split into first idents (variable) and last (fluent name)
      .map(idents => (idents.dropRight(1), idents.last))
      // only keep if first idents represent a valid variable
      .map(tup => (ctx.findVariable(tup._1.mkString(".")), tup._2))
      .namedFilter(_._1.isDefined, "declared-variable")
      .map(tup => (tup._1.get, tup._2))
      // keep if we can find the fluent in the type of the variable
      .namedFilter({ case (v, fluentName) => findInTypeFunction(v.typ, fluentName).isDefined },
                   s"fluent-available-for-this-variable-type")
      // return the fluent and the variable
      .map { case (v, fluentName) => (findInTypeFunction(v.typ, fluentName).get, v) }
  }

  private[this] def varList(expectedTypes: Seq[Type],
                            sep: String,
                            previous: Seq[StaticExpr] = Seq()): Parser[Seq[StaticExpr]] = {
    if(expectedTypes.isEmpty) {
      PassWith(previous)
    } else {
      staticExpr
        .namedFilter(_.typ.isSubtypeOf(expectedTypes.head), "has-expected-type")
        .flatMap(
          v =>
            if(expectedTypes.tail.isEmpty)
              PassWith(previous :+ v)
            else
              Pass ~ sep ~/ varList(expectedTypes.tail, sep, previous :+ v))
    }
  }

  /** Parses a sequence of args necessarily enclosed in parenthesis if non empty
    * Example of valid inputs "", "()", "(Type1 arg1)", "(Type1 arg1, Type2 arg2)"
    */
  protected val argList: Parser[Seq[(String, Type)]] = {
    val arg: Parser[(String, Type)] =
      (declaredType ~ ident)
        .map { case (typ, argName) => (argName, typ) }

    /** A list of at least one argument formatted as "Type1 arg, Type2 arg2" */
    def distinctArgSeq(sep: String,
                       previous: Seq[(String, Type)] = Seq()): Parser[Seq[(String, Type)]] =
      Pass ~ arg
        .namedFilter(a => !previous.exists(_._1 == a._1), "not-used-in-current-arg-sequence")
        .flatMap(a => (Pass ~ sep ~/ distinctArgSeq(sep, previous :+ a)) | PassWith(previous :+ a))

    ("(" ~/
      ((&(word) ~/ distinctArgSeq(",")) | PassWith(Seq()).opaque("no-args")) ~
      ")") | // parenthesis with and without args
      PassWith(Seq()).opaque("no-args") // no args no, parenthesis
  }

  val timedSymExpr: Parser[TimedExpr] = {
    val partiallyAppliedFluent = partiallyAppliedFunction
      .namedFilter(_._1.isInstanceOf[FluentTemplate], "is-fluent")
      .map(tup => (tup._1.asInstanceOf[FluentTemplate], tup._2))

    (fluent ~/ Pass).flatMap(f =>
      f.params.map(param => param.typ) match {
        case Seq() => (("(" ~/ ")") | Pass) ~ PassWith(new Fluent(f, Seq()))
        case paramTypes =>
          "(" ~/ varList(paramTypes, ",").map(args => new Fluent(f, args)) ~ ")" ~/ Pass
    }) |
      (partiallyAppliedFluent ~/ Pass).flatMap {
        case (f, firstArg) =>
          f.params.map(param => param.typ) match {
            case Seq(singleParam) =>
              (("(" ~/ ")") | Pass) ~ PassWith(new Fluent(f, Seq(CommonTerm(firstArg))))
            case paramTypes =>
              "(" ~/ varList(paramTypes.tail, ",")
                .map(args => new Fluent(f, CommonTerm(firstArg) +: args)) ~ ")" ~/ Pass
          }
      }
  }

  val constantFuncTerm: Parser[Constant] = P {
    val partiallyAppliedConstant = partiallyAppliedFunction
      .namedFilter(_._1.isInstanceOf[ConstantTemplate], "is-constant")
      .map(tup => (tup._1.asInstanceOf[ConstantTemplate], tup._2))

    (constantFunc ~/ Pass).flatMap(f =>
      f.params.map(param => param.typ) match {
        case Seq() => (("(" ~/ ")") | Pass) ~ PassWith(Constant(f, Seq()))
        case paramTypes =>
          "(" ~/ varList(paramTypes, ",").map(args => Constant(f, args)) ~ ")" ~/ Pass
    }) |
      (partiallyAppliedConstant ~/ Pass).flatMap {
        case (f, firstArg) =>
          f.params.map(param => param.typ) match {
            case Seq(singleParam) =>
              (("(" ~/ ")") | Pass) ~ PassWith(Constant(f, Seq(CommonTerm(firstArg))))
            case paramTypes =>
              "(" ~/ varList(paramTypes.tail, ",")
                .map(args => Constant(f, CommonTerm(firstArg) +: args)) ~ ")" ~/ Pass
          }
      }
  }

  val staticTerm: Parser[StaticExpr] = {
    (durationKW ~/ Pass)
      .flatMap(_ =>
        (for {
          st <- ctx.findTimepoint("start")
          ed <- ctx.findTimepoint("end")
        } yield full.BinaryExprTree(operators.Sub, CommonTerm(ed), CommonTerm(st))) match {
          case Some(e) => PassWith(e)
          case None    => sys.error("No start/end timepoint")
      })
      .opaque("duration") |
      int.map(i => CommonTerm(IntLiteral(i))) |
      variable.map(CommonTerm(_)) |
      constantFuncTerm
  }

  val staticExpr: P[StaticExpr] = Tmp.expr

  object Tmp {
    type E = StaticExpr
    type PE = Parser[StaticExpr]
    def term: PE = P(staticTerm ~/ Pass)
    def expr: PE = P(top)
    val bottom: PE = P(("(" ~/ expr ~/ ")") | term)
    val top: PE =
      operators.layeredOps.foldLeft(bottom) {
        case (inner, opGroup) => groupParser(opGroup, inner)
      }

    def binGroupParser(gpe: BinOperatorGroup, inner: PE): PE = {
      // parser for a single operator in the group
      val operator: P[BinaryOperator] =
        StringIn(gpe.ops.map(_.op).toSeq: _*).!.optGet(str => gpe.ops.find(_.op == str))
          .opaque(gpe.ops.map(a => "\"" + a.op + "\"").mkString("(", "|", ")"))
      gpe match {
        case BinOperatorGroup(ops, _, Associativity.Left) =>
          (inner ~/ (operator ~/ inner).rep).optGet({
            case (head, tail) =>
              tail.foldLeft(Option(head)) {
                case (acc, (op, rhs)) => acc.flatMap(x => asBinary(op, x, rhs))
              }
          }, "well-typed")

        case BinOperatorGroup(ops, _, Associativity.Right) =>
          (inner ~/ (operator ~/ inner).rep).optGet(
            {
              case (head, tail) =>
                def makeRightAssociative[A, B](e1: A, nexts: List[(B, A)]): (List[(A, B)], A) =
                  nexts match {
                    case Nil => (Nil, e1)
                    case (b, e2) :: rest =>
                      val (prevs, last) = makeRightAssociative(e2, rest)
                      ((e1, b) :: prevs, last)
                  }
                val (prevs: List[(StaticExpr, BinaryOperator)], last: StaticExpr) =
                  makeRightAssociative(head, tail.toList)
                prevs.foldRight(Option(last)) {
                  case ((lhs, op), rhs) => rhs.flatMap(asBinary(op, lhs, _))
                }
            },
            "well-typed"
          )

        case BinOperatorGroup(ops, _, Associativity.Non) =>
          (inner ~/ (operator ~/ inner).?).optGet({
            case (lhs, None)            => Some(lhs)
            case (lhs, Some((op, rhs))) => asBinary(op, lhs, rhs)
          }, "well-typed")
      }
    }

    def unaryGroupParser(gpe: UniOperatorGroup, inner: PE): PE = {
      val operator: P[UnaryOperator] =
        StringIn(gpe.ops.map(_.op).toSeq: _*).!.optGet(str => gpe.ops.find(_.op == str))
          .opaque(gpe.ops.map(a => "\"" + a.op + "\"").mkString("(", "|", ")"))
      (operator.? ~ inner)
        .optGet({
          case (None, e)     => Some(e)
          case (Some(op), e) => Try(full.UnaryExprTree(op, e)).toOption
        }, "well-typed")
    }

    def groupParser(gpe: OperatorGroup, inner: PE): PE = gpe match {
      case x: BinOperatorGroup => binGroupParser(x, inner)
      case x: UniOperatorGroup => unaryGroupParser(x, inner)
    }
    def asBinary(op: BinaryOperator, lhs: StaticExpr, rhs: StaticExpr): Option[StaticExpr] = {
      op.tpe(lhs.typ, rhs.typ) match {
        case Right(_) => Some(full.BinaryExprTree(op, lhs, rhs))
        case Left(_)  => None
      }
    }
  }

  val expr: Parser[full.Expr] =
    timedSymExpr | staticExpr

  val timepoint: P[StaticExpr] =
    staticExpr.namedFilter(_.typ.isSubtypeOf(Type.Integers), "of-type-integer")

  val interval: Parser[ClosedInterval[StaticExpr]] =
    ("[" ~/
      ((timepoint ~/ ("," ~/ timepoint).?).map {
        case (tp, None)       => (tp, tp) // "[end]" becomes "[end, end]"
        case (tp1, Some(tp2)) => (tp1, tp2)
      } |
        P("all").map(_ => {
          (ctx.findTimepoint("start"), ctx.findTimepoint("end")) match {
            case (Some(st), Some(ed)) => (CommonTerm(st), CommonTerm(ed))
            case _                    => sys.error("Start and/or end timepoints are not defined.")
          }
        })) ~
      "]").map {
      case (tp1, tp2) => ClosedInterval(tp1, tp2)
    }

  val timedAssertion: Parser[TimedAssertion] = {
    // variable that hold the first two parsed token to facilitate type checking logic
    var id: Id = null
    var fluent: TimedExpr = null

    def compatibleTypes(t1: Type, t2: Type): Boolean = t1.isSubtypeOf(t2) || t2.isSubtypeOf(t1)

    /** Reads a static symbolic expressions whose type is compatible with the one of the fluent */
    val rightSideExpr: Parser[StaticExpr] =
      staticExpr.namedFilter(expr => compatibleTypes(fluent.typ, expr.typ), "has-compatible-type")

    /** Reads an identifier or construct a default one otherwise */
    val assertionId: Parser[Id] =
      (freeIdent ~ ":" ~/ Pass).?.map {
        case Some(id) => Id(ctx.scope, id)
        case None     => ctx.scope.makeNewId()
      }

    assertionId.sideEffect(id = _).silent ~
      (timedSymExpr.sideEffect(fluent = _).silent ~
        (("==" ~/ rightSideExpr ~ (":->" ~/ rightSideExpr).?).map {
          case (expr, None)     => TimedEqualAssertion(fluent, expr, Some(ctx), id)
          case (from, Some(to)) => TimedTransitionAssertion(fluent, from, to, Some(ctx), id)
        } | (":=" ~/ rightSideExpr).map(e => TimedAssignmentAssertion(fluent, e, Some(ctx), id))))
  }

  val qualifier: Parser[TemporalQualifier] =
    (interval ~/ containsKW.!.?).map {
      case (it, None)    => Equals(it)
      case (it, Some(_)) => Contains(it)
    }

  val temporallyQualifiedAssertion: Parser[Seq[TemporallyQualifiedAssertion]] = {
    (qualifier ~/
      ((("{" ~ (!"}" ~/ timedAssertion ~ ";").rep ~ "}") |
        timedAssertion.map(Seq(_)))
        ~ ";"))
      .map { case (it, assertions) => assertions.map(TemporallyQualifiedAssertion(it, _)) }
  }

  val staticAssertion: Parser[StaticAssertion] = {
    var leftExpr: StaticExpr = null
    (staticExpr.sideEffect(leftExpr = _) ~/
      (":=".! ~/ staticExpr.namedFilter(_.typ.overlaps(leftExpr.typ), "has-compatible-type")).? ~
      ";")
      .namedFilter({
        case (_: Constant, Some(_)) => true
        case (_, Some(_))           => false
        case (_, None)              => true
      }, "assignment-to-const-func-only")
      .namedFilter({
        case (_, Some(_)) => true
        case (expr, None) => expr.typ.isSubtypeOf(predef.Boolean)
      }, "boolean-if-not-assignment")
      .map {
        case (left: Constant, Some((":=", right))) => StaticAssignmentAssertion(left, right)
        case (expr, None)                          => BooleanAssertion(expr)
        case _                                     => sys.error("Something is wrong with this parser.")
      }
  }
}

/** Second phase parser that extracts all ANML elements expects types that should
  *  be already present in the initial model. */
class AnmlModuleParser(val initialModel: Model) extends AnmlParser(initialModel) {

  /** Parser for instance declaration.
    * "instance Type id1, id2, id3;" */
  val instancesDeclaration: Parser[Seq[InstanceDeclaration]] = {

    /** Parses a sequences of yet unused *distinct* identifiers. */
    def distinctFreeIdents(previous: Seq[String], sep: String, term: String): Parser[Seq[String]] =
      Pass ~ freeIdent
        .namedFilter(!previous.contains(_), "not-in-same-instance-list")
        .flatMap(name =>
          Pass ~ sep ~/ distinctFreeIdents(previous :+ name, sep, term)
            | Pass ~ term ~ PassWith(previous :+ name))

    (instanceKW ~/ declaredType ~/ distinctFreeIdents(Nil, ",", ";"))
      .map {
        case (typ, instanceNames) => instanceNames.map(name => Instance(ctx.id(name), typ))
      }
  }.map(instances => instances.map(InstanceDeclaration))

  /** Parser that to read the kind and type of a function declaration. For instance:
    * "fluent T", "constant T", "function T", "variable T", "predicate" where T is a type already declared.
    * Returns either ("fluent", T) or ("constant", T) considering that
    * 1) "variable" and "function" are alias for "fluent"
    * 2) "predicate" is an alias for "fluent boolean" */
  private[this] val functionKindAndType: Parser[(String, Type)] = {
    (word
      .filter(w => w == "fluent" || w == "variable" || w == "function")
      .opaque("fluent")
      .silent ~/ declaredType).map(("fluent", _)) |
      (constantKW ~/ declaredType).map(("constant", _)) |
      word
        .filter(_ == "predicate")
        .opaque("predicate")
        .optGet(_ => ctx.findType("boolean"), "with-boolean-type-in-scope")
        .map(("fluent", _))
  }

  val functionDeclaration: Parser[FunctionDeclaration] = {
    (functionKindAndType ~ freeIdent ~ argList ~ ";")
      .map {
        case ("fluent", typ, svName, args) =>
          FluentTemplate(ctx.id(svName), typ, args.map {
            case (name, argType) => Arg(Id(ctx.scope + svName, name), argType)
          })
        case ("constant", typ, svName, args) =>
          ConstantTemplate(ctx.id(svName), typ, args.map {
            case (name, argType) => Arg(Id(ctx.scope + svName, name), argType)
          })
        case _ => sys.error("Match failed")
      }
      .map(FunctionDeclaration(_))
  }

  /** Extract the functions declared in a type. This consumes the whole type declaration.
    * Note that the type should be already present in the module.
    * Typically consumed:
    *   "type B < A with { fluent f(C c); };" */
  val inTypeFunctionDeclaration: Parser[Seq[FunctionDeclaration]] =
    (typeKW ~/
      declaredType
      ~ ("<" ~/ declaredType.!).asInstanceOf[Parser[Type]].?
      ~ (withKW ~/ "{" ~/ functionDeclaration.rep ~ "}").?
      ~ ";")
      .map {
        case (_, _, None) => Seq()
        case (t, _, Some(funcDecl)) =>
          funcDecl.map(fd => {
            val id = Id(t.asScope, fd.id.name)
            val functionScope = t.asScope + id.name
            val selfArg = Arg(Id(functionScope, "self"), t)
            val params = selfArg +: fd.func.params.map(arg =>
              Arg(Id(functionScope, arg.id.name), arg.typ))
            val template = fd.func match {
              case _: FluentTemplate   => FluentTemplate(id, fd.func.typ, params)
              case _: ConstantTemplate => ConstantTemplate(id, fd.func.typ, params)
            }
            FunctionDeclaration(template)
          })
      }

  val action: Parser[ActionTemplate] = new AnmlActionParser(this).parser

  val elem: Parser[Seq[InModuleBlock]] =
    inTypeFunctionDeclaration |
      instancesDeclaration |
      functionDeclaration.map(Seq(_)) |
      timepointDeclaration.map(Seq(_)) |
      temporallyQualifiedAssertion |
      staticAssertion.map(Seq(_)) |
      action.map(Seq(_))

  def currentModel: Model = ctx match {
    case m: Model => m
    case _        => sys.error("Current context is not a model")
  }

  private[this] val anmlParser: Parser[Model] =
    // for each elem parsed, update the current model
    (Pass ~ elem.optGet(currentModel ++ _).sideEffect(updateContext(_)).silent).rep ~ End.map(_ =>
      currentModel)

  def parse(input: String): Parsed[Model] = {
    updateContext(initialModel)
    anmlParser.parse(input)
  }
}

class AnmlActionParser(superParser: AnmlModuleParser)(implicit predef: Predef)
    extends AnmlParser(superParser.currentModel) {

  private def currentAction: ActionTemplate = ctx match {
    case a: ActionTemplate => a
    case _                 => sys.error("Current context is not an action.")
  }

  /** Creates an action template with the given name and its default content (i.e. start/end timepoints). */
  private[this] def buildEmptyAction(actionName: String): ActionTemplate = {
    val container = ctx match {
      case m: Model => m
      case _        => sys.error("Starting to parse an action while the context is not a model.")
    }
    val emptyAct = ActionTemplate(ctx.scope / actionName, container)
    emptyAct +
      LocalVarDeclaration(Timepoint(Id(emptyAct.scope, "start"))) +
      LocalVarDeclaration(Timepoint(Id(emptyAct.scope, "end")))
  }

  /** FIXME: this interprets a "constant" as a local variable. This is is compatible with FAPE but not with official ANML. */
  val variableDeclaration: Parser[LocalVarDeclaration] = {
    (constantKW ~/ declaredType ~/ freeIdent ~/ ";").map {
      case (typ, id) =>
        LocalVarDeclaration(LocalVar(Id(ctx.scope, id), typ))
    }
  }

  val parser: Parser[ActionTemplate] =
    (Pass ~ actionKW.sideEffect(x => {
      // set context to the current model in order to access previous declarations
      updateContext(superParser.currentModel)
    }) ~/
      freeIdent.sideEffect(actionName => {
        // set context to the current action
        updateContext(buildEmptyAction(actionName))
      }) ~/
      argList // parse arguments and update the current action
        .map(_.map {
          case (name, typ) => ArgDeclaration(Arg(Id(ctx.scope, name), typ))
        })
        .sideEffect(argDeclarations => updateContext(currentAction ++ argDeclarations)) ~
      "{" ~/
      (temporallyQualifiedAssertion |
        staticAssertion.map(Seq(_)) |
        variableDeclaration.map(Seq(_)))
        .sideEffect(x => updateContext(currentAction ++ x)) // add assertions to the current action
        .rep ~
      "}" ~/
      ";")
      .flatMap(_ => PassWith(currentAction))
}

/** First phase parser used to extract all type declarations from a given ANML string. */
class AnmlTypeParser(val initialModel: Model)(implicit predef: Predef)
    extends AnmlParser(initialModel) {

  val nonTypeToken: Parser[String] =
    (word | int | CharIn("{}[]();=:<>-+.,!/*")).!.namedFilter(_ != "type", "non-type-token")
  val typeDeclaration: Parser[TypeDeclaration] =
    (typeKW ~/ freeIdent ~ ("<" ~ declaredType).? ~ (";" | withKW)).map {
      case (name, None)                    => TypeDeclaration(Type.ObjSubType(ctx.id(name), Type.ObjectTop))
      case (name, Some(t: Type.ObjType))   => TypeDeclaration(Type.ObjSubType(ctx.id(name), t))
      case (name, Some(t: Type.IIntType))  => TypeDeclaration(Type.IntSubType(ctx.id(name), t))
      case (name, Some(t: Type.IRealType)) => TypeDeclaration(Type.RealSubType(ctx.id(name), t))
    }

  private[this] def currentModel: Model = ctx match {
    case m: Model => m
    case x        => sys.error("Current context is not a model")
  }

  private[this] val parser: Parser[Model] = {
    val typeDeclarationWithUpdate =
      typeDeclaration.optGet(currentModel + _).sideEffect(updateContext(_))
    ((Pass ~ (nonTypeToken | typeDeclarationWithUpdate).silent).rep ~ End).map(_ => currentModel)
  }
  def parse(input: String): Parsed[Model] = {
    updateContext(initialModel)
    parser.parse(input)
  }
}

object Parser {

  /** ANML model with default definitions already added */
  def baseAnmlModel(implicit predef: Predef): Model = predef.baseModel

  /** Parses an ANML string. If the previous model parameter is Some(m), then the result
    * of parsing will be appended to m.
    **/
  def parse(input: String, previousModel: Option[Model] = None): ParseResult[Model] = {
    def formatFailure(failure: Failure[Char, String]): ParserFailure = {
      def toLineAndColumn(lines: Seq[String], index: Int, lineNumber: Int = 0): (String, Int, Int) =
        lines match {
          case Seq(head, _*) if index <= head.length =>
            (lines.head, lineNumber, index)
          case Seq(head, tail @ _*) =>
            toLineAndColumn(tail, index - head.length - 1, lineNumber + 1)
          case _ =>
            sys.error("Index is not in the provided lines")
        }

      val (faultyLine, faultyLineNumber, faultyColumnNumber) =
        toLineAndColumn(input.split('\n'), failure.index)
      ParserFailure(faultyLine, faultyLineNumber, faultyColumnNumber, failure.lastParser, None)
    }

    Try {
      new AnmlTypeParser(previousModel.getOrElse(baseAnmlModel)).parse(input) match {
        case Success(modelWithTypes, _) =>
          new AnmlModuleParser(modelWithTypes).parse(input) match {
            case Success(fullModel, _)    => ParseSuccess(fullModel)
            case x: Failure[Char, String] => formatFailure(x)
          }
        case x: Failure[Char, String] => formatFailure(x)
      }
    } match {
      case scala.util.Success(x) => x
      case scala.util.Failure(e) => ParserCrash(e, None)
    }
  }

  private def parseFromFile(file: File, previousModel: Option[Model] = None): ParseResult[Model] = {
    Try {
      val source = scala.io.Source.fromFile(file)
      val input: String = source.getLines.mkString("\n")
      source.close()
      parse(input, previousModel) match {
        case x: ParserFailure => x.copy(file = Some(file))
        case x                => x
      }
    } match {
      case scala.util.Success(x) => x
      case scala.util.Failure(e: IOException) =>
        FileAccessError(file, e)
      case scala.util.Failure(e) => ParserCrash(e, Some(file))

    }
  }

  /** Parses an ANML file.
    * If the file name is formated as "XXXX.YYY.pb.anml", the file "XXXX.dom.anml" will be parsed
    * first and its content prepended to the model.
    **/
  def parse(file: File): ParseResult[Model] = {
    file.getName.split('.') match {
      case Array(domId, pbId, "pb", "anml") =>
        // file name formated as domainID.pbID.pb.anml, load domainID.dom.anml first
        val domainFile = new File(file.getParentFile, domId + ".dom.anml")
        Parser
          .parseFromFile(domainFile)
          .flatMap(domainModel => parseFromFile(file, Some(domainModel)))
      case _ =>
        // not a problem file, load the file standalone
        Parser.parseFromFile(file)
    }
  }
}
