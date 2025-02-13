/*
 * Copyright (c) "Neo4j"
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
package org.neo4j.cypher.internal.runtime.slotted.expressions

import org.neo4j.cypher.internal.runtime.ReadableRow
import org.neo4j.cypher.internal.runtime.interpreted.commands.AstNode
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.Predicate
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState

case class PrimitiveEquals(a: Expression, b: Expression) extends Predicate with SlottedExpression {

  override def isMatch(ctx: ReadableRow, state: QueryState): Option[Boolean] = {
    val value1 = a(ctx, state)
    val value2 = b(ctx, state)
    Some(value1 == value2)
  }
  override def containsIsNull: Boolean = false

  override def rewrite(f: Expression => Expression): Expression = f(PrimitiveEquals(a.rewrite(f), b.rewrite(f)))

  override def children: Seq[AstNode[_]] = Seq(a, b)
}
