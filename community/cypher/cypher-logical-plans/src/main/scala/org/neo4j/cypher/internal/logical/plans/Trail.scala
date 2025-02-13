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
package org.neo4j.cypher.internal.logical.plans

import org.neo4j.cypher.internal.util.Repetition
import org.neo4j.cypher.internal.util.attribution.IdGen

/**
 * Used to solve queries like: `(start) [(innerStart)-->(innerEnd)]{i, j} (end)`
 *
 * @param left                          source plan
 * @param right                         inner plan to repeat
 * @param repetition                    how many times to repeat the RHS on each partial result
 * @param start                         the outside node variable where the quantified pattern
 *                                      starts. Assumed to be present in the output of `left`.
 *                                      [[start]] (and for subsequent iterations [[innerEnd]]) is projected to [[innerStart]].
 * @param end                           the outside node variable where the quantified pattern
 *                                      ends. Projected in output if present.
 * @param innerStart                    the node variable where the inner pattern starts
 * @param innerEnd                      the node variable where the inner pattern ends.
 *                                      [[innerEnd]] will eventually be projected to [[end]] (if present).
 * @param nodeVariableGroupings         node variables to aggregate
 * @param relationshipVariableGroupings relationship variables to aggregate
 * @param innerRelationships    all inner relationships, whether they get projected or not
 * @param previouslyBoundRelationships all relationship variables of the same MATCH that are present in lhs
 * @param previouslyBoundRelationshipGroups all relationship group variables of the same MATCH that are present in lhs
*/
case class Trail(
  override val left: LogicalPlan,
  override val right: LogicalPlan,
  repetition: Repetition,
  start: String,
  end: String,
  innerStart: String,
  innerEnd: String,
  nodeVariableGroupings: Set[VariableGrouping],
  relationshipVariableGroupings: Set[VariableGrouping],
  innerRelationships: Set[String],
  previouslyBoundRelationships: Set[String],
  previouslyBoundRelationshipGroups: Set[String]
)(implicit idGen: IdGen)
    extends LogicalBinaryPlan(idGen) with ApplyPlan {
  override def withLhs(newLHS: LogicalPlan)(idGen: IdGen): LogicalBinaryPlan = copy(left = newLHS)(idGen)
  override def withRhs(newRHS: LogicalPlan)(idGen: IdGen): LogicalBinaryPlan = copy(right = newRHS)(idGen)

  override val availableSymbols: Set[String] =
    left.availableSymbols + end + start ++ nodeVariableGroupings.map(_.groupName) ++ relationshipVariableGroupings.map(
      _.groupName
    )
}

/**
 * Describes a variable that is exposed from a QuantifiedPath.
 *
 * @param singletonName the name of the singleton variable inside the QuantifiedPath.
 * @param groupName the name of the group variable exposed outside of the QuantifiedPath.
 */
case class VariableGrouping(singletonName: String, groupName: String)
