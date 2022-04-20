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

import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.beta.pregel.context.ComputeContext;
import org.neo4j.gds.beta.pregel.context.InitContext;
import org.neo4j.gds.core.utils.paged.HugeAtomicBitSet;
import org.neo4j.gds.core.utils.partition.Partition;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.mem.BitUtil;

import java.util.concurrent.CountedCompleter;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ForkJoinComputeStep<CONFIG extends PregelConfig, ITERATOR extends Messages.MessageIterator>
    extends CountedCompleter<Void>
    implements ComputeStep<CONFIG, ITERATOR> {

    private static final int SEQUENTIAL_THRESHOLD = 1000;

    private final Graph graph;
    private final CONFIG config;

    private final InitContext<CONFIG> initContext;
    private final ComputeContext<CONFIG> computeContext;
    private final NodeValue nodeValue;
    private final HugeAtomicBitSet voteBits;
    private final Messenger<ITERATOR> messenger;
    private final PregelComputation<CONFIG> computation;

    private Partition nodeBatch;
    private final int iteration;
    private boolean hasSendMessage;
    private final AtomicBoolean sentMessage;
    private final ProgressTracker progressTracker;

    ForkJoinComputeStep(
        Graph graph,
        PregelComputation<CONFIG> computation,
        CONFIG config,
        int iteration,
        Partition nodeBatch,
        NodeValue nodeValue,
        Messenger<ITERATOR> messenger,
        HugeAtomicBitSet voteBits,
        @Nullable CountedCompleter<Void> parent,
        AtomicBoolean sentMessage,
        ProgressTracker progressTracker
    ) {
        super(parent);
        this.graph = graph;
        this.config = config;
        this.iteration = iteration;
        this.computation = computation;
        this.voteBits = voteBits;
        this.nodeBatch = nodeBatch;
        this.nodeValue = nodeValue;
        this.messenger = messenger;
        this.computeContext = new ComputeContext<>(this, config, progressTracker);
        this.sentMessage = sentMessage;
        this.progressTracker = progressTracker;
        this.initContext = new InitContext<>(this, config, graph, progressTracker);
    }

    @Override
    public void compute() {
        if (nodeBatch.nodeCount() >= SEQUENTIAL_THRESHOLD) {
            long startNode = nodeBatch.startNode();
            long batchSize = nodeBatch.nodeCount();
            boolean isEven = batchSize % 2 == 0;

            long pivot = BitUtil.ceilDiv(batchSize, 2);

            var rightBatch = isEven
                ? Partition.of(startNode + pivot, pivot)
                : Partition.of(startNode + pivot, pivot - 1);

            var leftBatch = Partition.of(startNode, pivot);

            var leftTask = new ForkJoinComputeStep<>(
                graph.concurrentCopy(),
                computation,
                config,
                iteration,
                leftBatch,
                nodeValue,
                messenger,
                voteBits,
                this,
                sentMessage,
                progressTracker
            );

            this.nodeBatch = rightBatch;

            addToPendingCount(1);
            leftTask.fork();

            this.compute();
        } else {
            computeBatch();
            this.sentMessage.set(hasSendMessage);
            tryComplete();
        }
    }

    @Override
    public Graph graph() {
        return graph;
    }

    @Override
    public HugeAtomicBitSet voteBits() {
        return voteBits;
    }

    @Override
    public PregelComputation<CONFIG> computation() {
        return computation;
    }

    @Override
    public NodeValue nodeValue() {
        return nodeValue;
    }

    @Override
    public Messenger<ITERATOR> messenger() {
        return messenger;
    }

    @Override
    public Partition nodeBatch() {
        return nodeBatch;
    }

    @Override
    public InitContext<CONFIG> initContext() {
        return initContext;
    }

    @Override
    public ComputeContext<CONFIG> computeContext() {
        return computeContext;
    }

    @Override
    public ProgressTracker progressTracker() {
        return progressTracker;
    }

    @Override
    public int iteration() {
        return iteration;
    }

    @Override
    public void sendTo(long targetNodeId, double message) {
        messenger.sendTo(targetNodeId, message);
        hasSendMessage = true;
    }
}
