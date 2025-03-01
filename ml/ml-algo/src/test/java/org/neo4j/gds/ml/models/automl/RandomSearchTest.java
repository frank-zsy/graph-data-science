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
package org.neo4j.gds.ml.models.automl;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.ml.models.TrainingMethod;
import org.neo4j.gds.ml.models.logisticregression.LogisticRegressionTrainConfig;
import org.neo4j.gds.ml.models.randomforest.RandomForestTrainConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RandomSearchTest {
    @Test
    void shouldSampleMethodsRoughlyEqually() {
        var maxTrials = 10000;
        var randomSearch = new RandomSearch(
            Map.of(
                TrainingMethod.LogisticRegression,
                List.of(
                    TunableTrainerConfig.of(
                        Map.of("penalty", Map.of("range", List.of(1e-4, 1e4))),
                        TrainingMethod.LogisticRegression
                    )
                ),
                TrainingMethod.RandomForest,
                List.of(
                    TunableTrainerConfig.of(
                        Map.of(
                            "maxFeaturesRatio", Map.of("range", List.of(0.0, 1.0))
                        ),
                        TrainingMethod.RandomForest
                    ),
                    TunableTrainerConfig.of(
                        Map.of(),
                        TrainingMethod.RandomForest
                    )
                )
            ),
            maxTrials,
            // passed 2500 times
            System.currentTimeMillis()
        );
        var randomForestSamples = 0;
        while (randomSearch.hasNext()) {
            var trainerConfig = randomSearch.next();
            if (trainerConfig instanceof RandomForestTrainConfig) randomForestSamples++;
        }
        assertThat(randomForestSamples).isCloseTo((int) (0.5 * maxTrials), Offset.offset((int) (0.05 * maxTrials)));
    }

    @Test
    void shouldProduceLogScaleSamples() {
        var maxTrials = 10000;
        var randomSearch = new RandomSearch(
            Map.of(
                TrainingMethod.LogisticRegression,
                List.of(
                    TunableTrainerConfig.of(
                        Map.of(
                            "penalty", Map.of("range", List.of(1e-4, 1e4))
                        ),
                        TrainingMethod.LogisticRegression
                    )
                )
            ),
            maxTrials,
            // passed 2500 times
            System.currentTimeMillis()
        );
        // in log scale, the midpoint is 1.0
        var penaltiesHigherThanOne = 0;
        while (randomSearch.hasNext()) {
            var trainerConfig = randomSearch.next();
            assertThat(trainerConfig).isInstanceOf(LogisticRegressionTrainConfig.class);
            var lrConfig = (LogisticRegressionTrainConfig) trainerConfig;
            assertThat(lrConfig.penalty()).isBetween(1e-4, 1e4);
            if (lrConfig.penalty() > 1.0) penaltiesHigherThanOne++;
        }
        assertThat(penaltiesHigherThanOne).isCloseTo((int) (0.5 * maxTrials), Offset.offset((int) (0.05 * maxTrials)));
    }

    @Test
    void shouldProduceLinearScaleSamples() {
        var maxTrials = 10000;
        var randomSearch = new RandomSearch(
            Map.of(
                TrainingMethod.RandomForest,
                List.of(
                    TunableTrainerConfig.of(
                        Map.of(
                            "maxFeaturesRatio", Map.of("range", List.of(0.0, 1.0))
                        ),
                        TrainingMethod.RandomForest
                    )
                )
            ),
            maxTrials,
            // passed 2500 times
            System.currentTimeMillis()
        );
        var penaltiesHigherThanAHalf = 0;
        while (randomSearch.hasNext()) {
            var trainerConfig = randomSearch.next();
            assertThat(trainerConfig).isInstanceOf(RandomForestTrainConfig.class);
            var lrConfig = (RandomForestTrainConfig) trainerConfig;
            assertThat(lrConfig.maxFeaturesRatio().get()).isBetween(0.0, 1.0);
            if (lrConfig.maxFeaturesRatio().get() > 0.5) penaltiesHigherThanAHalf++;
        }
        assertThat(penaltiesHigherThanAHalf).isCloseTo((int) (0.5 * maxTrials), Offset.offset((int) (0.05 * maxTrials)));
    }

    @Test
    void shouldProduceConcreteConfigsFirst() {
        var maxTrials = 1;
        var randomSearch = new RandomSearch(
            Map.of(
                TrainingMethod.LogisticRegression,
                List.of(
                    TunableTrainerConfig.of(
                        Map.of(
                            "penalty", Map.of("range", List.of(1e-4, 1e4))
                        ),
                        TrainingMethod.LogisticRegression
                    ),
                    TunableTrainerConfig.of(
                        Map.of(),
                        TrainingMethod.RandomForest
                    )
                )
            ),
            maxTrials,
            System.currentTimeMillis()
        );
        assertThat(randomSearch.hasNext());
        assertThat(randomSearch.next()).isInstanceOf(RandomForestTrainConfig.class);
        assertThat(!randomSearch.hasNext());
    }
}
