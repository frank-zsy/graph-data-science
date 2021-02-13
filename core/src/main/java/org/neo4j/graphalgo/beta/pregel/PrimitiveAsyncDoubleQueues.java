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
package org.neo4j.graphalgo.beta.pregel;

import org.jetbrains.annotations.TestOnly;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.mem.MemoryUsage;
import org.neo4j.graphalgo.core.utils.paged.HugeAtomicLongArray;
import org.neo4j.graphalgo.core.utils.paged.HugeCursor;
import org.neo4j.graphalgo.core.utils.paged.HugeObjectArray;

import java.util.Arrays;

public final class PrimitiveAsyncDoubleQueues extends PrimitiveDoubleQueues {
    public static final double COMPACT_THRESHOLD = 0.25;
    private static final double EMPTY_MESSAGE = Double.NaN;

    private final HugeAtomicLongArray heads;
    private final HugeCursor<double[][]> queuesCursor;

    public static PrimitiveAsyncDoubleQueues of(long nodeCount, AllocationTracker tracker) {
        return of(nodeCount, MIN_CAPACITY, tracker);
    }

    public static PrimitiveAsyncDoubleQueues of(long nodeCount, int initialQueueCapacity, AllocationTracker tracker) {
        var heads = HugeAtomicLongArray.newArray(nodeCount, tracker);
        var tails = HugeAtomicLongArray.newArray(nodeCount, tracker);
        var queues = HugeObjectArray.newArray(double[].class, nodeCount, tracker);
        var referenceCounts = HugeAtomicLongArray.newArray(nodeCount, tracker);

        var capacity = Math.max(initialQueueCapacity, MIN_CAPACITY);
        queues.setAll(value -> {
            var queue = new double[capacity];
            Arrays.fill(queue, EMPTY_MESSAGE);
            return queue;
        });

        return new PrimitiveAsyncDoubleQueues(heads, tails, queues, referenceCounts);
    }

    public static MemoryEstimation memoryEstimation() {
        return MemoryEstimations.builder(PrimitiveAsyncDoubleQueues.class)
            .add("queues", HugeObjectArray.memoryEstimation(MemoryUsage.sizeOfDoubleArray(MIN_CAPACITY)))
            .perNode("heads", HugeAtomicLongArray::memoryEstimation)
            .perNode("tails", HugeAtomicLongArray::memoryEstimation)
            .perNode("reference counts", HugeAtomicLongArray::memoryEstimation)
            .build();
    }

    private PrimitiveAsyncDoubleQueues(
        HugeAtomicLongArray heads,
        HugeAtomicLongArray tails,
        HugeObjectArray<double[]> queues,
        HugeAtomicLongArray referenceCounts
    ) {
        super(queues, tails, referenceCounts);
        this.heads = heads;
        this.queuesCursor = queues.newCursor();
    }

    public void compact() {
        queues.initCursor(queuesCursor);

        while (queuesCursor.next()) {
            for (int i = queuesCursor.offset; i < queuesCursor.limit; i++) {
                var queue = queuesCursor.array[i];
                var tail = (int) tails.get(i);
                var head = (int) heads.get(i);

                if (isEmpty(queue, head, tail) && head > 0) {
                    // The queue is empty, we can reset head and tail to index 0
                    // but we need to fill the previous entries with NaN.
                    Arrays.fill(queue, 0, tail, EMPTY_MESSAGE);
                    heads.set(i, 0);
                    tails.set(i, 0);
                } else if (head > queue.length * COMPACT_THRESHOLD) {
                    // The queue is not empty, we need to move the entries for
                    // the next iteration to the beginning of the queue and fill
                    // the remaining entries with NaN.
                    var length = tail - head;
                    System.arraycopy(queue, head, queue, 0, length);
                    Arrays.fill(queue, length, queue.length, EMPTY_MESSAGE);

                    heads.set(i, 0);
                    tails.set(i, length);
                }
            }
        }
    }

    boolean isEmpty(long nodeId) {
        var head = (int) heads.get(nodeId);
        var tail = (int) tails.get(nodeId);
        var queue = queues.get(nodeId);
        return isEmpty(queue, head, tail);
    }

    private boolean isEmpty(double[] queue, int head, int tail) {
        return head == queue.length || head > tail || Double.isNaN(queue[head]);
    }

    double pop(long nodeId) {
        var currentHead = heads.getAndAdd(nodeId, 1);
        return queues.get(nodeId)[(int) currentHead];
    }

    @Override
    void grow(long nodeId, int minCapacity) {
        var queue = this.queues.get(nodeId);
        var capacity = queue.length;
        // grow by 50%
        var newCapacity = capacity + (capacity >> 1);
        var resizedArray = Arrays.copyOf(queue, newCapacity);
        // Fill with NaN to indicate empty slots.
        Arrays.fill(resizedArray, minCapacity - 1, newCapacity, EMPTY_MESSAGE);
        this.queues.set(nodeId, resizedArray);
    }

    void release() {
        super.release();
        this.heads.release();
    }

    @TestOnly
    long head(long nodeId) {
        return heads.get(nodeId);
    }

    public static class Iterator implements Messages.MessageIterator {

        private final PrimitiveAsyncDoubleQueues queues;

        private long nodeId;

        public Iterator(PrimitiveAsyncDoubleQueues queues) {this.queues = queues;}

        void init(long nodeId) {
            this.nodeId = nodeId;
        }

        @Override
        public boolean hasNext() {
            return !queues.isEmpty(nodeId);
        }

        @Override
        public double nextDouble() {
            return queues.pop(nodeId);
        }

        @Override
        public boolean isEmpty() {
            return queues.isEmpty(nodeId);
        }
    }
}
