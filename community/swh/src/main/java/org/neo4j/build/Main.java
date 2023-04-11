package org.neo4j.build;

import org.neo4j.property.SwhPropertyProvider;
import org.neo4j.webGraphExecutor.WebGraphExecutor;
import org.softwareheritage.graph.SwhBidirectionalGraph;

import java.io.IOException;

/**
 * Main class to run Cypher queries on Software Heritage's graphs.
 * Usage: Main [path] [query] [optional: path, where to store logs]
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args == null || args.length < 2 || args[0] == null || args[1] == null) {
            System.out.println(
                    "Usage: Main [path] [query] [optional: path, where to store logs]");
            return;
        }

        String path = args[0];
        String query = args[1];
        String logsPath = "logs";
        if (args.length == 3) {
            logsPath = args[2];
        }

        WebGraphExecutor executor = new WebGraphExecutor();
        SwhBidirectionalGraph swhGraph =
                SwhBidirectionalGraph.loadLabelled(path);
        SwhPropertyProvider propertyProvider = new SwhPropertyProvider(swhGraph);

        executor.initialise(swhGraph, propertyProvider, logsPath);

        String result = executor.executeQuery(query);
        System.out.println(result);
    }
}
