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
package org.neo4j.gds.ml.linkmodels.pipeline;

import org.neo4j.gds.BaseProc;
import org.neo4j.gds.core.ConfigKeyValidation;
import org.neo4j.gds.ml.models.TrainingMethod;
import org.neo4j.gds.ml.models.automl.TunableTrainerConfig;
import org.neo4j.gds.ml.models.logisticregression.LogisticRegressionTrainConfig;
import org.neo4j.gds.ml.models.randomforest.RandomForestTrainConfig;
import org.neo4j.gds.ml.pipeline.PipelineCatalog;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionTrainingPipeline;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class LinkPredictionPipelineAddTrainerMethodProcs extends BaseProc {

    @Procedure(name = "gds.beta.pipeline.linkPrediction.addLogisticRegression", mode = READ)
    @Description("Add a logistic regression configuration to the parameter space of the link prediction train pipeline.")
    public Stream<PipelineInfoResult> addLogisticRegression(
        @Name("pipelineName") String pipelineName,
        @Name(value = "config", defaultValue = "{}") Map<String, Object> config
    ) {
        var pipeline = PipelineCatalog.getTyped(username(), pipelineName, LinkPredictionTrainingPipeline.class);

        var allowedKeys = LogisticRegressionTrainConfig.DEFAULT.configKeys();
        ConfigKeyValidation.requireOnlyKeysFrom(allowedKeys, config.keySet());

        var tunableTrainerConfig = TunableTrainerConfig.of(config, TrainingMethod.LogisticRegression);
        pipeline.addTrainerConfig(
            tunableTrainerConfig
        );

        return Stream.of(new PipelineInfoResult(pipelineName, pipeline));
    }

    @Procedure(name = "gds.alpha.pipeline.linkPrediction.addRandomForest", mode = READ)
    @Description("Add a random forest configuration to the parameter space of the link prediction train pipeline.")
    public Stream<PipelineInfoResult> addRandomForest(
        @Name("pipelineName") String pipelineName,
        @Name(value = "config") Map<String, Object> randomForestConfig
    ) {
        var pipeline = PipelineCatalog.getTyped(username(), pipelineName, LinkPredictionTrainingPipeline.class);

        var allowedKeys = RandomForestTrainConfig.DEFAULT.configKeys();
        ConfigKeyValidation.requireOnlyKeysFrom(allowedKeys, randomForestConfig.keySet());

        var tunableTrainerConfig = TunableTrainerConfig.of(randomForestConfig, TrainingMethod.RandomForest);
        pipeline.addTrainerConfig(
            tunableTrainerConfig
        );

        return Stream.of(new PipelineInfoResult(pipelineName, pipeline));
    }
}
