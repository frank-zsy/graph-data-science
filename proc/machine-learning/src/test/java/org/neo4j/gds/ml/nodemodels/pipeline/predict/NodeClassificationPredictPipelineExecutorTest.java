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
package org.neo4j.gds.ml.nodemodels.pipeline.predict;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.TestProcedureRunner;
import org.neo4j.gds.TestProgressTracker;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.schema.GraphSchema;
import org.neo4j.gds.catalog.GraphProjectProc;
import org.neo4j.gds.catalog.GraphStreamNodePropertiesProc;
import org.neo4j.gds.compat.Neo4jProxy;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.model.Model;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.Neo4jGraph;
import org.neo4j.gds.extension.Neo4jModelCatalogExtension;
import org.neo4j.gds.ml.core.subgraph.LocalIdMap;
import org.neo4j.gds.ml.decisiontree.DecisionTreePredictor;
import org.neo4j.gds.ml.decisiontree.TreeNode;
import org.neo4j.gds.ml.metrics.MetricSpecification;
import org.neo4j.gds.ml.models.Classifier;
import org.neo4j.gds.ml.models.logisticregression.LogisticRegressionTrainConfig;
import org.neo4j.gds.ml.models.randomforest.ImmutableRandomForestData;
import org.neo4j.gds.ml.models.randomforest.RandomForestTrainConfigImpl;
import org.neo4j.gds.ml.pipeline.NodePropertyStepFactory;
import org.neo4j.gds.ml.pipeline.linkPipeline.train.LinkPredictionTrain;
import org.neo4j.gds.ml.pipeline.nodePipeline.NodeClassificationFeatureStep;
import org.neo4j.gds.ml.pipeline.nodePipeline.NodeClassificationPredictPipeline;
import org.neo4j.gds.ml.pipeline.nodePipeline.NodeClassificationTrainingPipeline;
import org.neo4j.gds.ml.pipeline.nodePipeline.train.NodeClassificationPipelineModelInfo;
import org.neo4j.gds.ml.pipeline.nodePipeline.train.NodeClassificationPipelineTrainConfig;
import org.neo4j.gds.ml.pipeline.nodePipeline.train.NodeClassificationPipelineTrainConfigImpl;
import org.neo4j.gds.test.TestProc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.gds.TestSupport.assertMemoryEstimation;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;
import static org.neo4j.gds.assertj.Extractors.replaceTimings;
import static org.neo4j.gds.compat.TestLog.INFO;
import static org.neo4j.gds.ml.nodemodels.pipeline.predict.NodeClassificationPipelinePredictProcTestUtil.addPipelineModelWithFeatures;
import static org.neo4j.gds.ml.nodemodels.pipeline.predict.NodeClassificationPipelinePredictProcTestUtil.createModel;

@Neo4jModelCatalogExtension
class NodeClassificationPredictPipelineExecutorTest extends BaseProcTest {

    private static final String GRAPH_NAME = "g";
    private static final String MODEL_NAME = "model";

    @Neo4jGraph
    static String GDL = "CREATE " +
                        "  (n0:N {a: 1.0, b: 0.8, c: 1})" +
                        ", (n1:N {a: 2.0, b: 1.0, c: 1})" +
                        ", (n2:N {a: 3.0, b: 1.5, c: 1})" +
                        ", (n3:N {a: 0.0, b: 2.8, c: 1})" +
                        ", (n4:N {a: 1.0, b: 0.9, c: 1})" +
                        ", (n1)-[:T]->(n2)" +
                        ", (n3)-[:T]->(n4)" +
                        ", (n1)-[:T]->(n3)" +
                        ", (n2)-[:T]->(n4)";

    private GraphStore graphStore;

    @Inject
    private ModelCatalog modelCatalog;

    @BeforeEach
    void setup() throws Exception {
        registerProcedures(
            GraphProjectProc.class,
            GraphStreamNodePropertiesProc.class
        );
        String createQuery = GdsCypher.call(GRAPH_NAME)
            .graphProject()
            .withNodeLabel("N")
            .withRelationshipType("T", Orientation.UNDIRECTED)
            .withNodeProperties(List.of("a", "b", "c"), DefaultValue.DEFAULT)
            .yields();

        runQuery(createQuery);

        graphStore = GraphStoreCatalog.get(getUsername(), db.databaseId(), "g").graphStore();
    }

