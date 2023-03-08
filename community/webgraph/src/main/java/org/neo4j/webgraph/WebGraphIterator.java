package org.neo4j.webgraph;

import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.ResourceIterator;

import java.util.Iterator;

public class WebGraphIterator<E> implements ResourceIterable<E> {

    private final Iterator<E> iterator;

    public WebGraphIterator (Iterator<E> iterator) {
        this.iterator = iterator;
    }

    @Override
    public void close() {
    }

    @Override
    public ResourceIterator<E> iterator() {
        return new ResourceIterator<>() {
            @Override
            public void close() {
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public E next() {
                return iterator.next();
            }
        };
    }
}
