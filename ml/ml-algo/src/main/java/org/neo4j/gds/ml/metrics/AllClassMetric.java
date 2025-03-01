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

import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.openjdk.jol.util.Multiset;

public enum AllClassMetric implements ClassificationMetric {
    F1_WEIGHTED(new F1Weighted()),
    F1_MACRO(new F1Macro()),
    ACCURACY(new AccuracyMetric());

    private final MetricStrategy strategy;

    AllClassMetric(MetricStrategy strategy) {
        this.strategy = strategy;
    }

    public double compute(
        HugeLongArray targets,
        HugeLongArray predictions,
        Multiset<Long> globalClassCounts
    ) {
        return strategy.compute(targets, predictions, globalClassCounts);
    }

    interface MetricStrategy {
        double compute(
            HugeLongArray targets,
            HugeLongArray predictions,
            Multiset<Long> globalClassCounts
        );
    }
}
