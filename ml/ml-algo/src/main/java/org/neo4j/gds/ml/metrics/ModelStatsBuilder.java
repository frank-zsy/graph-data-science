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
package org.neo4j.gds.ml.metrics;

import org.neo4j.gds.ml.models.TrainerConfig;

import java.util.HashMap;
import java.util.Map;

public class ModelStatsBuilder {
    private final Map<Metric, Double> min;
    private final Map<Metric, Double> max;
    private final Map<Metric, Double> sum;
    private final TrainerConfig modelParams;
    private final int numberOfSplits;

    public ModelStatsBuilder(TrainerConfig modelParams, int numberOfSplits) {
        this.modelParams = modelParams;
        this.numberOfSplits = numberOfSplits;
        this.min = new HashMap<>();
        this.max = new HashMap<>();
        this.sum = new HashMap<>();
    }

    public void update(Metric metric, double value) {
        min.merge(metric, value, Math::min);
        max.merge(metric, value, Math::max);
        sum.merge(metric, value, Double::sum);
    }

    public ModelStats build(Metric metric) {
        return ImmutableModelStats.of(
            modelParams,
            sum.get(metric) / numberOfSplits,
            min.get(metric),
            max.get(metric)
        );
    }
}
