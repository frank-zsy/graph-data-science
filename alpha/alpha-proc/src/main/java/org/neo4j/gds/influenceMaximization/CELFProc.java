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
package org.neo4j.gds.influenceMaximization;

import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.GraphAlgorithmFactory;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.executor.ComputationResultConsumer;
import org.neo4j.gds.executor.GdsCallable;
import org.neo4j.gds.impl.influenceMaximization.CELF;
import org.neo4j.gds.results.InfluenceMaximizationResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.executor.ExecutionMode.STREAM;
import static org.neo4j.gds.influenceMaximization.CELFProc.DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;

@GdsCallable(name = "gds.alpha.influenceMaximization.celf.stream", description = DESCRIPTION, executionMode = STREAM)
public class CELFProc extends AlgoBaseProc<CELF, CELF, InfluenceMaximizationConfig, InfluenceMaximizationResult> {

    public static final String DESCRIPTION = "The Cost Effective Lazy Forward (CELF) algorithm aims to find k nodes that maximize the expected spread of influence in the network.";

    @Procedure(name = "gds.alpha.influenceMaximization.celf.stream", mode = READ)
    @Description(DESCRIPTION)
    public Stream<InfluenceMaximizationResult> stream(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        var computationResult = compute(graphName, configuration);
        return computationResultConsumer().consume(computationResult, executionContext());
    }

    @Override
    protected InfluenceMaximizationConfig newConfig(String username, CypherMapWrapper config) {
        return new InfluenceMaximizationConfigImpl(config);
    }

    @Override
    public GraphAlgorithmFactory<CELF, InfluenceMaximizationConfig> algorithmFactory() {
        return new GraphAlgorithmFactory<>() {
            @Override
            public String taskName() {
                return "CELF";
            }

            @Override
            public CELF build(
                Graph graph,
                InfluenceMaximizationConfig configuration,
                AllocationTracker allocationTracker,
                ProgressTracker progressTracker
            ) {
                return new CELF(
                    graph,
                    configuration.seedSetSize(),
                    configuration.propagationProbability(),
                    configuration.monteCarloSimulations(),
                    Pools.DEFAULT,
                    configuration.concurrency()
                );
            }
        };
    }

    @Override
    public ComputationResultConsumer<CELF, CELF, InfluenceMaximizationConfig, Stream<InfluenceMaximizationResult>> computationResultConsumer() {
        return (computationResult, executionContext) -> {
            if (computationResult.graph().isEmpty()) {
                computationResult.graph().release();
                return Stream.empty();
            }

            computationResult.graph().release();
            return computationResult.algorithm().resultStream();
        };
    }
}
