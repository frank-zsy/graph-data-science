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
package org.neo4j.gds.ml.linkmodels.pipeline.train;

import org.neo4j.gds.GraphStoreAlgorithmFactory;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.GraphDimensions;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.executor.ExecutionContext;
import org.neo4j.gds.ml.pipeline.PipelineCatalog;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionTrainingPipeline;
import org.neo4j.gds.ml.pipeline.linkPipeline.train.LinkPredictionTrain;
import org.neo4j.gds.ml.pipeline.linkPipeline.train.LinkPredictionTrainConfig;

import java.util.List;

public class LinkPredictionTrainPipelineAlgorithmFactory extends GraphStoreAlgorithmFactory<LinkPredictionTrainPipelineExecutor, LinkPredictionTrainConfig> {
    private final ExecutionContext executionContext;

    public LinkPredictionTrainPipelineAlgorithmFactory(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    @Override
    public LinkPredictionTrainPipelineExecutor build(
        GraphStore graphStore,
        LinkPredictionTrainConfig trainConfig,
        ProgressTracker progressTracker
    ) {
        var pipeline = PipelineCatalog.getTyped(
            trainConfig.username(),
            trainConfig.pipeline(),
            LinkPredictionTrainingPipeline.class
        );

        return new LinkPredictionTrainPipelineExecutor(
            pipeline,
            trainConfig,
            executionContext,
            graphStore,
            trainConfig.graphName(),
            progressTracker
        );
    }

    @Override
    public String taskName() {
        return "Link Prediction Train Pipeline";
    }

    @Override
    public Task progressTask(GraphStore graphStore, LinkPredictionTrainConfig config) {
        var pipeline = PipelineCatalog.getTyped(config.username(), config.pipeline(), LinkPredictionTrainingPipeline.class);

        return Tasks.task(
            taskName(),
            Tasks.leaf("split relationships"),
            Tasks.iterativeFixed(
                "execute node property steps",
                () -> List.of(Tasks.leaf("step")),
                pipeline.nodePropertySteps().size()
            ),
            LinkPredictionTrain.progressTask()
        );
    }

    public MemoryEstimation memoryEstimation(LinkPredictionTrainConfig configuration) {
        var pipeline = PipelineCatalog.getTyped(
            configuration.username(),
            configuration.pipeline(),
            LinkPredictionTrainingPipeline.class
        );

        return LinkPredictionTrainPipelineExecutor.estimate(executionContext.modelCatalog(), pipeline, configuration);
    }

    @Override
    public GraphDimensions estimatedGraphDimensionTransformer(GraphDimensions graphDimensions, LinkPredictionTrainConfig config) {
        // inject expected relationship set sizes which are used in the estimation of the TrainPipelineExecutor
        // this allows to compute the MemoryTree over a single graphDimension
        var splitConfig = PipelineCatalog
            .getTyped(config.username(), config.pipeline(), LinkPredictionTrainingPipeline.class)
            .splitConfig();

        return splitConfig.expectedGraphDimensions(graphDimensions.nodeCount(), graphDimensions.relCountUpperBound());
    }
}
