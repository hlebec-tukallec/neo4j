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
package org.neo4j.cypher.internal.compiler.planner.logical.steps

import org.neo4j.cypher.internal.ast.UsingScanHint
import org.neo4j.cypher.internal.compiler.planner.logical.LeafPlanner
import org.neo4j.cypher.internal.compiler.planner.logical.LogicalPlanningContext
import org.neo4j.cypher.internal.compiler.planner.logical.ordering.InterestingOrderConfig
import org.neo4j.cypher.internal.compiler.planner.logical.ordering.ResultOrdering
import org.neo4j.cypher.internal.compiler.planner.logical.steps.RelationshipLeafPlanner.planHiddenSelectionAndRelationshipLeafPlan
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LabelOrRelTypeName
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.ir.PatternRelationship
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.SimplePatternLength
import org.neo4j.cypher.internal.logical.plans.LogicalPlan

case class unionRelationshipTypeScanLeafPlanner(skipIDs: Set[String]) extends LeafPlanner {

  override def apply(
    queryGraph: QueryGraph,
    interestingOrderConfig: InterestingOrderConfig,
    context: LogicalPlanningContext
  ): Set[LogicalPlan] = {
    def shouldIgnore(pattern: PatternRelationship) =
      !context.planContext.canLookupRelationshipsByType ||
        queryGraph.argumentIds.contains(pattern.name) ||
        skipIDs.contains(pattern.name) ||
        skipIDs.contains(pattern.left) ||
        skipIDs.contains(pattern.right)

    queryGraph.patternRelationships.flatMap {

      case relationship @ PatternRelationship(name, (_, _), _, types, SimplePatternLength)
        if types.distinct.length > 1 && !shouldIgnore(relationship) =>
        Some(planHiddenSelectionAndRelationshipLeafPlan(
          queryGraph.argumentIds,
          relationship,
          context,
          planUnionRelationshipTypeScan(name, types, _, _, _, queryGraph, interestingOrderConfig, context)
        ))

      case _ => None
    }
  }

  private def planUnionRelationshipTypeScan(
    name: String,
    types: Seq[RelTypeName],
    patternForLeafPlan: PatternRelationship,
    originalPattern: PatternRelationship,
    hiddenSelections: Seq[Expression],
    queryGraph: QueryGraph,
    interestingOrderConfig: InterestingOrderConfig,
    context: LogicalPlanningContext
  ): LogicalPlan = {
    def providedOrderFor = ResultOrdering.providedOrderForRelationshipTypeScan(
      interestingOrderConfig.orderToSolve,
      _,
      context.providedOrderFactory
    )
    context.logicalPlanProducer.planUnionRelationshipByTypeScan(
      name,
      types,
      patternForLeafPlan,
      originalPattern,
      hiddenSelections,
      hints(queryGraph, originalPattern),
      queryGraph.argumentIds,
      providedOrderFor(name),
      context
    )
  }

  private def hints(queryGraph: QueryGraph, patternRelationship: PatternRelationship): Seq[UsingScanHint] = {
    queryGraph.hints.toSeq.collect {
      case hint @ UsingScanHint(Variable(patternRelationship.name), LabelOrRelTypeName(relTypeName))
        if patternRelationship.types.map(_.name).contains(relTypeName) => hint
    }
  }

}
