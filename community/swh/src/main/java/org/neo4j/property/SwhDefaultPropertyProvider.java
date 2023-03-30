package org.neo4j.property;

import org.neo4j.webgraph.property.DefaultPropertyProvider;
import org.neo4j.webgraph.property.WebGraphPropertyProvider;
import org.neo4j.webgraph.property.entities.node.FileProperty;
import org.neo4j.webgraph.property.entities.node.NodeProperty;
import org.neo4j.webgraph.property.entities.relationship.ArcLabelRelationshipProperty;
import org.neo4j.webgraph.property.entities.relationship.ArcLabelRelationshipSubProperty;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.labels.DirEntry;

import java.io.IOException;

public class SwhDefaultPropertyProvider {
    public static DefaultPropertyProvider getDefaultPropertyProvider(SwhBidirectionalGraph graph) throws IOException {
        String path = graph.getPath();
        DefaultPropertyProvider provider = new DefaultPropertyProvider();
        String[] labels = new String[]{"CNT", "DIR", "ORI", "REL", "REV", "SNP"};
        provider.addNodeLabel(id -> graph.getNodeType(id).toString(), labels);
        provider.addNodeProperty(new FileProperty<>("author_timestamp",
                path + ".property.author_timestamp.bin", Long.class));
        provider.addNodeProperty(new NodeProperty<>("SWHID", graph::getSWHID));
        provider.addNodeProperty(new NodeProperty<>("SWHType", graph::getNodeType));
        return provider;
    }

    public static WebGraphPropertyProvider getRelationshipLabelPropertyProvider(SwhBidirectionalGraph graph) throws IOException {
        graph.loadLabelNames();
        DefaultPropertyProvider provider = getDefaultPropertyProvider(graph);
        ArcLabelRelationshipProperty<DirEntry[]> edgeProperty = new ArcLabelRelationshipProperty<>("__arc_label_property__",
                graph.getForwardGraph().underlyingLabelledGraph());
        provider.addRelationshipProperty(edgeProperty);
        provider.addRelationshipProperty(
                new ArcLabelRelationshipSubProperty<Object>("dir_entry_str", edgeProperty,
                        dirEntries -> dirEntryStr(dirEntries, graph)));
        provider.addRelationshipProperty(
                new ArcLabelRelationshipSubProperty<Object>("filenames", edgeProperty, dirEntries -> filenames(dirEntries, graph)));
        return provider;
    }

    private static DirEntryString[] dirEntryStr(DirEntry[] dirEntries, SwhBidirectionalGraph graph) {
        if (dirEntries.length == 0) {
            return null;
        }
        DirEntryString[] res = new DirEntryString[dirEntries.length];
        for (int i = 0; i < dirEntries.length; i++) {
            res[i] = new DirEntryString(getFilename(dirEntries[i], graph), dirEntries[i].permission);
        }
        return res;
    }

    private static String[] filenames(DirEntry[] dirEntries, SwhBidirectionalGraph graph) {
        if (dirEntries.length == 0) {
            return null;
        }
        String[] res = new String[dirEntries.length];
        for (int i = 0; i < dirEntries.length; i++) {
            res[i] = getFilename(dirEntries[i], graph);
        }
        return res;
    }

    private static String getFilename(DirEntry dirEntry, SwhBidirectionalGraph graph) {
        return new String(graph.getLabelName(dirEntry.filenameId));
    }

    public static class DirEntryString {

        public String filename;
        public int permission;

        public DirEntryString(String filename, int permission) {
            this.filename = filename;
            this.permission = permission;
        }
    }
}