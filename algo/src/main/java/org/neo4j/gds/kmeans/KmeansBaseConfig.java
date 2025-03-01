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
package org.neo4j.gds.kmeans;


import org.immutables.value.Value;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.IterationsConfig;
import org.neo4j.gds.config.NodeWeightConfig;
import org.neo4j.gds.config.SingleThreadedRandomSeedConfig;

@ValueClass
@Configuration
@SuppressWarnings("immutables:subtype")
public interface KmeansBaseConfig extends AlgoBaseConfig, IterationsConfig, SingleThreadedRandomSeedConfig, NodeWeightConfig {

    @Configuration.IntegerRange(min = 1)
    @Override
    @Value.Default
    default int maxIterations() {
        return 10;
    }

    @Value.Default
    @Configuration.IntegerRange(min = 1)
    default int K() {
        return 10;
    }
}
