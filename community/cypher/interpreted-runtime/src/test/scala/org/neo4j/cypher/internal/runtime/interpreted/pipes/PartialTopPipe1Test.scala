/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import java.util.Comparator

import org.neo4j.cypher.internal.runtime.ExecutionContext
import org.neo4j.cypher.internal.runtime.interpreted.QueryStateHelper
import org.neo4j.cypher.internal.runtime.interpreted.ValueComparisonHelper.beEquivalentTo
import org.neo4j.cypher.internal.v4_0.util.test_helpers.CypherFunSuite

class PartialTopPipe1Test extends CypherFunSuite {

  private val compareX: Comparator[ExecutionContext] = ExecutionContextOrdering.asComparator(List(Ascending("x")))
  private val compareY: Comparator[ExecutionContext] = ExecutionContextOrdering.asComparator(List(Ascending("y")))
  private val compareYDesc: Comparator[ExecutionContext] = ExecutionContextOrdering.asComparator(List(Descending("y")))

  test("empty top should be empty") {
    val source = new FakePipe(List())
    val sortPipe = PartialTop1Pipe(source, compareX, compareY)()

    sortPipe.createResults(QueryStateHelper.emptyWithValueSerialization) should be(empty)
  }

  test("simple sorting works as expected") {
    val list = List(
      Map("x" -> "A", "y" -> 2),
      Map("x" -> "A", "y" -> 1),
      Map("x" -> "B", "y" -> 0)
    )
    val source = new FakePipe(list)
    val sortPipe = PartialTop1Pipe(source, compareX, compareY)()

    sortPipe.createResults(QueryStateHelper.emptyWithValueSerialization).toList should beEquivalentTo(List(Map("x" -> "A", "y" -> 1)))
  }

  test("two ties for the first place are not all returned") {
    val input = List(
      Map("x" -> 1, "y" -> 5),
      Map("x" -> 1, "y" -> 5),
      Map("x" -> 1, "y" -> 2),
      Map("x" -> 1, "y" -> 2),
      Map("x" -> 2, "y" -> 4),
      Map("x" -> 2, "y" -> 3),
      Map("x" -> 2, "y" -> 0)
    )

    val source = new FakePipe(input)
    val sortPipe = PartialTop1Pipe(source, compareX, compareY)()

    sortPipe.createResults(QueryStateHelper.emptyWithValueSerialization).toList should beEquivalentTo(List(
      Map("x" -> 1, "y" -> 2)))
  }

  test("if only null is present, it should be returned") {
    val input = List(
      Map[String,Any]("x" -> null, "y" -> null),
      Map[String,Any]("x" -> null, "y" -> null),
      Map[String,Any]("x" -> null, "y" -> 2),
      Map[String,Any]("x" -> null, "y" -> 2)
    )

    val source = new FakePipe(input)
    val sortPipe = PartialTop1Pipe(source, compareX, compareYDesc)()

    sortPipe.createResults(QueryStateHelper.emptyWithValueSerialization).toList should beEquivalentTo(List(
      Map("x" -> null, "y" -> null)))
  }

  test("partial top 1 should be lazy") {
    val input = List(
      Map("x" -> 1, "y" -> 5),
      Map("x" -> 1, "y" -> 5),
      Map("x" -> 1, "y" -> 2),
      Map("x" -> 1, "y" -> 2),
      Map("x" -> 2, "y" -> 4),
      Map("x" -> 2, "y" -> 3),
      Map("x" -> 2, "y" -> 4)
    )

    val source = new FakePipe(input)
    val sortPipe = PartialTop1Pipe(source, compareX, compareY)()

    val iterator = sortPipe.createResults(QueryStateHelper.emptyWithValueSerialization)

    iterator.next() // first 1, 2
    source.numberOfPulledRows should be(5)

    iterator.hasNext should be(false)
  }
}
