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
package org.neo4j.gds.beta.pregel;

import org.immutables.value.Value;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.beta.pregel.context.MasterComputeContext;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.paged.HugeAtomicBitSet;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Value.Style(builderVisibility = Value.Style.BuilderVisibility.PUBLIC, depluralize = true, deepImmutablesDetection = true)
public final class Pregel<CONFIG extends PregelConfig> {

    private final CONFIG config;

    private final PregelComputation<CONFIG> computation;

    private final Graph graph;

    private final NodeValue nodeValues;

    private final Messenger<?> messenger;

    private final PregelComputer<CONFIG> computer;

    private final ProgressTracker progressTracker;

    private final ExecutorService executor;

    public static <CONFIG extends PregelConfig> Pregel<CONFIG> create(
        Graph graph,
        CONFIG config,
        PregelComputation<CONFIG> computation,
        ExecutorService executor,
        ProgressTracker progressTracker
    ) {
        // This prevents users from disabling concurrency
        // validation in custom PregelConfig implementations.
        // Creating a copy of the user config triggers the
        // concurrency validations.
        ImmutablePregelConfig.copyOf(config);

        return new Pregel<>(
            graph,
            config,
            computation,
            NodeValue.of(computation.schema(config), graph.nodeCount(), config.concurrency()),
            executor,
            progressTracker
        );
    }

    public static MemoryEstimation memoryEstimation(PregelSchema pregelSchema, boolean isQueueBased, boolean isAsync) {
        var estimationBuilder = MemoryEstimations.builder(Pregel.class)
            .perNode("vote bits", HugeAtomicBitSet::memoryEstimation)
            .perThread("compute steps", MemoryEstimations.builder(PartitionedComputeStep.class).build())
            .add("node value", NodeValue.memoryEstimation(pregelSchema));

        if (isQueueBased) {
            if (isAsync) {
                estimationBuilder.add("message queues", AsyncQueueMessenger.memoryEstimation());
            } else {
                estimationBuilder.add("message queues", SyncQueueMessenger.memoryEstimation());
            }
        } else {
            estimationBuilder.add("message arrays", ReducingMessenger.memoryEstimation());
        }

        return estimationBuilder.build();
    }

    public static <CONFIG extends PregelConfig> Task progressTask(Graph graph, CONFIG config, String taskName) {
        return Tasks.iterativeDynamic(
            taskName,
            () -> List.of(
                Tasks.leaf("Compute iteration", graph.nodeCount()),
                Tasks.leaf("Master compute iteration", graph.nodeCount())
            ),
            config.maxIterations()
        );
    }

    public static <CONFIG extends PregelConfig> Task progressTask(Graph graph, CONFIG config) {
        var configName = config.getClass().getSimpleName();
        var taskName = configName.replaceAll(
            "(Mutate|Stream|Write|Stats)*Config",
            ""
        );
        return progressTask(graph, config, taskName);
    }

    private Pregel(
        final Graph graph,
        final CONFIG config,
        final PregelComputation<CONFIG> computation,
        final NodeValue initialNodeValue,
        final ExecutorService executor,
        final ProgressTracker progressTracker
    ) {
        this.graph = graph;
        this.config = config;
        this.computation = computation;
        this.nodeValues = initialNodeValue;
        this.executor = executor;
        this.progressTracker = progressTracker;

        var reducer = computation.reducer();

        this.messenger = reducer.isPresent()
            ? new ReducingMessenger(graph, config, reducer.get())
            : config.isAsynchronous()
                ? new AsyncQueueMessenger(graph.nodeCount())
                : new SyncQueueMessenger(graph.nodeCount());

        this.computer = PregelComputer.<CONFIG>builder()
            .graph(graph)
            .computation(computation)
            .config(config)
            .nodeValues(nodeValues)
            .messenger(messenger)
            .voteBits(HugeAtomicBitSet.create(graph.nodeCount()))
            .executorService(config.useForkJoin()
                ? Pools.createForkJoinPool(config.concurrency())
                : executor)
            .progressTracker(progressTracker)
            .build();
    }

    public PregelResult run() {
        boolean didConverge = false;

        computer.initComputation();

        try {
            progressTracker.beginSubTask();

            int iteration = 0;
            for (; iteration < config.maxIterations(); iteration++) {
                try {
                    progressTracker.beginSubTask();

                    computer.initIteration(iteration);
                    messenger.initIteration(iteration);
                    computer.runIteration();
                } finally {
                    progressTracker.endSubTask();
                }

                try {
                    progressTracker.beginSubTask();

                    didConverge = runMasterComputeStep(iteration) || computer.hasConverged();
                } finally {
                    progressTracker.endSubTask();
                }

                if (didConverge) {
                    break;
                }
            }
            return ImmutablePregelResult.builder()
                .nodeValues(nodeValues)
                .didConverge(didConverge)
                .ranIterations(iteration)
                .build();
        } finally {
            progressTracker.endSubTask();
            computer.release();
        }
    }

    public void release() {
        progressTracker.release();
        messenger.release();
    }

    private boolean runMasterComputeStep(int iteration) {
        var context = new MasterComputeContext<>(config, graph, iteration, nodeValues, executor, progressTracker);
        var didConverge = computation.masterCompute(context);
        return didConverge;
    }
}