    @AfterEach
    void tearDown() {
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @Test
    void shouldPredict() {
        TestProcedureRunner.applyOnProcedure(db, TestProc.class, caller -> {
            var config = new NodeClassificationPredictPipelineBaseConfigImpl(
                "",
                CypherMapWrapper.empty()
                    .withEntry("modelName", "model")
                    .withEntry("includePredictedProbabilities", true)
                    .withEntry("graphName", GRAPH_NAME)
            );

            var pipeline = NodeClassificationPredictPipeline.from(
                Stream.of(),
                Stream.of(
                    NodeClassificationFeatureStep.of("a"),
                    NodeClassificationFeatureStep.of("b"),
                    NodeClassificationFeatureStep.of("c")
                )
            );

            var weights = new double[]{
                -2.0, -1.0, 3.0,
                -1.5, -1.3, 2.6
            };
            var bias = new double[]{0.0, 0.0};
            var modelData = NodeClassificationPipelinePredictProcTestUtil.createModeldata(weights, bias);

            var pipelineExecutor = new NodeClassificationPredictPipelineExecutor(
                pipeline,
                config,
                caller.executionContext(),
                graphStore,
                GRAPH_NAME,
                ProgressTracker.NULL_TRACKER,
                modelData
            );

            var predictionResult = pipelineExecutor.compute();

            assertThat(predictionResult.predictedClasses().size()).isEqualTo(graphStore.nodeCount());
            assertThat(predictionResult.predictedProbabilities()).isPresent();
            assertThat(predictionResult
                .predictedProbabilities()
                .orElseThrow()
                .size()).isEqualTo(graphStore.nodeCount());

            assertThat(graphStore.relationshipTypes()).containsExactlyElementsOf(RelationshipType.listOf("T"));
            assertThat(graphStore.hasNodeProperty(graphStore.nodeLabels(), "degree")).isFalse();
        });
    }

    @Test
    void shouldPredictWithRandomForest() {
        TestProcedureRunner.applyOnProcedure(db, TestProc.class, caller -> {
            var config = new NodeClassificationPredictPipelineBaseConfigImpl(
                "",
                CypherMapWrapper.empty()
                    .withEntry("modelName", "model")
                    .withEntry("includePredictedProbabilities", true)
                    .withEntry("graphName", GRAPH_NAME)
            );

            var pipeline = NodeClassificationPredictPipeline.from(
                Stream.of(),
                Stream.of(
                    NodeClassificationFeatureStep.of("a"),
                    NodeClassificationFeatureStep.of("b"),
                    NodeClassificationFeatureStep.of("c")
                )
            );

            var root = new TreeNode<>(0);
            var modelData = ImmutableRandomForestData
                .builder()
                .addDecisionTree(new DecisionTreePredictor<>(root))
                .featureDimension(3)
                .classIdMap(LinkPredictionTrain.makeClassIdMap())
                .build();

            var pipelineExecutor = new NodeClassificationPredictPipelineExecutor(
                pipeline,
                config,
                caller.executionContext(),
                graphStore,
                GRAPH_NAME,
                ProgressTracker.NULL_TRACKER,
                modelData
            );

            var predictionResult = pipelineExecutor.compute();

            assertThat(predictionResult.predictedClasses().size()).isEqualTo(graphStore.nodeCount());
            assertThat(predictionResult.predictedProbabilities()).isPresent();
            assertThat(predictionResult
                .predictedProbabilities()
                .orElseThrow()
                .size()).isEqualTo(graphStore.nodeCount());

            assertThat(graphStore.relationshipTypes()).containsExactlyElementsOf(RelationshipType.listOf("T"));
            assertThat(graphStore.hasNodeProperty(graphStore.nodeLabels(), "degree")).isFalse();
        });
    }

    @Test
    void shouldPredictWithNodePropertySteps() {
        TestProcedureRunner.applyOnProcedure(db, TestProc.class, caller -> {
            var config = new NodeClassificationPredictPipelineBaseConfigImpl(
                "",
                CypherMapWrapper.empty()
                    .withEntry("modelName", "model")
                    .withEntry("includePredictedProbabilities", true)
                    .withEntry("graphName", GRAPH_NAME)
            );

            var pipeline = NodeClassificationPredictPipeline.from(
                Stream.of(NodePropertyStepFactory.createNodePropertyStep(
                    "degree",
                    Map.of("mutateProperty", "degree")
                )),
                Stream.of(
                    NodeClassificationFeatureStep.of("a"),
                    NodeClassificationFeatureStep.of("b"),
                    NodeClassificationFeatureStep.of("c"),
                    NodeClassificationFeatureStep.of("degree")
                )
            );

            var weights = new double[]{
                1.0, 1.0, -2.0, -1.0,
                0.0, -1.5, -1.3, 2.6
            };
            var bias = new double[]{3.0, 0.0};
            var modelData = NodeClassificationPipelinePredictProcTestUtil.createModeldata(weights, bias);

            var pipelineExecutor = new NodeClassificationPredictPipelineExecutor(
                pipeline,
                config,
                caller.executionContext(),
                graphStore,
                GRAPH_NAME,
                ProgressTracker.NULL_TRACKER,
                modelData
            );

            var predictionResult = pipelineExecutor.compute();
            assertThat(predictionResult.predictedClasses().size()).isEqualTo(graphStore.nodeCount());
            assertThat(predictionResult.predictedProbabilities()).isPresent();
            assertThat(predictionResult
                .predictedProbabilities()
                .orElseThrow()
                .size()).isEqualTo(graphStore.nodeCount());

            assertThat(graphStore.relationshipTypes()).containsExactlyElementsOf(RelationshipType.listOf("T"));
            assertThat(graphStore.hasNodeProperty(graphStore.nodeLabels(), "degree")).isFalse();
        });
    }

    @Test
    void progressTracking() {
        TestProcedureRunner.applyOnProcedure(db, TestProc.class, caller -> {
            var config = new NodeClassificationPredictPipelineBaseConfigImpl(
                "",
                CypherMapWrapper.empty()
                    .withEntry("modelName", "model")
                    .withEntry("includePredictedProbabilities", true)
                    .withEntry("graphName", GRAPH_NAME)
            );

            var pipeline = NodeClassificationPredictPipeline.from(
                Stream.of(NodePropertyStepFactory.createNodePropertyStep(
                    "degree",
                    Map.of("mutateProperty", "degree")
                )),
                Stream.of(
                    NodeClassificationFeatureStep.of("a"),
                    NodeClassificationFeatureStep.of("b"),
                    NodeClassificationFeatureStep.of("c"),
                    NodeClassificationFeatureStep.of("degree")
                )
            );

            var weights = new double[]{
                1.0, 1.0, -2.0, -1.0,
                0.0, -1.5, -1.3, 2.6
            };
            var bias = new double[]{3.0, 0.0};
            var modelData = NodeClassificationPipelinePredictProcTestUtil.createModeldata(weights, bias);

            modelCatalog.set(Model.of(
                getUsername(),
                "model",
                NodeClassificationTrainingPipeline.MODEL_TYPE,
                GraphSchema.empty(),
                modelData,
                NodeClassificationPipelineTrainConfigImpl.builder()
                    .username(getUsername())
                    .modelName("model")
                    .pipeline("DUMMY")
                    .graphName(GRAPH_NAME)
                    .metrics(MetricSpecification.parse(List.of("F1_MACRO")))
                    .targetProperty("foo")
                    .build(),
                NodeClassificationPipelineModelInfo.builder()
                    .classes(modelData.classIdMap().originalIdsList())
                    .bestParameters(LogisticRegressionTrainConfig.DEFAULT)
                    .metrics(Map.of())
                    .pipeline(pipeline)
                    .build()
            ));

            var log = Neo4jProxy.testLog();
            var progressTracker = new TestProgressTracker(
                new NodeClassificationPredictPipelineAlgorithmFactory<>(
                    caller.executionContext(),
                    modelCatalog
                ).progressTask(graphStore, config),
                log,
                1,
                EmptyTaskRegistryFactory.INSTANCE
            );

            var pipelineExecutor = new NodeClassificationPredictPipelineExecutor(
                pipeline,
                config,
                caller.executionContext(),
                graphStore,
                GRAPH_NAME,
                progressTracker,
                modelData
            );

            pipelineExecutor.compute();

            var expectedMessages = new ArrayList<>(List.of(
                "Node Classification Predict Pipeline :: Start",
                "Node Classification Predict Pipeline :: execute node property steps :: Start",
                "Node Classification Predict Pipeline :: execute node property steps :: step 1 of 1 :: Start",
                "Node Classification Predict Pipeline :: execute node property steps :: step 1 of 1 100%",
                "Node Classification Predict Pipeline :: execute node property steps :: step 1 of 1 :: Finished",
                "Node Classification Predict Pipeline :: execute node property steps :: Finished",
                "Node Classification Predict Pipeline :: Node classification predict :: Start",
                "Node Classification Predict Pipeline :: Node classification predict 100%",
                "Node Classification Predict Pipeline :: Node classification predict :: Finished",
                "Node Classification Predict Pipeline :: Finished"
            ));

            assertThat(log.getMessages(INFO))
                .extracting(removingThreadId())
                .extracting(replaceTimings())
                .containsExactly(expectedMessages.toArray(String[]::new));
        });
    }

    @Test
    void validateFeaturesExistOnGraph() {
        addPipelineModelWithFeatures(modelCatalog, GRAPH_NAME, getUsername(), 3, List.of("a", "b", "d"));
        TestProcedureRunner.applyOnProcedure(db, TestProc.class, caller -> {
            var factory = new NodeClassificationPredictPipelineAlgorithmFactory<>(
                caller.executionContext(),
                modelCatalog
            );
            var streamConfig = ImmutableNodeClassificationPredictPipelineBaseConfig.builder()
                .username(getUsername())
                .modelName(MODEL_NAME)
                .graphName(GRAPH_NAME)
                .includePredictedProbabilities(false)
                .build();

            var algo = factory.build(
                graphStore,
                streamConfig,
                ProgressTracker.NULL_TRACKER
            );
            assertThatThrownBy(algo::compute)
                .hasMessage(
                    "Node properties [d] defined in the feature steps do not exist in the graph or part of the pipeline"
                );
        });
    }

    @Test
    void shouldEstimateMemory() {
        var model = createModel(GRAPH_NAME, getUsername(), 3, List.of("a", "b", "d"));
        var config = new NodeClassificationPredictPipelineBaseConfigImpl.Builder()
            .concurrency(1)
            .graphName(GRAPH_NAME)
            .modelName(model.name())
            .includePredictedProbabilities(true)
            .username("user")
            .build();

        var memoryEstimation = NodeClassificationPredictPipelineExecutor.estimate(model, config, modelCatalog);
        assertMemoryEstimation(
            () -> memoryEstimation,
            graphStore.nodeCount(),
            graphStore.relationshipCount(),
            config.concurrency(),
            6952L,
            6952L
        );

    }


    @Test
    void shouldEstimateMemoryWithRandomForest() {
        var root = new TreeNode<>(0);
        var modelData = ImmutableRandomForestData
            .builder()
            .addDecisionTree(new DecisionTreePredictor<>(root))
            .featureDimension(3)
            .classIdMap(LocalIdMap.ofSorted(List.of(0L)))
            .build();

        Model<Classifier.ClassifierData, NodeClassificationPipelineTrainConfig, NodeClassificationPipelineModelInfo> model = Model.of(
            getUsername(),
            "model",
            NodeClassificationTrainingPipeline.MODEL_TYPE,
            GraphSchema.empty(),
            modelData,
            NodeClassificationPipelineTrainConfigImpl.builder()
                .username(getUsername())
                .modelName("model")
                .metrics(MetricSpecification.parse(List.of("F1_MACRO")))
                .graphName(GRAPH_NAME)
                .pipeline("DUMMY")
                .targetProperty("foo")
                .build(),
            NodeClassificationPipelineModelInfo.builder()
                .classes(modelData.classIdMap().originalIdsList())
                .bestParameters(RandomForestTrainConfigImpl.builder().build())
                .metrics(Map.of())
                .pipeline(NodeClassificationPredictPipeline.EMPTY)
                .build()
        );

        var config = new NodeClassificationPredictPipelineBaseConfigImpl.Builder()
            .concurrency(1)
            .graphName(GRAPH_NAME)
            .modelName(model.name())
            .includePredictedProbabilities(true)
            .username("user")
            .build();

        var memoryEstimation = NodeClassificationPredictPipelineExecutor.estimate(model, config, modelCatalog);
        assertMemoryEstimation(
            () -> memoryEstimation,
            graphStore.nodeCount(),
            graphStore.relationshipCount(),
            config.concurrency(),
            400L,
            400L
        );
    }
}
