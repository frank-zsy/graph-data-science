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
package org.neo4j.gds.ml.decisiontree;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.TestSupport;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.core.utils.paged.ReadOnlyHugeLongArray;
import org.neo4j.gds.ml.core.subgraph.LocalIdMap;
import org.neo4j.gds.ml.models.Features;
import org.neo4j.gds.ml.models.FeaturesFactory;

import java.util.SplittableRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionTreeClassifierTest {

    private static final long NUM_SAMPLES = 10;
    private static final LocalIdMap CLASS_MAPPING = LocalIdMap.of(1337, 42);

    private final HugeLongArray allLabels = HugeLongArray.newArray(NUM_SAMPLES);
    private Features features;
    private GiniIndex giniIndexLoss;

    @BeforeEach
    void setup() {
        allLabels.setAll(idx -> idx >= 5 ? 42 : 1337);

        HugeObjectArray<double[]> featureVectorArray = HugeObjectArray.newArray(
            double[].class,
            NUM_SAMPLES
        );

        // Class 1337 feature vectors.
        featureVectorArray.set(0, new double[]{2.771244718, 1.784783929});
        featureVectorArray.set(1, new double[]{1.728571309, 1.169761413});
        featureVectorArray.set(2, new double[]{3.678319846, 3.31281357});
        featureVectorArray.set(3, new double[]{6.961043357, 2.61995032});
        featureVectorArray.set(4, new double[]{6.999208922, 2.209014212});

        // Class 42 feature vectors.
        featureVectorArray.set(5, new double[]{7.497545867, 3.162953546});
        featureVectorArray.set(6, new double[]{9.00220326, 3.339047188});
        featureVectorArray.set(7, new double[]{7.444542326, 0.476683375});
        featureVectorArray.set(8, new double[]{10.12493903, 3.234550982});
        featureVectorArray.set(9, new double[]{6.642287351, 3.319983761});

        features = FeaturesFactory.wrap(featureVectorArray);

        giniIndexLoss = GiniIndex.fromOriginalLabels(allLabels, CLASS_MAPPING);

    }

    private static Stream<Arguments> predictionWithoutSamplingParameters() {
        return TestSupport.crossArguments(
            () -> Stream.of(
                Arguments.of(new double[]{8.0, 0.0}, 42, 1),
                Arguments.of(new double[]{3.0, 0.0}, 1337, 1),
                Arguments.of(new double[]{0.0, 4.0}, 42, 2),
                Arguments.of(new double[]{3.0, 0.0}, 1337, 100),
                Arguments.of(new double[]{0.0, 4.0}, 42, 100)
            ),
            () -> Stream.of(Arguments.of(2), Arguments.of(4))
        );
    }

    @ParameterizedTest
    @MethodSource("predictionWithoutSamplingParameters")
    void shouldMakeSanePrediction(
        double[] featureVector,
        long expectedPrediction,
        int maxDepth,
        int minSplitSize
    ) {
        var decisionTree = new DecisionTreeClassifierTrainer<>(
            giniIndexLoss,
            features,
            allLabels,
            CLASS_MAPPING,
            DecisionTreeTrainerConfigImpl.builder()
                .maxDepth(maxDepth)
                .minSplitSize(minSplitSize)
                .build(),
            new FeatureBagger(new SplittableRandom(), featureVector.length, 1)
        );

        HugeLongArray mutableFeatureVectors = HugeLongArray.newArray(features.size());
        mutableFeatureVectors.setAll(idx -> idx);
        var featureVectors = ReadOnlyHugeLongArray.of(mutableFeatureVectors);


        var decisionTreePredict = decisionTree.train(featureVectors);

        assertThat(decisionTreePredict.predict(featureVector)).isEqualTo(CLASS_MAPPING.toMapped(expectedPrediction));
    }

    @Test
    void indexSamplingShouldWork() {
        var decisionTreeTrainConfig = DecisionTreeTrainerConfigImpl.builder()
            .maxDepth(1)
            .minSplitSize(2)
            .build();

        HugeLongArray mutableFeatureVectors = HugeLongArray.newArray(features.size());
        mutableFeatureVectors.setAll(idx -> idx);
        var featureVectors = ReadOnlyHugeLongArray.of(mutableFeatureVectors);

        var decisionTree = new DecisionTreeClassifierTrainer<>(
            giniIndexLoss,
            features,
            allLabels,
            CLASS_MAPPING,
            decisionTreeTrainConfig,
            new FeatureBagger(new SplittableRandom(-6938002729576536314L), features.get(0).length, 0.5D)
        );

        var featureVector = new double[]{8.0, 0.0};

        var decisionTreePredict = decisionTree.train(featureVectors);
        assertThat(decisionTreePredict.predict(featureVector)).isEqualTo(CLASS_MAPPING.toMapped(42));

        decisionTree = new DecisionTreeClassifierTrainer<>(
            giniIndexLoss,
            features,
            allLabels,
            CLASS_MAPPING,
            decisionTreeTrainConfig,
            new FeatureBagger(new SplittableRandom(1337L), featureVector.length, 0.5D) // Only one feature is used.
        );

        decisionTreePredict = decisionTree.train(featureVectors);
        assertThat(decisionTreePredict.predict(featureVector)).isEqualTo(CLASS_MAPPING.toMapped(1337));
    }

    @Test
    void considersSampledVectors() {
        var featureVector = new double[]{8.0, 0.0};

        var decisionTreeTrainConfig = DecisionTreeTrainerConfigImpl.builder()
            .maxDepth(1)
            .minSplitSize(2)
            .build();

        var mutableSampledVectors = HugeLongArray.newArray(1);
        mutableSampledVectors.set(0, 1);
        var sampledVectors = ReadOnlyHugeLongArray.of(mutableSampledVectors);

        var decisionTree = new DecisionTreeClassifierTrainer<>(
            giniIndexLoss,
            features,
            allLabels,
            CLASS_MAPPING,
            decisionTreeTrainConfig,
            new FeatureBagger(new SplittableRandom(5677377167946646799L), featureVector.length, 1)
        );

        var decisionTreePredict = decisionTree.train(sampledVectors);
        assertThat(decisionTreePredict.predict(featureVector)).isEqualTo(CLASS_MAPPING.toMapped(1337L));

        var mutableOtherSampledVectors = HugeLongArray.newArray(1);
        mutableOtherSampledVectors.set(0, features.size() - 1);
        var otherSampledVectors = ReadOnlyHugeLongArray.of(mutableOtherSampledVectors);

        decisionTree = new DecisionTreeClassifierTrainer<>(
            giniIndexLoss,
            features,
            allLabels,
            CLASS_MAPPING,
            decisionTreeTrainConfig,
            new FeatureBagger(new SplittableRandom(321328L), featureVector.length, 1)
        );

        decisionTreePredict = decisionTree.train(otherSampledVectors);
        assertThat(decisionTreePredict.predict(featureVector)).isEqualTo(CLASS_MAPPING.toMapped(42L));
    }

    @ParameterizedTest
    @CsvSource(value = {
        // When maxDepth is limiting tree size minSplitSize does not matter.
        "     6, 100_000,  2, 56,     5_096",
        "     6, 100_000,  4, 56,     5_096",
        // Scales with maxDepth when maxDepth is limiting tree size.
        "    10, 100_000,  2, 56,    81_896",
        // maxDepth does not matter for small training sets.
        "   100,     500,  2, 56,    39_976",
        " 8_000,     500,  2, 56,    39_976",
        // Max should scale almost inverse linearly with minSplitSize.
        " 8_000, 100_000,  2, 56, 79_99_976",
        " 8_000, 100_000, 20, 56,   799_976"
    })
    void estimateDecisionTree(
        int maxDepth,
        long numberOfTrainingSamples,
        int minSplitSize,
        long expectedMin,
        long expectedMax
    ) {
        var range = DecisionTreeTrainer.estimateTree(maxDepth, numberOfTrainingSamples, minSplitSize);

        assertThat(range.min).isEqualTo(expectedMin);
        assertThat(range.max).isEqualTo(expectedMax);
    }

    @ParameterizedTest
    @CsvSource(value = {
        // Scales with training set size even if maxDepth limits tree size.
        "  6,  1_000,  32_456,    46_208",
        "  6, 10_000, 320_456,   406_208",
        // Scales with maxDepth when maxDepth is limiting tree size.
        " 20, 10_000, 320_456, 1_202_912",
    })
    void trainMemoryEstimation(int maxDepth, long numberOfTrainingSamples, long expectedMin, long expectedMax) {
        var range = DecisionTreeClassifierTrainer.memoryEstimation(maxDepth, 2, numberOfTrainingSamples, 10, 10);

        assertThat(range.min).isEqualTo(expectedMin);
        assertThat(range.max).isEqualTo(expectedMax);
    }
}
