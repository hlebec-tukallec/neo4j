/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.rewriting.rewriters

import org.neo4j.cypher.internal.expressions.AllIterablePredicate
import org.neo4j.cypher.internal.expressions.Disjoint
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.In
import org.neo4j.cypher.internal.expressions.NoneIterablePredicate
import org.neo4j.cypher.internal.expressions.SingleIterablePredicate
import org.neo4j.cypher.internal.expressions.Unique
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.topDown

/**
 * Removes [[Disjoint]] and [[Unique]] predicates into expressions that the runtime can evaluate.
 */
case class UniquenessRewriter(anonymousVariableNameGenerator: AnonymousVariableNameGenerator) extends Rewriter {

  private val instance = topDown(Rewriter.lift {
    case d @ Disjoint(x, y) =>
      val innerX = Variable(anonymousVariableNameGenerator.nextName)(x.position)
      NoneIterablePredicate(
        innerX,
        x,
        Some(In(innerX.copyId, y)(d.position))
      )(d.position)

    case u @ Unique(list) =>
      val element1 = Variable(anonymousVariableNameGenerator.nextName)(list.position)
      val element2 = Variable(anonymousVariableNameGenerator.nextName)(list.position)
      AllIterablePredicate(
        element1,
        list,
        Some(SingleIterablePredicate(
          element2,
          list.endoRewrite(copyVariables),
          Some(Equals(element1.copyId, element2.copyId)(list.position))
        )(u.position))
      )(u.position)
  })

  override def apply(value: AnyRef): AnyRef = instance(value)
}
