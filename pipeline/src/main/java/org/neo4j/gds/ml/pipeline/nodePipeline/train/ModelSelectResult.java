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
package org.neo4j.gds.ml.pipeline.nodePipeline.train;

import org.immutables.value.Value;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.ml.metrics.Metric;
import org.neo4j.gds.ml.metrics.ModelStats;
import org.neo4j.gds.ml.metrics.StatsMap;
import org.neo4j.gds.ml.models.TrainerConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ValueClass
public interface ModelSelectResult {
    TrainerConfig bestParameters();

    Map<Metric, List<ModelStats>> trainStats();

    Map<Metric, List<ModelStats>> validationStats();

    static ModelSelectResult of(
        TrainerConfig bestConfig,
        StatsMap trainStats,
        StatsMap validationStats
    ) {
        return ImmutableModelSelectResult.of(bestConfig, trainStats.getMap(), validationStats.getMap());
    }

    @Value.Derived
    default Map<String, Object> toMap() {
        Function<Map<Metric, List<ModelStats>>, Map<String, Object>> statsConverter = stats ->
            stats.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().name(),
                value -> value.getValue().stream().map(ModelStats::toMap)
            ));

        return Map.of(
            "bestParameters", bestParameters().toMap(),
            "trainStats", statsConverter.apply(trainStats()),
            "validationStats", statsConverter.apply(validationStats())
        );
    }

}
