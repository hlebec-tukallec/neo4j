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
package org.neo4j.cypher.internal.frontend

import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase.AST_REWRITE
import org.neo4j.cypher.internal.frontend.phases.OpenCypherJavaCCParsing
import org.neo4j.cypher.internal.frontend.phases.Phase
import org.neo4j.cypher.internal.frontend.phases.SemanticAnalysis
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.rewriting.rewriters.projectNamedPaths
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.InternalNotification
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.SubqueryVariableShadowing
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class SemanticAnalysisTest extends CypherFunSuite with SemanticAnalysisTestSuite {

  private val pipelineWithMultiGraphs = pipelineWithSemanticFeatures(
    SemanticFeature.MultipleGraphs,
    SemanticFeature.WithInitialQuerySignature
  )

  private val pipelineWithUseGraphSelector = pipelineWithSemanticFeatures(
    SemanticFeature.MultipleGraphs,
    SemanticFeature.WithInitialQuerySignature,
    SemanticFeature.UseGraphSelector
  )

  private val pipelineWithExpressionsInView = pipelineWithSemanticFeatures(
    SemanticFeature.MultipleGraphs,
    SemanticFeature.WithInitialQuerySignature,
    SemanticFeature.UseGraphSelector,
    SemanticFeature.ExpressionsInViewInvocations
  )

  private val pipelineWithCallInTxsEnhancements = pipelineWithSemanticFeatures(
    SemanticFeature.CallInTxsStatusAndErrorHandling
  )

  private val emptyTokenErrorMessage =
    "'' is not a valid token name. Token names cannot be empty or contain any null-bytes."

  test("should fail for max() with no arguments") {
    val query = "RETURN max() AS max"
    expectErrorsFrom(
      query,
      Set(SemanticError("Insufficient parameters for function 'max'", InputPosition(7, 1, 8)))
    )
  }

  test("Should allow overriding variable name in RETURN clause with an ORDER BY") {
    val query = "MATCH (n) RETURN n.prop AS n ORDER BY n + 2"
    expectNoErrorsFrom(query)
  }

  test("Should not allow multiple columns with the same name in WITH") {
    val query = "MATCH (n) WITH n.prop AS n, n.foo AS n ORDER BY n + 2 RETURN 1 AS one"
    expectErrorsFrom(
      query,
      Set(SemanticError("Multiple result columns with the same name are not supported", InputPosition(15, 1, 16)))
    )
  }

  test("Should not allow duplicate variable name in CREATE") {
    val query = "CREATE (n), (n) RETURN 1 as one"
    expectErrorsFrom(
      query,
      Set(SemanticError("Variable `n` already declared", InputPosition(13, 1, 14)))
    )
  }

  test("Should not allow Distinct in functions that aren't aggregate") {
    val nonAggregateFunctions = Seq(
      ("localdatetime", "'param1'"),
      ("duration", "'param1'"),
      ("left", "'param1', 4"),
      ("right", "'param1', 4"),
      ("reverse", "'param1'"),
      ("trim", "'param1'"),
      ("ceil", "0.1"),
      ("floor", "0.1"),
      ("sign", "0.1"),
      ("round", "0.1"),
      ("abs", "0.1"),
      ("asin", "0.1"),
      ("isEmpty", "'param1'"),
      ("toBoolean", "'param1'")
    )
    nonAggregateFunctions.foreach {
      case (func, params) =>
        val query = s"RETURN $func(DISTINCT $params)"
        expectErrorsFrom(
          query,
          Set(SemanticError(s"Invalid use of DISTINCT with function '$func'", InputPosition(7, 1, 8)))
        )
    }
  }

  test("Should not allow parameter maps in MATCH") {
    val query = "MATCH (n $foo) RETURN 1"
    expectErrorsFrom(
      query,
      Set(SemanticError(
        "Parameter maps cannot be used in `MATCH` patterns (use a literal map instead, e.g. `{id: $foo.id}`)",
        InputPosition(9, 1, 10)
      ))
    )
  }

  test("Should not allow parameter maps in MERGE") {
    val query = "MERGE (n $foo) RETURN 1"
    expectErrorsFrom(
      query,
      Set(SemanticError(
        "Parameter maps cannot be used in `MERGE` patterns (use a literal map instead, e.g. `{id: $foo.id}`)",
        InputPosition(9, 1, 10)
      ))
    )
  }

  test("Should allow parameter as valid predicate in FilteringExpression") {
    val queries = Seq(
      "RETURN [x IN [1,2,3] WHERE $p | x + 1] AS foo",
      "RETURN all(x IN [1,2,3] WHERE $p) AS foo",
      "RETURN any(x IN [1,2,3] WHERE $p) AS foo",
      "RETURN none(x IN [1,2,3] WHERE $p) AS foo",
      "RETURN single(x IN [1,2,3] WHERE $p) AS foo"
    )
    queries.foreach { query =>
      withClue(query) {
        val pipeline = pipelineWithSemanticFeatures()
        val initialState = initialStateWithQuery(query).withParams(Map("p" -> 42))
        val result = runSemanticAnalysisWithPipelineAndState(pipeline, initialState)

        result.errors shouldBe empty
      }
    }
  }

  test("Should allow pattern as valid predicate in FilteringExpression") {
    val queries = Seq(
      "MATCH (n) RETURN [x IN [1,2,3] WHERE (n)--() | x + 1] AS foo",
      "MATCH (n) RETURN all(x IN [1,2,3] WHERE (n)--()) AS foo",
      "MATCH (n) RETURN any(x IN [1,2,3] WHERE (n)--()) AS foo",
      "MATCH (n) RETURN none(x IN [1,2,3] WHERE (n)--()) AS foo",
      "MATCH (n) RETURN single(x IN [1,2,3] WHERE (n)--()) AS foo"
    )
    queries.foreach { query =>
      withClue(query) {
        expectNoErrorsFrom(query)
      }
    }
  }

  // Escaped backticks in tokens

  test("Should allow escaped backticks in node property key name") {
    // Property without escaping: `abc123``
    val query = "CREATE ({prop: 5, ```abc123`````: 1})"
    expectNoErrorsFrom(query)
  }

  test("Should allow escaped backticks in relationship property key name") {
    // Property without escaping: abc`123
    val query = "MATCH ()-[r]->() RETURN r.`abc``123` as result"
    expectNoErrorsFrom(query)
  }

  test("Should allow escaped backticks in label") {
    // Label without escaping: `abc123
    val query = "MATCH (n) SET n:```abc123`"
    expectNoErrorsFrom(query)
  }

  test("Should allow escaped backtick in relationship type") {
    // Relationship type without escaping: abc123``
    val query = "MERGE ()-[r:`abc123`````]->()"
    expectNoErrorsFrom(query)
  }

  test("Should allow escaped backtick in indexes") {
    // Query without proper escaping: CREATE INDEX `abc`123`` FOR (n:`Per`son`) ON (n.first``name`, n.``last`name)
    val query = "CREATE INDEX ```abc``123````` FOR (n:```Per``son```) ON (n.`first````name```, n.`````last``name`)"
    expectNoErrorsFrom(query)
  }

  test("Should allow escaped backtick in constraints") {
    // Query without proper escaping: CREATE CONSTRAINT abc123` FOR (n:``Label) REQUIRE (n.pr``op) IS NODE KEY
    val query = "CREATE CONSTRAINT `abc123``` FOR (n:`````Label`) REQUIRE (n.`pr````op`) IS NODE KEY"
    expectNoErrorsFrom(query)
  }

  test("Should register uses in PathExpressions") {
    val query = "MATCH p = (a)-[r]-(b) RETURN p AS p"

    val pipeline = OpenCypherJavaCCParsing andThen ProjectNamedPathsPhase andThen SemanticAnalysis(warn = true)
    val result = runSemanticAnalysisWithPipeline(pipeline, query)
    val scopeTree = result.state.semantics().scopeTree

    Set("a", "r", "b").foreach { name =>
      scopeTree.allSymbols(name).head.uses shouldNot be(empty)
    }
  }

  test("nested CALL { ... } IN TRANSACTIONS") {
    val query = "CALL { CALL { CREATE (x) } IN TRANSACTIONS } IN TRANSACTIONS RETURN 1 AS result"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Nested CALL { ... } IN TRANSACTIONS is not supported", InputPosition(7, 1, 8))
      )
    )
  }

  test("regular CALL nested in CALL { ... } IN TRANSACTIONS") {
    val query = "CALL { CALL { CREATE (x) } } IN TRANSACTIONS RETURN 1 AS result"
    expectNoErrorsFrom(query)
  }

  test("CALL { ... } IN TRANSACTIONS nested in a regular CALL") {
    val query = "CALL { CALL { CREATE (x) } IN TRANSACTIONS } RETURN 1 AS result"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL { ... } IN TRANSACTIONS nested in a regular CALL is not supported", InputPosition(7, 1, 8))
      )
    )
  }

  test("CALL { ... } IN TRANSACTIONS nested in a regular CALL and nested CALL { ... } IN TRANSACTIONS") {
    val query = "CALL { CALL { CALL { CREATE (x) } IN TRANSACTIONS } IN TRANSACTIONS } RETURN 1 AS result"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Nested CALL { ... } IN TRANSACTIONS is not supported", InputPosition(14, 1, 15)),
        SemanticError("CALL { ... } IN TRANSACTIONS nested in a regular CALL is not supported", InputPosition(7, 1, 8))
      )
    )
  }

  test("CALL { ... } IN TRANSACTIONS in a UNION") {
    val query =
      """CALL { CREATE (x) } IN TRANSACTIONS
        |RETURN 1 AS result
        |UNION
        |CALL { CREATE (x) } IN TRANSACTIONS
        |RETURN 2 AS result""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL { ... } IN TRANSACTIONS in a UNION is not supported", InputPosition(0, 1, 1)),
        SemanticError("CALL { ... } IN TRANSACTIONS in a UNION is not supported", InputPosition(61, 4, 1))
      )
    )
  }

  test("CALL { ... } IN TRANSACTIONS in first part of UNION") {
    val query =
      """CALL { CREATE (x) } IN TRANSACTIONS
        |RETURN 1 AS result
        |UNION
        |RETURN 2 AS result""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL { ... } IN TRANSACTIONS in a UNION is not supported", InputPosition(0, 1, 1))
      )
    )
  }

  test("CALL { ... } IN TRANSACTIONS in second part of UNION") {
    val query =
      """RETURN 1 AS result
        |UNION
        |CALL { CREATE (x) } IN TRANSACTIONS
        |RETURN 2 AS result""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL { ... } IN TRANSACTIONS in a UNION is not supported", InputPosition(25, 3, 1))
      )
    )
  }

  test("CALL { ... } IN TRANSACTIONS with a preceding write clause") {
    val query =
      """CREATE (foo)
        |WITH foo AS foo
        |CALL { CREATE (x) } IN TRANSACTIONS
        |RETURN foo AS foo""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL { ... } IN TRANSACTIONS after a write clause is not supported", InputPosition(29, 3, 1))
      )
    )
  }

  test("Multiple CALL { ... } IN TRANSACTIONS with preceding write clauses") {
    val query =
      """CREATE (foo)
        |WITH foo AS foo
        |CALL { CREATE (x) } IN TRANSACTIONS
        |CALL { CREATE (x) } IN TRANSACTIONS
        |RETURN foo AS foo""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL { ... } IN TRANSACTIONS after a write clause is not supported", InputPosition(29, 3, 1)),
        SemanticError("CALL { ... } IN TRANSACTIONS after a write clause is not supported", InputPosition(65, 4, 1))
      )
    )
  }

  test("Multiple CALL { ... } IN TRANSACTIONS with a write clause between them") {
    val query =
      """CALL { CREATE (x) } IN TRANSACTIONS
        |CREATE (foo)
        |WITH foo AS foo
        |CALL { CREATE (x) } IN TRANSACTIONS
        |RETURN foo AS foo""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL { ... } IN TRANSACTIONS after a write clause is not supported", InputPosition(65, 4, 1))
      )
    )
  }

  test("CALL { ... } IN TRANSACTIONS with a preceding nested write clause") {
    val query =
      """CALL { CREATE (foo) RETURN foo AS foo }
        |WITH foo AS foo
        |CALL { CREATE (x) } IN TRANSACTIONS
        |RETURN foo AS foo""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL { ... } IN TRANSACTIONS after a write clause is not supported", InputPosition(56, 3, 1))
      )
    )
  }

  test("CALL { ... } IN TRANSACTIONS with a preceding nested write clause in a unit subquery") {
    val query =
      """CALL { CREATE (x) }
        |WITH 1 AS foo
        |CALL { CREATE (x) } IN TRANSACTIONS
        |RETURN foo AS foo""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL { ... } IN TRANSACTIONS after a write clause is not supported", InputPosition(34, 3, 1))
      )
    )
  }

  test("Multiple CALL { ... } IN TRANSACTIONS that contain write clauses, but no write clauses in between") {
    val query =
      """CALL { CREATE (x) } IN TRANSACTIONS
        |WITH 1 AS foo
        |CALL { CREATE (y) } IN TRANSACTIONS
        |RETURN foo AS foo""".stripMargin
    expectNoErrorsFrom(query)
  }

  test("CALL { ... } IN TRANSACTIONS with a following write clause") {
    val query =
      """CALL { CREATE (x) } IN TRANSACTIONS
        |CREATE (foo)
        |RETURN foo AS foo""".stripMargin
    expectNoErrorsFrom(query)
  }

  test("should allow node pattern predicates in MATCH") {
    val query =
      "WITH 123 AS minValue MATCH (n {prop: 42} WHERE n.otherProp > minValue)-->(m:Label WHERE m.prop = 42) RETURN n AS result"
    expectNoErrorsFrom(query)
  }

  test("should allow node pattern predicates in MATCH to refer to other nodes") {
    val query = "MATCH (start)-->(end:Label WHERE start.prop = 42) RETURN start AS result"
    expectNoErrorsFrom(query)
  }

  test("should allow node pattern predicates in shortest path to refer to other nodes") {
    val query = "MATCH (a), (b) MATCH shortestPath( (a)-->(b WHERE c.prop = 42) ), (c) RETURN count(*) AS result"
    expectNoErrorsFrom(query)
  }

  test("should allow node property predicates in shortest path to refer to other nodes") {
    val query = "MATCH (a), (b) MATCH shortestPath( (a)-->(b {prop: c.prop}) ), (c) RETURN count(*) AS result"
    expectNoErrorsFrom(query)
  }

  test("should not allow node pattern predicates in CREATE") {
    val query = "CREATE (n WHERE n.prop = 123)"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Node pattern predicates are not allowed in CREATE, but only in MATCH clause or inside a pattern comprehension",
          InputPosition(23, 1, 24)
        )
      )
    )
  }

  test("should not allow node pattern predicates in MERGE") {
    val query = "MERGE (n WHERE n.prop = 123)"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Node pattern predicates are not allowed in MERGE, but only in MATCH clause or inside a pattern comprehension",
          InputPosition(22, 1, 23)
        )
      )
    )
  }

  test("should allow node pattern predicates in pattern comprehension") {
    val query =
      "WITH 123 AS minValue RETURN [(n {prop: 42} WHERE n.otherProp > minValue)-->(m:Label WHERE m.prop = 42) | n] AS result"
    expectNoErrorsFrom(query)
  }

  test("should allow node pattern predicates in pattern comprehension to refer to other nodes") {
    val query = "RETURN [(start)-->(end:Label WHERE start.prop = 42) | start] AS result"
    expectNoErrorsFrom(query)
  }

  test("should not allow node pattern predicates in pattern expression") {
    val query =
      """MATCH (a), (b)
        |RETURN exists((a WHERE a.prop > 123)-->(b)) AS result""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Node pattern predicates are not allowed in expression, but only in MATCH clause or inside a pattern comprehension",
          InputPosition(45, 2, 31)
        )
      )
    )
  }

  test("should allow node pattern predicates in MATCH with shortestPath") {
    val query =
      """
        |WITH 123 AS minValue
        |MATCH p = shortestPath((n {prop: 42} WHERE n.otherProp > minValue)-[:REL*]->(m:Label WHERE m.prop = 42))
        |RETURN n AS result
        |""".stripMargin
    expectNoErrorsFrom(query)
  }

  test("should allow node pattern predicates in MATCH with shortestPath to refer to other nodes") {
    val query =
      """
        |MATCH p = shortestPath((start)-[:REL*]->(end:Label WHERE start.prop = 42))
        |RETURN start AS result""".stripMargin
    expectNoErrorsFrom(query)
  }

  test("should not allow node pattern predicates in shortestPath expression") {
    val query =
      """
        |MATCH (a), (b)
        |WITH shortestPath((a WHERE a.prop > 123)-[:REL*]->(b)) AS p
        |RETURN length(p) AS result""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Node pattern predicates are not allowed in expression, but only in MATCH clause or inside a pattern comprehension",
          InputPosition(50, 3, 35)
        )
      )
    )
  }

  test("should allow relationship pattern predicates in MATCH") {
    val query =
      "WITH 123 AS minValue MATCH (n)-[r:Relationship {prop: 42} WHERE r.otherProp > minValue]->(m) RETURN r AS result"
    expectNoErrorsFrom(query)
  }

  test("should not allow relationship pattern predicates in MATCH when path length is provided") {
    val query =
      "WITH 123 AS minValue MATCH (n)-[r:Relationship*1..3 {prop: 42} WHERE r.otherProp > minValue]->(m) RETURN r AS result"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Relationship pattern predicates are not supported for variable-length relationships.",
          InputPosition(81, 1, 82)
        )
      )
    )
  }

  test("should allow relationship pattern predicates in MATCH to refer to nodes") {
    val query = "MATCH (n)-[r:Relationship WHERE n.prop = 42]->(m:Label) RETURN r AS result"
    expectNoErrorsFrom(query)
  }

  test("should not allow relationship pattern predicates in CREATE") {
    val query = "CREATE (n)-[r:Relationship WHERE r.prop = 42]->(m)"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Relationship pattern predicates are not allowed in CREATE, but only in MATCH clause or inside a pattern comprehension",
          InputPosition(40, 1, 41)
        )
      )
    )
  }

  test("should not allow relationship pattern predicates in MERGE") {
    val query = "MERGE (n)-[r:Relationship WHERE r.prop = 42]->(m)"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Relationship pattern predicates are not allowed in MERGE, but only in MATCH clause or inside a pattern comprehension",
          InputPosition(39, 1, 40)
        )
      )
    )
  }

  test("should allow relationship pattern predicates in pattern comprehension") {
    val query =
      "WITH 123 AS minValue RETURN [(n)-[r:Relationship {prop: 42} WHERE r.otherProp > minValue]->(m) | r] AS result"
    expectNoErrorsFrom(query)
  }

  test("should allow relationship pattern predicates in pattern comprehension to refer to nodes") {
    val query = "RETURN [(n)-[r:Relationship WHERE n.prop = 42]->(m:Label) | r] AS result"
    expectNoErrorsFrom(query)
  }

  test("should not allow relationship pattern predicates in pattern expression") {
    val query =
      """MATCH (a)-[r]->(b)
        |RETURN exists((a)-[r WHERE r.prop > 123]->(b)) AS result""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Relationship pattern predicates are not allowed in expression, but only in MATCH clause or inside a pattern comprehension",
          InputPosition(53, 2, 35)
        )
      )
    )
  }

  test("CALL IN TRANSACTIONS with batchSize 1") {
    val query =
      """CALL {
        |  CREATE ()
        |} IN TRANSACTIONS OF 1 ROW
        |""".stripMargin
    expectNoErrorsFrom(query)
  }

  test("CALL IN TRANSACTIONS with batchSize 0") {
    val query =
      """CALL {
        |  CREATE ()
        |} IN TRANSACTIONS OF 0 ROWS
        |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Invalid input. '0' is not a valid value. Must be a positive integer.", InputPosition(40, 3, 22))
      )
    )
  }

  test("CALL IN TRANSACTIONS with batchSize -1") {
    val query =
      """CALL {
        |  CREATE ()
        |} IN TRANSACTIONS OF -1 ROWS
        |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Invalid input. '-1' is not a valid value. Must be a positive integer.", InputPosition(40, 3, 22))
      )
    )
  }

  test("CALL IN TRANSACTIONS with batchSize 1.5") {
    val query =
      """CALL {
        |  CREATE ()
        |} IN TRANSACTIONS OF 1.5 ROWS
        |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Invalid input. '1.5' is not a valid value. Must be a positive integer.",
          InputPosition(40, 3, 22)
        ),
        SemanticError("Type mismatch: expected Integer but was Float", InputPosition(40, 3, 22))
      )
    )
  }

  test("CALL IN TRANSACTIONS with batchSize 'foo'") {
    val query =
      """CALL {
        |  CREATE ()
        |} IN TRANSACTIONS OF 'foo' ROWS
        |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Invalid input. 'foo' is not a valid value. Must be a positive integer.",
          InputPosition(40, 3, 22)
        ),
        SemanticError("Type mismatch: expected Integer but was String", InputPosition(40, 3, 22))
      )
    )
  }

  test("CALL IN TRANSACTIONS with batchSize NULL") {
    val query =
      """CALL {
        |  CREATE ()
        |} IN TRANSACTIONS OF NULL ROWS
        |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Invalid input. 'NULL' is not a valid value. Must be a positive integer.",
          InputPosition(40, 3, 22)
        )
      )
    )
  }

  test("CALL IN TRANSACTIONS with batchSize larger than Long.Max") {
    val batchSize = Long.MaxValue.toString + "0"
    val query =
      s"""CALL {
         |  CREATE ()
         |} IN TRANSACTIONS OF $batchSize ROWS
         |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("integer is too large", InputPosition(40, 3, 22))
      )
    )
  }

  test("CALL IN TRANSACTIONS with batchSize with a variable reference") {
    val query =
      s"""WITH 1 AS b
         |CALL {
         |  CREATE ()
         |} IN TRANSACTIONS OF b ROWS
         |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("It is not allowed to refer to variables in OF ... ROWS", InputPosition(52, 4, 22))
      )
    )
  }

  test("CALL IN TRANSACTIONS with batchSize with a PatternComprehension") {
    val query =
      s"""CALL {
         |  CREATE ()
         |} IN TRANSACTIONS OF [path IN ()--() | 5] ROWS
         |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError("It is not allowed to refer to variables in OF ... ROWS", InputPosition(40, 3, 22)),
        SemanticError("Type mismatch: expected Integer but was List<Integer>", InputPosition(40, 3, 22))
      )
    )
  }

  test("CALL IN TRANSACTIONS REPORT STATUS without outer RETURN should fail semantic check") {
    val query =
      """CALL {
        |  CREATE ()
        |} IN TRANSACTIONS REPORT STATUS AS status
        |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Query cannot conclude with CALL (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
          InputPosition(0, 1, 1)
        )
      ),
      pipelineWithCallInTxsEnhancements
    )
  }

  test("CALL IN TRANSACTIONS REPORT STATUS AS <v> should fail semantic check if <v> has already been scoped") {
    val query =
      """WITH {} AS v
        |CALL {
        |  CREATE ()
        |} IN TRANSACTIONS REPORT STATUS AS v RETURN v
        |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Variable `v` already declared",
          InputPosition(67, 4, 36)
        )
      ),
      pipelineWithCallInTxsEnhancements
    )
  }

  test("CALL IN TRANSACTIONS ON ERROR BREAK should pass semantic check") {
    val query =
      """CALL {
        |  RETURN 1 AS v
        |} IN TRANSACTIONS 
        |  ON ERROR BREAK 
        |  RETURN v
        |""".stripMargin
    expectNoErrorsFrom(query, pipelineWithCallInTxsEnhancements)
  }

  test("CALL IN TRANSACTIONS ON ERROR BREAK without inner and outer return should pass semantic check") {
    val query =
      """CALL {
        |  CREATE ()
        |} IN TRANSACTIONS 
        |  ON ERROR BREAK 
        |""".stripMargin
    expectNoErrorsFrom(query, pipelineWithCallInTxsEnhancements)
  }

  test("CALL IN TRANSACTIONS ON ERROR BREAK with inner return and no outer return should fail semantic check") {
    val query =
      """CALL {
        |  RETURN 1 AS v
        |} IN TRANSACTIONS 
        |  ON ERROR BREAK 
        |""".stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Query cannot conclude with CALL (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
          InputPosition(0, 1, 1)
        )
      ),
      pipelineWithCallInTxsEnhancements
    )
  }

  test("CALL IN TRANSACTIONS ON ERROR CONTINUE REPORT STATUS AS status should pass semantic check") {
    val query =
      """CALL {
        |  RETURN 1 AS v
        |} IN TRANSACTIONS 
        |  ON ERROR CONTINUE 
        |  REPORT STATUS AS status
        |  RETURN v, status
        |""".stripMargin
    expectNoErrorsFrom(query, pipelineWithCallInTxsEnhancements)
  }

  test("CALL IN TRANSACTIONS ON ERROR FAIL REPORT STATUS AS status should pass semantic check") {
    val query =
      """CALL {
        |  RETURN 1 AS v
        |} IN TRANSACTIONS 
        |  ON ERROR FAIL 
        |  REPORT STATUS AS status
        |  RETURN v, status
        |""".stripMargin
    expectNoErrorsFrom(query, pipelineWithCallInTxsEnhancements)
  }

  test("CALL IN TRANSACTIONS ON ERROR <behaviour> should be a disabled feature") {
    val query =
      """CALL {
        |  RETURN 1 AS v
        |} IN TRANSACTIONS 
        |  ON ERROR FAIL 
        |  RETURN v
        |""".stripMargin

    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL IN TRANSACTIONS does not support ON ERROR behaviour yet", InputPosition(44, 4, 3))
      )
    )
  }

  test("CALL IN TRANSACTIONS REPORT STATUS should be a disabled feature") {
    val query =
      """CALL {
        |  RETURN 1 AS v
        |} IN TRANSACTIONS 
        |  REPORT STATUS AS s
        |  RETURN v, s
        |""".stripMargin

    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL IN TRANSACTIONS does not support REPORT STATUS yet", InputPosition(44, 4, 3))
      )
    )
  }

  test("CALL IN TRANSACTIONS ON ERROR <behaviour> REPORT STATUS should be disabled features") {
    val query =
      """CALL {
        |  RETURN 1 AS v
        |} IN TRANSACTIONS 
        |  ON ERROR CONTINUE 
        |  REPORT STATUS AS status
        |  RETURN v, status
        |""".stripMargin

    expectErrorsFrom(
      query,
      Set(
        SemanticError("CALL IN TRANSACTIONS does not support ON ERROR behaviour yet", InputPosition(44, 4, 3)),
        SemanticError("CALL IN TRANSACTIONS does not support REPORT STATUS yet", InputPosition(65, 5, 3))
      )
    )
  }

  test("should not allow USE when semantic feature is not set") {
    val query = "USE g RETURN 1"
    expectErrorMessagesFrom(
      query,
      Set(
        "The `USE GRAPH` clause is not available in this implementation of Cypher due to lack of support for USE graph selector."
      ),
      pipelineWithMultiGraphs
    )
  }

  test("should allow USE when semantic feature is set") {
    val query = "USE g RETURN 1"
    expectNoErrorsFrom(query, pipelineWithUseGraphSelector)
  }

  test("Allow single identifier in USE") {
    val query = "USE x RETURN 1"
    expectNoErrorsFrom(query, pipelineWithUseGraphSelector)
  }

  test("Allow qualified identifier in USE") {
    val query = "USE x.y.z RETURN 1"
    expectNoErrorsFrom(query, pipelineWithUseGraphSelector)
  }

  test("Allow view invocation in USE") {
    val query = "USE v(g, w(k)) RETURN 1"
    expectNoErrorsFrom(query, pipelineWithUseGraphSelector)
  }

  test("Allow qualified view invocation in USE") {
    val query = "USE a.b.v(g, x.g, x.v(k)) RETURN 1"
    expectNoErrorsFrom(query, pipelineWithUseGraphSelector)
  }

  test("Do not allow arbitrary expressions in USE") {
    val invalidQueries = Seq(
      ("USE 1 RETURN 1", InputPosition(4, 1, 5)),
      ("USE 'a' RETURN 1", InputPosition(4, 1, 5)),
      ("USE [x] RETURN 1", InputPosition(4, 1, 5)),
      ("USE 1 + 2 RETURN 1", InputPosition(6, 1, 7))
    )

    invalidQueries.foreach {
      case (query, pos) =>
        expectErrorsFrom(query, Set(SemanticError("Invalid graph reference", pos)), pipelineWithUseGraphSelector)
    }
  }

  test("Disallow expressions in view invocations") {
    val query = "USE a.b.v(1, 1+2, 'x') RETURN 1"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Invalid graph reference", InputPosition(10, 1, 11)),
        SemanticError("Invalid graph reference", InputPosition(14, 1, 15)),
        SemanticError("Invalid graph reference", InputPosition(18, 1, 19))
      ),
      pipelineWithUseGraphSelector
    )
  }

  test("Allow expressions in view invocations (with feature flag)") {
    val query = "WITH 1 AS x USE v(2, 'x', x, x+3) RETURN 1"
    expectNoErrorsFrom(query, pipelineWithExpressionsInView)
  }

  test("Expressions in view invocations are checked (with feature flag)") {
    val query = "WITH 1 AS x USE v(2, 'x', y, x+3) RETURN 1"
    expectErrorsFrom(
      query,
      Set(SemanticError("Variable `y` not defined", InputPosition(26, 1, 27))),
      pipelineWithExpressionsInView
    )
  }

  // positive tests that we get the error message
  // "In a WITH/RETURN with DISTINCT or an aggregation, it is not possible to access variables declared before the WITH/RETURN"

  test("Should give helpful error when accessing illegal variable in ORDER BY after WITH DISTINCT") {
    val query = "MATCH (p) WITH DISTINCT p.email AS mail ORDER BY p.name RETURN mail AS mail"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "In a WITH/RETURN with DISTINCT or an aggregation, it is not possible to access variables declared before the WITH/RETURN: p",
          InputPosition(49, 1, 50)
        )
      )
    )
  }

  test("Should give helpful error when accessing illegal variable in ORDER BY after WITH with aggregation") {
    val query = "MATCH (p) WITH collect(p.email) AS mail ORDER BY p.name RETURN mail AS mail"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "In a WITH/RETURN with DISTINCT or an aggregation, it is not possible to access variables declared before the WITH/RETURN: p",
          InputPosition(49, 1, 50)
        )
      )
    )
  }

  test("Should give helpful error when accessing illegal variable in ORDER BY after RETURN DISTINCT") {
    val query = "MATCH (p) RETURN DISTINCT p.email AS mail ORDER BY p.name"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "In a WITH/RETURN with DISTINCT or an aggregation, it is not possible to access variables declared before the WITH/RETURN: p",
          InputPosition(51, 1, 52)
        )
      )
    )
  }

  test("Should give helpful error when accessing illegal variable in ORDER BY after RETURN with aggregation") {
    val query = "MATCH (p) RETURN collect(p.email) AS mail ORDER BY p.name"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "In a WITH/RETURN with DISTINCT or an aggregation, it is not possible to access variables declared before the WITH/RETURN: p",
          InputPosition(51, 1, 52)
        )
      )
    )
  }

  test("Should give helpful error when accessing illegal variable in WHERE after WITH DISTINCT") {
    val query = "MATCH (p) WITH DISTINCT p.email AS mail WHERE p.name IS NOT NULL RETURN mail AS mail"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "In a WITH/RETURN with DISTINCT or an aggregation, it is not possible to access variables declared before the WITH/RETURN: p",
          InputPosition(46, 1, 47)
        )
      )
    )
  }

  test("Should give helpful error when accessing illegal variable in WHERE after WITH with aggregation") {
    val query = "MATCH (p) WITH collect(p.email) AS mail WHERE p.name IS NOT NULL RETURN mail AS mail"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "In a WITH/RETURN with DISTINCT or an aggregation, it is not possible to access variables declared before the WITH/RETURN: p",
          InputPosition(46, 1, 47)
        )
      )
    )
  }

  // negative tests that we do not get this error message otherwise

  test("Should not invent helpful error when accessing undefined variable in ORDER BY after WITH DISTINCT") {
    val query = "MATCH (p) WITH DISTINCT p.email AS mail ORDER BY q.name RETURN mail AS mail"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Variable `q` not defined", InputPosition(49, 1, 50))
      )
    )
  }

  test("Should not invent helpful error when accessing undefined variable in ORDER BY after WITH with aggregation") {
    val query = "MATCH (p) WITH collect(p.email) AS mail ORDER BY q.name RETURN mail AS mail"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Variable `q` not defined", InputPosition(49, 1, 50))
      )
    )
  }

  test("Should not invent helpful error when accessing undefined variable in ORDER BY after RETURN DISTINCT") {
    val query = "MATCH (p) RETURN DISTINCT p.email AS mail ORDER BY q.name"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Variable `q` not defined", InputPosition(51, 1, 52))
      )
    )
  }

  test("Should not invent helpful error when accessing undefined variable in ORDER BY after RETURN with aggregation") {
    val query = "MATCH (p) RETURN collect(p.email) AS mail ORDER BY q.name"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Variable `q` not defined", InputPosition(51, 1, 52))
      )
    )
  }

  test("Should not invent helpful error when accessing undefined variable in WHERE after WITH DISTINCT") {
    val query = "MATCH (p) WITH DISTINCT p.email AS mail WHERE q.name IS NOT NULL RETURN mail AS mail"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Variable `q` not defined", InputPosition(46, 1, 47))
      )
    )
  }

  test("Should not invent helpful error when accessing undefined variable in WHERE after WITH with aggregation") {
    val query = "MATCH (p) WITH collect(p.email) AS mail WHERE q.name IS NOT NULL RETURN mail AS mail"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Variable `q` not defined", InputPosition(46, 1, 47))
      )
    )
  }

  // Empty tokens for node property

  test("Should not allow empty node property key name in CREATE clause") {
    val query = "CREATE ({prop: 5, ``: 1})"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(18, 1, 19))
      )
    )
  }

  test("Should not allow empty node property key name in MERGE clause") {
    val query = "MERGE (n {``: 1})"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(10, 1, 11))
      )
    )
  }

  test("Should not allow empty node property key name in ON CREATE SET") {
    val query = "MERGE (n :Label) ON CREATE SET n.`` = 1"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(33, 1, 34))
      )
    )
  }

  test("Should not allow empty node property key name in ON MATCH SET") {
    val query = "MERGE (n :Label) ON MATCH SET n.`` = 1"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(32, 1, 33))
      )
    )
  }

  test("Should not allow empty node property key name in MATCH clause") {
    val query = "MATCH (n {``: 1}) RETURN n AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(10, 1, 11))
      )
    )
  }

  test("Should not allow empty node property key name in SET clause") {
    val query = "MATCH (n) SET n.``= 1"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(16, 1, 17))
      )
    )
  }

  test("Should not allow empty node property key name in REMOVE clause") {
    val query = "MATCH (n) REMOVE n.``"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(19, 1, 20))
      )
    )
  }

  test("Should not allow empty node property key name in WHERE clause") {
    val query = "MATCH (n) WHERE n.``= 1 RETURN n AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(18, 1, 19))
      )
    )
  }

  test("Should not allow empty node property key name in WITH clause") {
    val query = "MATCH (n) WITH n.`` AS prop RETURN prop AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(17, 1, 18))
      )
    )
  }

  test("Should not allow empty node property key name in ORDER BY in WITH") {
    val query = "MATCH (n) WITH n AS invalid ORDER BY n.`` RETURN count(*) AS count"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(39, 1, 40))
      )
    )
  }

  test("Should not allow empty node property key name in RETURN clause") {
    val query = "MATCH (n) RETURN n.`` AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(19, 1, 20))
      )
    )
  }

  test("Should not allow empty node property key name in DISTINCT RETURN clause") {
    val query = "MATCH (n) RETURN DISTINCT n.`` AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(28, 1, 29))
      )
    )
  }

  test("Should not allow empty node property key name in aggregation in RETURN clause") {
    val query = "MATCH (n) RETURN count(n.``) AS count"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(25, 1, 26))
      )
    )
  }

  test("Should not allow empty node property key name in ORDER BY in RETURN") {
    val query = "MATCH (n) RETURN n AS invalid ORDER BY n.`` DESC LIMIT 2"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(41, 1, 42))
      )
    )
  }

  test("Should not allow empty node property key name in CASE clause") {
    val query =
      """
        |MATCH (n)
        |RETURN
        |CASE n.``
        |WHEN 'val'
        |THEN 1
        |ELSE 2 END AS result
      """.stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(25, 4, 8))
      )
    )
  }

  test("Should not allow empty node property key name in CASE WHEN clause") {
    val query =
      """
        |MATCH (n)
        |RETURN
        |CASE
        |WHEN n.`` = 'blue'
        |THEN 1
        |ELSE 2 END AS result
      """.stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(30, 5, 8))
      )
    )
  }

  test("Should not allow empty node property key name in CASE THEN clause") {
    val query =
      """
        |MATCH (n)
        |RETURN
        |CASE
        |WHEN n.prop = 'blue'
        |THEN n.``
        |ELSE 2 END AS result
      """.stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(51, 6, 8))
      )
    )
  }

  test("Should not allow empty node property key name in CASE ELSE clause") {
    val query =
      """
        |MATCH (n)
        |RETURN
        |CASE
        |WHEN n.prop = 'blue'
        |THEN 1
        |ELSE n.`` END AS result
      """.stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(58, 7, 8))
      )
    )
  }

  // Empty tokens for relationship properties

  test("Should not allow empty relationship property key name in CREATE clause") {
    val query = "CREATE ()-[:REL {``: 1}]->()"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(17, 1, 18))
      )
    )
  }

  test("Should not allow empty relationship property key name in MERGE clause") {
    val query = "MERGE ()-[r :REL {``: 1, prop: 42}]->()"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(18, 1, 19))
      )
    )
  }

  test("Should not allow empty relationship property key name in ON CREATE SET") {
    val query = "MERGE ()-[r:REL]->() ON CREATE SET r.`` = 1"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(37, 1, 38))
      )
    )
  }

  test("Should not allow empty relationship property key name in ON MATCH SET") {
    val query = "MERGE ()-[r:REL]->() ON MATCH SET r.`` = 1"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(36, 1, 37))
      )
    )
  }

  test("Should not allow empty relationship property key name in MATCH clause") {
    val query = "MATCH ()-[r {prop:1337, ``: 1}]->() RETURN r AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(24, 1, 25))
      )
    )
  }

  test("Should not allow empty relationship property key name in SET clause") {
    val query = "MATCH ()-[r]->() SET r.``= 1 RETURN r AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(23, 1, 24))
      )
    )
  }

  test("Should not allow empty relationship property key name in REMOVE clause") {
    val query = "MATCH ()-[r]->() REMOVE r.``"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(26, 1, 27))
      )
    )
  }

  test("Should not allow empty relationship property key name in WHERE clause") {
    val query = "MATCH (n)-[r]->() WHERE n.prop > r.`` RETURN n AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(35, 1, 36))
      )
    )
  }

  test("Should not allow empty relationship property key name in WITH clause") {
    val query = "MATCH ()-[r]->() WITH r.`` AS prop, r.prop as prop2 RETURN count(*) AS count"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(24, 1, 25))
      )
    )
  }

  test("Should not allow empty relationship property key name in ORDER BY in WITH") {
    val query = "MATCH ()-[r]->() WITH r AS invalid ORDER BY r.`` RETURN count(*) AS count"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(46, 1, 47))
      )
    )
  }

  test("Should not allow empty relationship property key name in RETURN clause") {
    val query = "MATCH ()-[r]->() RETURN r.`` as result"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(26, 1, 27))
      )
    )
  }

  test("Should not allow empty relationship property key name in DISTINCT RETURN clause") {
    val query = "MATCH ()-[r]->() RETURN DISTINCT r.`` as result"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(35, 1, 36))
      )
    )
  }

  test("Should not allow empty relationship property key name in aggregation in RETURN clause") {
    val query = "MATCH ()-[r]->() RETURN max(r.``) AS max"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(30, 1, 31))
      )
    )
  }

  test("Should not allow empty relationship property key name in ORDER BY in RETURN") {
    val query = "MATCH ()-[r]->() RETURN r AS result ORDER BY r.``"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(47, 1, 48))
      )
    )
  }

  test("Should not allow empty relationship property key name in CASE clause") {
    val query =
      """
        |MATCH ()-[r]->()
        |RETURN
        |CASE r.``
        |WHEN 'val'
        |THEN 1
        |ELSE 2 END AS result
      """.stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(32, 4, 8))
      )
    )
  }

  test("Should not allow empty relationship property key name in CASE WHEN clause") {
    val query =
      """
        |MATCH ()-[r]->()
        |RETURN
        |CASE
        |WHEN r.`` = 'blue'
        |THEN 1
        |ELSE 2 END AS result
      """.stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(37, 5, 8))
      )
    )
  }

  test("Should not allow empty relationship property key name in CASE THEN clause") {
    val query =
      """
        |MATCH ()-[r]->()
        |RETURN
        |CASE
        |WHEN r.prop = 'blue'
        |THEN r.``
        |ELSE 2 END AS result
      """.stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(58, 6, 8))
      )
    )
  }

  test("Should not allow empty relationship property key name in CASE ELSE clause") {
    val query =
      """
        |MATCH ()-[r]->()
        |RETURN
        |CASE
        |WHEN r.prop = 'blue'
        |THEN 1
        |ELSE r.`` END AS result
      """.stripMargin
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(65, 7, 8))
      )
    )
  }

  // Empty tokens for labels

  test("Should not allow empty label in CREATE clause") {
    val query = "CREATE (:Valid:``)"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(14, 1, 15))
      )
    )
  }

  test("Should not allow empty label in MERGE clause") {
    val query = "MERGE (n:``)"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(9, 1, 10))
      )
    )
  }

  test("Should not allow empty label in MATCH clause") {
    val query = "MATCH (n:``:Valid) RETURN n AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(11, 1, 12))
      )
    )
  }

  test("Should not allow empty label in label expression") {
    val query = "MATCH (n:``&Valid) RETURN n AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(11, 1, 12))
      )
    )
  }

  test("should not allow empty label name in label expression predicate") {
    val query = "MATCH (n) WHERE n:A&`` RETURN *"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(19, 1, 20))
      )
    )
  }

  test("should not allow empty label name in label expression with legacy symbols") {
    val query = "MATCH (n) WHERE n:A:`` RETURN *"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(19, 1, 20))
      )
    )
  }

  test("Should not allow empty label in SET clause") {
    val query = "MATCH (n) SET n:``"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(14, 1, 15))
      )
    )
  }

  test("Should not allow empty label in REMOVE clause") {
    val query = "MATCH (n) REMOVE n:``"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(17, 1, 18))
      )
    )
  }

  // Empty tokens for relationship type

  test("Should not allow empty relationship type in CREATE clause") {
    val query = "CREATE ()-[:``]->()"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(12, 1, 13))
      )
    )
  }

  test("Should not allow empty relationship type in MERGE clause") {
    val query = "MERGE ()-[r :``]->()"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(13, 1, 14))
      )
    )
  }

  test("Should not allow empty relationship type in MATCH clause") {
    val query = "MATCH ()-[r :``]->() RETURN r AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(13, 1, 14))
      )
    )
  }

  test("Should not allow empty relationship type in variable length pattern") {
    val query = "MATCH ()-[r :``*1..5]->() RETURN r AS invalid"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(emptyTokenErrorMessage, InputPosition(13, 1, 14))
      )
    )
  }

  test("Should not allow to use aggregate functions inside aggregate functions") {
    val query = "WITH 1 AS x RETURN sum(max(x)) AS sumOfMax"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Can't use aggregate functions inside of aggregate functions.", InputPosition(23, 1, 24))
      )
    )
  }

  test("Should not allow to use count(*) inside aggregate functions") {
    val query = "WITH 1 AS x RETURN min(count(*)) AS minOfCount"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Can't use aggregate functions inside of aggregate functions.", InputPosition(23, 1, 24))
      )
    )
  }

  test("Should not allow repeating rel variable in pattern") {
    val query = "MATCH ()-[r]-()-[r]-() RETURN r AS r"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Cannot use the same relationship variable 'r' for multiple relationships",
          InputPosition(10, 1, 11)
        )
      )
    )
  }

  test("Should not allow repeated rel variable in pattern expression") {
    val query = normalizeNewLines("MATCH ()-[r]-() RETURN size( ()-[r]-()-[r]-() ) AS size")
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Cannot use the same relationship variable 'r' for multiple relationships",
          InputPosition(33, 1, 34)
        )
      )
    )
  }

  test("Should not allow repeated rel variable in pattern comprehension") {
    val query = "MATCH ()-[r]-() RETURN [ ()-[r]-()-[r]-() | r ] AS rs"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Cannot use the same relationship variable 'r' for multiple relationships",
          InputPosition(29, 1, 30)
        )
      )
    )
  }

  test("Should type check predicates in FilteringExpression") {
    val queries = Seq(
      ("RETURN [x IN [1,2,3] WHERE 42 | x + 1] AS foo", InputPosition(27, 1, 28)),
      ("RETURN all(x IN [1,2,3] WHERE 42) AS foo", InputPosition(30, 1, 31)),
      ("RETURN any(x IN [1,2,3] WHERE 42) AS foo", InputPosition(30, 1, 31)),
      ("RETURN none(x IN [1,2,3] WHERE 42) AS foo", InputPosition(31, 1, 32)),
      ("RETURN single(x IN [1,2,3] WHERE 42) AS foo", InputPosition(33, 1, 34))
    )
    queries.foreach {
      case (query, pos) =>
        expectErrorsFrom(query, Set(SemanticError("Type mismatch: expected Boolean but was Integer", pos)))
    }
  }

  test("Returning a variable that is already bound outside should give a useful error") {
    val query =
      """WITH 1 AS i
        |CALL {
        |  WITH 2 AS i
        |  RETURN i
        |}
        |RETURN i
        |""".stripMargin

    val result = runSemanticAnalysis(query)

    result.errors.map(e => (e.msg, e.position.line, e.position.column)) should equal(List(
      ("Variable `i` already declared in outer scope", 4, 10)
    ))
  }

  test("Returning a variable that is already bound outside, from a union, should give a useful error") {
    val query =
      """WITH 1 AS i
        |CALL {
        |  WITH 2 AS i
        |  RETURN i
        |    UNION
        |  WITH 3 AS i
        |  RETURN 2 AS i
        |}
        |RETURN i
        |""".stripMargin

    val result = runSemanticAnalysis(query)

    result.errors.map(e => (e.msg, e.position.line, e.position.column)) should equal(List(
      ("Variable `i` already declared in outer scope", 4, 10),
      ("Variable `i` already declared in outer scope", 7, 15)
    ))
  }

  test("Returning a variable implicitly that is already bound outside should give a useful error") {
    val query =
      """WITH 1 AS i
        |CALL {
        |  WITH 2 AS i
        |  RETURN *
        |}
        |RETURN i
        |""".stripMargin

    val result = runSemanticAnalysis(query)

    result.errors.map(e => (e.msg, e.position.line)) should equal(List(
      ("Variable `i` already declared in outer scope", 4)
    ))
  }

  test("Returning a variable implicitly that is already bound outside, from a union, should give a useful error") {
    val query =
      """WITH 1 AS i
        |CALL {
        |  WITH 2 AS i
        |  RETURN *
        |    UNION
        |  WITH 3 AS i
        |  RETURN *
        |}
        |RETURN i
        |""".stripMargin

    val result = runSemanticAnalysis(query)

    result.errors.map(e => (e.msg, e.position.line)) should equal(List(
      ("Variable `i` already declared in outer scope", 4),
      ("Variable `i` already declared in outer scope", 7)
    ))
  }

  test("Should warn about variable shadowing in a subquery") {
    val query =
      """MATCH (shadowed)
        |CALL {
        |  MATCH (shadowed)-[:REL]->(m) // warning here
        |  RETURN m
        |}
        |RETURN *""".stripMargin
    expectNotificationsFrom(query, Set(SubqueryVariableShadowing(InputPosition(33, 3, 10), "shadowed")))
  }

  test("Should warn about variable shadowing in a subquery when aliasing") {
    val query =
      """MATCH (shadowed)
        |CALL {
        |  MATCH (n)-[:REL]->(m)
        |  WITH m AS shadowed // warning here
        |  WITH shadowed AS m
        |  RETURN m
        |}
        |RETURN *""".stripMargin
    expectNotificationsFrom(query, Set(SubqueryVariableShadowing(InputPosition(60, 4, 13), "shadowed")))
  }

  test("Should warn about variable shadowing in a nested subquery") {
    val query =
      """MATCH (shadowed)
        |CALL {
        |  MATCH (n)-[:REL]->(m)
        |  CALL {
        |    MATCH (shadowed)-[:REL]->(x) // warning here
        |    RETURN x
        |  }
        |  RETURN m, x
        |}
        |RETURN *""".stripMargin
    expectNotificationsFrom(query, Set(SubqueryVariableShadowing(InputPosition(68, 5, 12), "shadowed")))
  }

  test("Should warn about variable shadowing from enclosing subquery") {
    val query =
      """MATCH (shadowed)
        |CALL {
        |  WITH shadowed
        |  MATCH (shadowed)-[:REL]->(m)
        |  CALL {
        |    MATCH (shadowed)-[:REL]->(x) // warning here
        |    RETURN x
        |  }
        |  RETURN m, x
        |}
        |RETURN *""".stripMargin
    expectNotificationsFrom(query, Set(SubqueryVariableShadowing(InputPosition(91, 6, 12), "shadowed")))
  }

  test("Should warn about multiple shadowed variables in a subquery") {
    val query =
      """MATCH (shadowed)-->(alsoShadowed)
        |CALL {
        |  MATCH (shadowed)-->(alsoShadowed) // multiple warnings here
        |  RETURN shadowed AS n, alsoShadowed AS m
        |}
        |RETURN *""".stripMargin
    expectNotificationsFrom(
      query,
      Set(
        SubqueryVariableShadowing(InputPosition(50, 3, 10), "shadowed"),
        SubqueryVariableShadowing(InputPosition(63, 3, 23), "alsoShadowed")
      )
    )
  }

  test("Should warn about multiple shadowed variables in a nested subquery") {
    val query =
      """MATCH (shadowed)
        |CALL {
        |  MATCH (shadowed)-[:REL]->(m) // warning here
        |  CALL {
        |    MATCH (shadowed)-[:REL]->(x) // and also here
        |    RETURN x
        |  }
        |  RETURN m, x
        |}
        |RETURN *""".stripMargin
    expectNotificationsFrom(
      query,
      Set(
        SubqueryVariableShadowing(InputPosition(33, 3, 10), "shadowed"),
        SubqueryVariableShadowing(InputPosition(91, 5, 12), "shadowed")
      )
    )
  }

  test("Should not warn about variable shadowing in a subquery if it has been removed from scope by WITH") {
    val query =
      """MATCH (notShadowed)
        |WITH notShadowed AS n
        |CALL {
        |  MATCH (notShadowed)-[:REL]->(m)
        |  RETURN m
        |}
        |RETURN *""".stripMargin
    expectNotificationsFrom(query, Set.empty)
  }

  test("Should not warn about variable shadowing in a subquery if it has been imported previously") {
    val query =
      """MATCH (notShadowed)
        |CALL {
        |  WITH notShadowed
        |  MATCH (notShadowed)-[:REL]->(m)
        |  WITH m AS notShadowed
        |  RETURN notShadowed AS x
        |}
        |RETURN *""".stripMargin
    expectNotificationsFrom(query, Set.empty)
  }

  test("Should warn about variable shadowing in an union subquery") {
    val query =
      """MATCH (shadowed)
        |CALL {
        |  MATCH (m) RETURN m
        | UNION
        |  MATCH (shadowed)-[:REL]->(m) // warning here
        |  RETURN m
        |}
        |RETURN *""".stripMargin
    expectNotificationsFrom(query, Set(SubqueryVariableShadowing(InputPosition(61, 5, 10), "shadowed")))
  }

  test("Should warn about variable shadowing in one of the union subquery branches") {
    val query =
      """MATCH (shadowed)
        |CALL {
        |  WITH shadowed
        |  MATCH (shadowed)-[:REL]->(m)
        |  RETURN m
        | UNION
        |  MATCH (shadowed)-[:REL]->(m) // warning here
        |  RETURN m
        | UNION
        |  MATCH (x) RETURN x AS m
        |}
        |RETURN *""".stripMargin
    expectNotificationsFrom(query, Set(SubqueryVariableShadowing(InputPosition(98, 7, 10), "shadowed")))
  }

  test("Should disallow introducing variables in pattern expressions") {
    val query = "MATCH (x) WHERE (x)-[r]-(y) RETURN x"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("PatternExpressions are not allowed to introduce new variables: 'r'.", InputPosition(21, 1, 22)),
        SemanticError("PatternExpressions are not allowed to introduce new variables: 'y'.", InputPosition(25, 1, 26))
      )
    )
  }

  test("Skip with PatternComprehension should complain") {
    val query = "RETURN 1 SKIP size([(a)-->(b) | a.prop])"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("It is not allowed to refer to variables in SKIP", InputPosition(14, 1, 15))
      )
    )
  }

  test("Skip with PatternExpression should complain") {
    val query = "RETURN 1 SKIP size(()-->())"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("It is not allowed to refer to variables in SKIP", InputPosition(14, 1, 15))
      )
    )
  }

  test("Limit with PatternComprehension should complain") {
    val query = "RETURN 1 LIMIT size([(a)-->(b) | a.prop])"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("It is not allowed to refer to variables in LIMIT", InputPosition(15, 1, 16))
      )
    )
  }

  test("Limit with PatternExpression should complain") {
    val query = "RETURN 1 LIMIT size(()-->())"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("It is not allowed to refer to variables in LIMIT", InputPosition(15, 1, 16))
      )
    )
  }

  test("UNION with incomplete first part") {
    val query = "MATCH (a) WITH a UNION MATCH (a) RETURN a"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Query cannot conclude with WITH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
          InputPosition(10, 1, 11)
        ),
        SemanticError(
          "All sub queries in an UNION must have the same return column names",
          InputPosition(17, 1, 18)
        )
      )
    )
  }

  test("UNION with missing return in first part") {
    val query = "CALL db.labels() YIELD label UNION CALL db.labels() YIELD label RETURN label"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "All sub queries in an UNION must have the same return column names",
          InputPosition(29, 1, 30)
        )
      )
    )
  }

  test("UNION with missing return in second part") {
    val query = "CALL db.labels() YIELD label RETURN label UNION CALL db.labels() YIELD label"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "All sub queries in an UNION must have the same return column names",
          InputPosition(42, 1, 43)
        )
      )
    )
  }

  test("UNION with incomplete second part") {
    val query = "MATCH (a) RETURN a UNION MATCH (a) WITH a"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Query cannot conclude with WITH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
          InputPosition(35, 1, 36)
        ),
        SemanticError(
          "All sub queries in an UNION must have the same return column names",
          InputPosition(19, 1, 20)
        )
      )
    )
  }

  test("Query ending in CALL ... YIELD ...") {
    val query = "MATCH (a) CALL proc.foo() YIELD bar"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Query cannot conclude with CALL together with YIELD", InputPosition(10, 1, 11))
      )
    )
  }

  test("Query with only importing WITH") {
    val query = "WITH a"
    expectErrorsFrom(
      query,
      Set(
        SemanticError("Variable `a` not defined", InputPosition(5, 1, 6)),
        SemanticError(
          "Query cannot conclude with WITH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
          InputPosition(0, 1, 1)
        )
      )
    )
  }

  test("Subquery with only importing WITH") {
    val query = "WITH 1 AS a CALL { WITH a } RETURN a"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Query must conclude with a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD",
          InputPosition(19, 1, 20)
        )
      )
    )
  }

  test("Subquery with only USE") {
    val query = "WITH 1 AS a CALL { USE x } RETURN a"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Query must conclude with a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD",
          InputPosition(19, 1, 20)
        )
      ),
      pipelineWithUseGraphSelector
    )
  }

  test("Subquery with only USE and importing WITH") {
    val query = "WITH 1 AS a CALL { USE x WITH a } RETURN a"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Query must conclude with a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD",
          InputPosition(19, 1, 20)
        )
      ),
      pipelineWithUseGraphSelector
    )
  }

  test("Subquery with only MATCH") {
    val query = "WITH 1 AS a CALL { MATCH (n) } RETURN a"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Query cannot conclude with MATCH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
          InputPosition(19, 1, 20)
        )
      )
    )
  }

  test("Relationship Pattern predicates should be enabled by default") {
    val query = "MATCH ()-[r:Rel WHERE r.prop > 42]->() return *"
    expectNoErrorsFrom(query)
  }

  test("Relationship Pattern predicates should not be allowed with quantification") {
    val query = "MATCH ()-[r:Rel* WHERE r.prop > 42]->() return *"
    expectErrorsFrom(
      query,
      Set(
        SemanticError(
          "Relationship pattern predicates are not supported for variable-length relationships.",
          InputPosition(30, 1, 31)
        )
      )
    )
  }

  test("subquery without RETURN should not declare variables from YIELD in the outer scope") {
    val query =
      """CALL {
        |  CALL dbms.procedures() YIELD name
        |}
        |RETURN name
        |""".stripMargin

    expectErrorMessagesFrom(
      query,
      Set("Variable `name` not defined")
    )
  }

  test("should fail for size(COUNT{...})") {
    val query = "RETURN size(COUNT{ (n) }) AS foo"
    expectErrorsFrom(
      query,
      Set(SemanticError("Type mismatch: expected String or List<T> but was Integer", InputPosition(12, 1, 13)))
    )
  }

  // ------- Helpers ------------------------------

  private def expectNoErrorsFrom(
    query: String,
    pipeline: Transformer[BaseContext, BaseState, BaseState] = pipelineWithSemanticFeatures()
  ): Unit =
    runSemanticAnalysisWithPipeline(pipeline, query).errors shouldBe empty

  private def expectErrorsFrom(
    query: String,
    expectedErrors: Set[SemanticError],
    pipeline: Transformer[BaseContext, BaseState, BaseState] = pipelineWithSemanticFeatures()
  ): Unit =
    runSemanticAnalysisWithPipeline(pipeline, query).errors.toSet shouldEqual expectedErrors

  private def expectErrorMessagesFrom(
    query: String,
    expectedErrors: Set[String],
    pipeline: Transformer[BaseContext, BaseState, BaseState] = pipelineWithSemanticFeatures()
  ): Unit =
    runSemanticAnalysisWithPipeline(pipeline, query).errorMessages.toSet shouldEqual expectedErrors

  private def expectNotificationsFrom(
    query: String,
    expectedNotifications: Set[InternalNotification],
    pipeline: Transformer[BaseContext, BaseState, BaseState] = pipelineWithSemanticFeatures()
  ): Unit = {
    val normalisedQuery = normalizeNewLines(query)
    val result = runSemanticAnalysisWithPipeline(pipeline, normalisedQuery)
    result.state.semantics().notifications shouldEqual expectedNotifications
    result.errors shouldBe empty
  }

  final case object ProjectNamedPathsPhase extends Phase[BaseContext, BaseState, BaseState] {
    override def phase: CompilationPhaseTracer.CompilationPhase = AST_REWRITE

    override def process(from: BaseState, context: BaseContext): BaseState = {
      from.withStatement(from.statement().endoRewrite(projectNamedPaths))
    }
    override def postConditions: Set[StepSequencer.Condition] = Set.empty
  }
}
