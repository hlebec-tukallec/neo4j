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
package org.neo4j.webgraph.property;

import java.util.Map;

/**
 * WebGraphPropertyProvider defines methods to work with properties and labels of nodes/relationships
 * to be provided into Neo4j.
 * In Neo4j implementation any property key/label has an id associated with it.
 */
public interface WebGraphPropertyProvider {

    /**
     * Returns node property key name associated with the given property id.
     * @param id the unique identifier of property key
     * @return the property key
     */
    String getNodePropertyNameById(int id);

    /**
     * Returns node property key id associated with the given property key.
     * @param key the property key
     * @return the unique identifier of property key
     */
    int getNodePropertyIdByName(String key);

    /**
     * Returns relationship property key name associated with the given property id.
     * @param id the unique identifier of property key
     * @return the property key
     */
    String getRelationshipPropertyNameById(int id);

    /**
     * Returns relationship property key id associated with the given property key.
     * @param key the property key
     * @return the unique identifier of property key
     */
    int getRelationshipPropertyIdByName(String key);

    /**
     * Returns node label key name associated with the given label id.
     * @param id the unique identifier of label key
     * @return the label key
     */
    String getNodeLabelNameById(int id);

    /**
     * Returns node label key id associated with the given label key.
     * @param key the label key
     * @return the unique identifier of label key
     */
    int getNodeLabelIdByName(String key);


    /**
     * Returns all keys of the properties associated with this node.
     *
     * @param nodeId the id of the node
     * @return the list of all property keys
     * */
    String[] getNodePropertyKeys(long nodeId);

    /**
     * Returns a value of the property associated with the given node by key.
     *
     * @param key the key of the property
     * @param nodeId the id of the node
     * @return the property value or null if the value does not exist
     */
    Object getNodeProperty(String key, long nodeId);

    /**
     * Returns all properties associated with the given node in format: {key, value}.
     *
     * @param nodeId the id of the given node
     * @return all properties' keys and values associated with the given node
     */
    Map<String, Object> getAllNodeProperties(long nodeId);

    /**
     * Returns true if the given node has a value associated with the key.
     *
     * @param key the key of the property
     * @param nodeId the id of the node
     * @return true if the property value is present, otherwise - false
     */
    boolean hasNodeProperty(String key, long nodeId);

    /**
     * Returns a label associated with the given node.
     * A node can have only one label.
     *
     * @param nodeId the id of the node
     * @return label associated with the node
     */
    String getNodeLabel(long nodeId);

    /**
     * Returns true if the given node has a label.
     *
     * @param nodeId the id of the node
     * @param label associated with the node
     * @return true if the node has the label, false - otherwise
     */
    boolean hasNodeLabel(long nodeId, String label);

    /**
     * Returns all keys of the properties associated with this relationship.
     *
     * @param startNode the id of the start node of the relationship
     * @param endNode the id of the end node of the relationship
     * @return the list of all property keys
     * */
    String[] getRelationshipPropertyKeys(long startNode, long endNode);

    /**
     * Returns a value of the property associated with the given relationship by key.
     *
     * @param key the key of the property
     * @param startNode the id of the start node of the relationship
     * @param endNode the id of the end node of the relationship
     * @return the property value or null if the value does not exist
     */
    Object getRelationshipProperty(String key, long startNode, long endNode);

    /**
     * Returns all properties associated with the given relationship in format: {key, value}.
     *
     * @param startNode the id of the start node of the relationship
     * @param endNode the id of the end node of the relationship
     * @return all properties' keys and values associated with the given relationship
     */
    Map<String, Object> getAllRelationshipProperties(long startNode, long endNode);

    /**
     * Returns true if the given relationship has a value associated with the key.
     *
     * @param key the key of the property
     * @param startNode the id of the start node of the relationship
     * @param endNode the id of the end node of the relationship
     * @return true if the property value is present, otherwise - false
     */
    boolean hasRelationshipProperty(String key, long startNode, long endNode);
}
