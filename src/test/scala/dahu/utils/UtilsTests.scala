package dahu.utils

import org.scalatest.FreeSpec
import Graph._

class UtilsTests extends FreeSpec {

  // graph with 2 SCC
  val g1: Map[Int, Set[Int]] = Map(1 -> Set(2, 3), 2 -> Set(1))

  // directed acyclic graphs
  val dag1: Map[Int, Set[Int]] = Map(1 -> Set(2), 2    -> Set(3), 3    -> Set(4))
  val dag2: Map[Int, Set[Int]] = Map(1 -> Set(2, 4), 2 -> Set(3, 4), 3 -> Set(4))

  "graph-utils" - {
    "tarjan" in {

      assert(tarjan(g1) == Seq(Set(1, 2), Set(3)))
      assert(tarjan(dag1) == (1 to 4).map(Set(_)))
      assert(tarjan(dag2) == (1 to 4).map(Set(_)))

    }
    "topological-order" in {
      assert(topologicalOrder(g1).isEmpty)
      assert(topologicalOrder(dag1).contains(1 to 4))
      assert(topologicalOrder(dag2).contains(1 to 4))
    }
  }

}