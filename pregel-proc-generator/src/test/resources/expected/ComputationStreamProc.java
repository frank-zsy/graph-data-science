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
package org.neo4j.gds.beta.pregel.cc;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.processing.Generated;
import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.BaseProc;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.beta.pregel.Pregel;
import org.neo4j.gds.beta.pregel.PregelProcedureConfig;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.pregel.proc.PregelStreamProc;
import org.neo4j.gds.pregel.proc.PregelStreamResult;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

@Generated("org.neo4j.gds.beta.pregel.PregelProcessor")
public final class ComputationStreamProc extends PregelStreamProc<ComputationAlgorithm, PregelProcedureConfig> {
    @Procedure(
            name = "gds.pregel.test.stream",
            mode = Mode.READ
    )
    @Description("Test computation description")
    public Stream<PregelStreamResult> stream(@Name("graphName") String graphName,
            @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {
        return stream(compute(graphName, configuration));
    }

    @Procedure(
            name = "gds.pregel.test.stream.estimate",
            mode = Mode.READ
    )
    @Description(BaseProc.ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> streamEstimate(@Name("graphName") Object graphNameOrConfig,
            @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {
        return computeEstimate(graphNameOrConfig, configuration);
    }

    @Override
    protected PregelStreamResult streamResult(long originalNodeId, long internalNodeId,
            NodeProperties nodeProperties) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected PregelProcedureConfig newConfig(String username, Optional<String> graphName, CypherMapWrapper config) {
        return PregelProcedureConfig.of(graphName, config);
    }

    @Override
    protected AlgorithmFactory<ComputationAlgorithm, PregelProcedureConfig> algorithmFactory() {
        return new AlgorithmFactory<ComputationAlgorithm, PregelProcedureConfig>() {
            @Override
            public ComputationAlgorithm build(Graph graph, PregelProcedureConfig configuration,
                                              AllocationTracker allocationTracker, ProgressTracker progressTracker) {
                return new ComputationAlgorithm(graph, configuration, allocationTracker, progressTracker);
            }

            @Override
            protected String taskName() {
                return ComputationAlgorithm.class.getSimpleName();
            }

            @Override
            public Task progressTask(Graph graph, PregelProcedureConfig configuration) {
                return Pregel.progressTask(graph, configuration);
            }

            @Override
            public MemoryEstimation memoryEstimation(PregelProcedureConfig configuration) {
                var computation = new Computation();
                return Pregel.memoryEstimation(computation.schema(configuration), computation.reducer().isPresent(), configuration.isAsynchronous());
            }
        };
    }
}
