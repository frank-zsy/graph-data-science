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
package org.neo4j.gds.ml.pipeline.linkPipeline;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.executor.GdsCallableFinder;
import org.neo4j.gds.ml.models.TrainingMethod;
import org.neo4j.gds.ml.models.automl.TunableTrainerConfig;
import org.neo4j.gds.ml.models.logisticregression.LogisticRegressionTrainConfig;
import org.neo4j.gds.ml.models.randomforest.RandomForestTrainConfigImpl;
import org.neo4j.gds.ml.pipeline.AutoTuningConfig;
import org.neo4j.gds.ml.pipeline.NodePropertyStep;
import org.neo4j.gds.ml.pipeline.TestGdsCallableFinder;
import org.neo4j.gds.ml.pipeline.linkPipeline.linkfunctions.CosineFeatureStep;
import org.neo4j.gds.ml.pipeline.linkPipeline.linkfunctions.HadamardFeatureStep;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LinkPredictionPipelineTest {

    @Test
    void canCreateEmptyPipeline() {
        var pipeline = new LinkPredictionTrainingPipeline();

        assertThat(pipeline)
            .returns(List.of(), LinkPredictionTrainingPipeline::featureSteps)
            .returns(List.of(), LinkPredictionTrainingPipeline::nodePropertySteps)
            .returns(LinkPredictionSplitConfig.DEFAULT_CONFIG, LinkPredictionTrainingPipeline::splitConfig);

        assertThat(pipeline.trainingParameterSpace().get(TrainingMethod.LogisticRegression)).isEmpty();
    }

    @Test
    void canAddFeatureSteps() {
        var pipeline = new LinkPredictionTrainingPipeline();
        var hadamardFeatureStep = new HadamardFeatureStep(List.of("a"));
        pipeline.addFeatureStep(hadamardFeatureStep);

        assertThat(pipeline)
            .returns(List.of(hadamardFeatureStep), LinkPredictionTrainingPipeline::featureSteps);

        var cosineFeatureStep = new CosineFeatureStep(List.of("b", "c"));
        pipeline.addFeatureStep(cosineFeatureStep);

        assertThat(pipeline)
            .returns(List.of(hadamardFeatureStep, cosineFeatureStep), LinkPredictionTrainingPipeline::featureSteps);
    }

    @Test
    void canAddNodePropertySteps() {
        var pipeline = new LinkPredictionTrainingPipeline();

        GdsCallableFinder.GdsCallableDefinition callableDefinition = GdsCallableFinder
            .findByName("gds.testProc.mutate", List.of())
            .orElseThrow();
        var step = new NodePropertyStep(callableDefinition, Map.of("mutateProperty", "pr"));
        pipeline.addNodePropertyStep(step);

        assertThat(pipeline)
            .returns(List.of(step), LinkPredictionTrainingPipeline::nodePropertySteps);

        var otherStep = new NodePropertyStep(callableDefinition, Map.of("mutateProperty", "pr2"));
        pipeline.addNodePropertyStep(otherStep);

        assertThat(pipeline)
            .returns(List.of(step, otherStep), LinkPredictionTrainingPipeline::nodePropertySteps);
    }

    @Test
    void canSetParameterSpace() {
        var lrConfig = LogisticRegressionTrainConfig.of(Map.of("penalty", 19));
        var rfConfg = RandomForestTrainConfigImpl.builder()
            .maxFeaturesRatio(0.5)
            .numberOfDecisionTrees(1)
            .minSplitSize(2)
            .maxDepth(19)
            .build();

        var pipeline = new LinkPredictionTrainingPipeline();
        pipeline.setConcreteTrainingParameterSpace(TrainingMethod.LogisticRegression, List.of(lrConfig));
        pipeline.setConcreteTrainingParameterSpace(TrainingMethod.RandomForest, List.of(rfConfg));

        assertThat(pipeline.trainingParameterSpace().get(TrainingMethod.LogisticRegression))
            .containsExactly(lrConfig.toTunableConfig());

        assertThat(pipeline.trainingParameterSpace().get(TrainingMethod.RandomForest))
            .containsExactly(rfConfg.toTunableConfig());
    }

    @Test
    void overridesTheParameterSpace() {
        var config1 = LogisticRegressionTrainConfig.of(Map.of("penalty", 19));
        var config2 = LogisticRegressionTrainConfig.of(Map.of("penalty", 1337));
        var config3 = LogisticRegressionTrainConfig.of(Map.of("penalty", 42));

        var pipeline = new LinkPredictionTrainingPipeline();
        pipeline.setConcreteTrainingParameterSpace(TrainingMethod.LogisticRegression, List.of(
            config1
        ));
        pipeline.setConcreteTrainingParameterSpace(TrainingMethod.LogisticRegression, List.of(
            config2,
            config3
        ));

        var parameterSpace = pipeline.trainingParameterSpace();

        assertThat(parameterSpace.get(TrainingMethod.LogisticRegression)).containsExactly(
            config2.toTunableConfig(),
            config3.toTunableConfig()
        );
    }

    @Test
    void canSetSplitConfig() {
        var pipeline = new LinkPredictionTrainingPipeline();
        var splitConfig = LinkPredictionSplitConfigImpl.builder().trainFraction(0.01).testFraction(0.5).build();
        pipeline.setSplitConfig(splitConfig);

        assertThat(pipeline)
            .returns(splitConfig, LinkPredictionTrainingPipeline::splitConfig);
    }

    @Test
    void overridesTheSplitConfig() {
        var pipeline = new LinkPredictionTrainingPipeline();
        var splitConfig = LinkPredictionSplitConfigImpl.builder().trainFraction(0.01).testFraction(0.5).build();
        pipeline.setSplitConfig(splitConfig);

        var splitConfigOverride = LinkPredictionSplitConfigImpl.builder().trainFraction(0.1).testFraction(0.7).build();
        pipeline.setSplitConfig(splitConfigOverride);

        assertThat(pipeline)
            .returns(splitConfigOverride, LinkPredictionTrainingPipeline::splitConfig);
    }

    @Nested
    class ToMapTest {

        @Test
        void returnsCorrectDefaultsMap() {
            var pipeline = new LinkPredictionTrainingPipeline();
            assertThat(pipeline.toMap())
                .containsOnlyKeys("featurePipeline", "splitConfig", "trainingParameterSpace", "autoTuningConfig")
                .satisfies(pipelineMap -> {
                    assertThat(pipelineMap.get("featurePipeline"))
                        .isInstanceOf(Map.class)
                        .asInstanceOf(InstanceOfAssertFactories.MAP)
                        .containsOnlyKeys("nodePropertySteps", "featureSteps")
                        .returns(List.of(), featurePipelineMap -> featurePipelineMap.get("nodePropertySteps"))
                        .returns(List.of(), featurePipelineMap -> featurePipelineMap.get("featureSteps"));
                })
                .returns(
                    LinkPredictionSplitConfig.DEFAULT_CONFIG.toMap(),
                    pipelineMap -> pipelineMap.get("splitConfig")
                )
                .returns(
                    Map.of(
                        TrainingMethod.LogisticRegression.name(), List.of(),
                        TrainingMethod.RandomForest.name(), List.of()
                    ),
                    pipelineMap -> pipelineMap.get("trainingParameterSpace")
                ).returns(
                    AutoTuningConfig.DEFAULT_CONFIG.toMap(),
                    pipelineMap -> pipelineMap.get("autoTuningConfig")
                );
        }

        @Test
        void returnsCorrectMapWithFullConfiguration() {
            var pipeline = new LinkPredictionTrainingPipeline();
            var step = new NodePropertyStep(
                TestGdsCallableFinder.findByName("gds.testProc.mutate").orElseThrow(),
                Map.of("mutateProperty", "prop1")
            );
            pipeline.addNodePropertyStep(step);

            var hadamardFeatureStep = new HadamardFeatureStep(List.of("a"));
            pipeline.addFeatureStep(hadamardFeatureStep);

            pipeline.setConcreteTrainingParameterSpace(TrainingMethod.LogisticRegression, List.of(
                LogisticRegressionTrainConfig.of(Map.of("penalty", 1000000)),
                LogisticRegressionTrainConfig.of(Map.of("penalty", 1))
            ));

            pipeline.setConcreteTrainingParameterSpace(TrainingMethod.RandomForest, List.of(
                RandomForestTrainConfigImpl
                    .builder()
                    .maxDepth(2)
                    .maxFeaturesRatio(0.5)
                    .minSplitSize(2)
                    .numberOfDecisionTrees(1)
                    .build()
            ));

            var splitConfig = LinkPredictionSplitConfigImpl.builder().trainFraction(0.01).testFraction(0.5).build();
            pipeline.setSplitConfig(splitConfig);

            assertThat(pipeline.toMap())
                .containsOnlyKeys("featurePipeline", "splitConfig", "trainingParameterSpace", "autoTuningConfig")
                .satisfies(pipelineMap -> {
                    assertThat(pipelineMap.get("featurePipeline"))
                        .isInstanceOf(Map.class)
                        .asInstanceOf(InstanceOfAssertFactories.MAP)
                        .containsOnlyKeys("nodePropertySteps", "featureSteps")
                        .returns(
                            List.of(step.toMap()),
                            featurePipelineMap -> featurePipelineMap.get("nodePropertySteps")
                        )
                        .returns(
                            List.of(hadamardFeatureStep.toMap()),
                            featurePipelineMap -> featurePipelineMap.get("featureSteps")
                        );
                })
                .returns(
                    pipeline.splitConfig().toMap(),
                    pipelineMap -> pipelineMap.get("splitConfig")
                )
                .returns(
                    Map.of(
                        TrainingMethod.LogisticRegression.name(),
                        pipeline
                            .trainingParameterSpace()
                            .get(TrainingMethod.LogisticRegression)
                            .stream()
                            .map(TunableTrainerConfig::toMap)
                            .collect(Collectors.toList()),
                        TrainingMethod.RandomForest.name(), pipeline.trainingParameterSpace().get(TrainingMethod.RandomForest)
                            .stream()
                            .map(TunableTrainerConfig::toMap)
                            .collect(Collectors.toList())
                    ),
                      pipelineMap -> pipelineMap.get("trainingParameterSpace")
                ).returns(
                    AutoTuningConfig.DEFAULT_CONFIG.toMap(),
                    pipelineMap -> pipelineMap.get("autoTuningConfig")
                );
        }
    }
}
