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
package org.neo4j.gds.embeddings.graphsage;

import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.StreamProc;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSage;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageAlgorithmFactory;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageStreamConfig;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.gds.validation.ValidationConfiguration;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.neo4j.gds.embeddings.graphsage.GraphSageCompanion.GRAPHSAGE_DESCRIPTION;
import static org.neo4j.gds.embeddings.graphsage.GraphSageCompanion.getActualConfig;
import static org.neo4j.gds.embeddings.graphsage.GraphSageCompanion.injectRelationshipWeightPropertyFromModel;

public class GraphSageStreamProc extends StreamProc<GraphSage, GraphSage.GraphSageResult, GraphSageStreamProc.GraphSageStreamResult, GraphSageStreamConfig> {

    @Context
    public ModelCatalog modelCatalog;

    @Description(GRAPHSAGE_DESCRIPTION)
    @Procedure(name = "gds.beta.graphSage.stream", mode = Mode.READ)
    public Stream<GraphSageStreamResult> stream(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        injectRelationshipWeightPropertyFromModel(getActualConfig(graphName, configuration), modelCatalog, username.username());

        return stream(compute(graphName, configuration));
    }

    @Procedure(value = "gds.beta.graphSage.stream.estimate", mode = Mode.READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        injectRelationshipWeightPropertyFromModel(getActualConfig(graphNameOrConfiguration, algoConfiguration), modelCatalog, username.username());

        return computeEstimate(graphNameOrConfiguration, algoConfiguration);
    }

    @Override
    protected Stream<GraphSageStreamResult> stream(ComputationResult<GraphSage, GraphSage.GraphSageResult, GraphSageStreamConfig> computationResult) {
        return runWithExceptionLogging("GraphSage streaming failed", () -> {
            var graph = computationResult.graph();
            var result = computationResult.result();

            if (result == null) {
                return Stream.empty();
            }

            return LongStream.range(0, graph.nodeCount())
                .mapToObj(i -> new GraphSageStreamResult(
                    graph.toOriginalNodeId(i),
                    result.embeddings().get(i)
                ));
        });
    }

    @Override
    public ValidationConfiguration<GraphSageStreamConfig> getValidationConfig() {
        return GraphSageCompanion.getValidationConfig(modelCatalog, username());
    }

    @Override
    protected GraphSageStreamConfig newConfig(String username, CypherMapWrapper config) {
        return GraphSageStreamConfig.of(username, config);
    }

    @Override
    protected AlgorithmFactory<GraphSage, GraphSageStreamConfig> algorithmFactory() {
        return new GraphSageAlgorithmFactory<>(modelCatalog);
    }

    @Override
    protected GraphSageStreamResult streamResult(
        long originalNodeId, long internalNodeId, NodeProperties nodeProperties
    ) {
        throw new UnsupportedOperationException("GraphSage handles result building individually.");
    }

    @SuppressWarnings("unused")
    public static class GraphSageStreamResult {
        public long nodeId;
        public List<Double> embedding;

        GraphSageStreamResult(long nodeId, double[] embeddings) {
            this.nodeId = nodeId;
            this.embedding = Arrays.stream(embeddings).boxed().collect(Collectors.toList());
        }
    }
}
