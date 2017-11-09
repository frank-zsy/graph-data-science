/**
 * Copyright (c) 2017 "Neo4j, Inc." <http://neo4j.com>
 *
 * This file is part of Neo4j Graph Algorithms <http://github.com/neo4j-contrib/neo4j-graph-algorithms>.
 *
 * Neo4j Graph Algorithms is free software: you can redistribute it and/or modify
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
package org.neo4j.graphalgo.core.neo4jview;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.neo4j.graphalgo.SimpleGraphSetup;
import org.neo4j.graphalgo.SimpleGraphTestCase;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

/**
 * @author mknobloch
 */
//@Ignore("weights faulty")
public class GraphViewTest extends SimpleGraphTestCase {

    private static SimpleGraphSetup setup = new SimpleGraphSetup();

    @BeforeClass
    public static void setupGraph() {
        graph = new GraphView((GraphDatabaseAPI) setup.getDb(), Direction.BOTH, LABEL, RELATION, WEIGHT_PROPERTY, 0.0);
        v0 = 0;
        v1 = 1;
        v2 = 2;
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (setup != null) setup.shutdown();
        if (db != null) db.shutdown();
    }
}
