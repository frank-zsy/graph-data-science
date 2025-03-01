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
package org.neo4j.gds.core.loading;

import org.immutables.builder.Builder;
import org.neo4j.gds.api.GraphLoaderContext;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.config.GraphProjectFromStoreConfig;
import org.neo4j.gds.core.GraphDimensions;
import org.neo4j.gds.core.loading.SingleTypeRelationshipImporter.SingleTypeRelationshipImportContext;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.List;
import java.util.stream.Collectors;


public final class ScanningRelationshipsImporter extends ScanningRecordsImporter<RelationshipReference, RelationshipsAndProperties> {

    private final GraphProjectFromStoreConfig graphProjectConfig;
    private final GraphLoaderContext loadingContext;

    private final IdMap idMap;
    private List<SingleTypeRelationshipImportContext> importContexts;

    @Builder.Factory
    static ScanningRelationshipsImporter scanningRelationshipsImporter(
        GraphProjectFromStoreConfig graphProjectConfig,
        GraphLoaderContext loadingContext,
        GraphDimensions dimensions,
        ProgressTracker progressTracker,
        IdMap idMap,
        int concurrency
    ) {
        return new ScanningRelationshipsImporter(
            graphProjectConfig,
            loadingContext,
            dimensions,
            progressTracker,
            idMap,
            concurrency
        );
    }

    private ScanningRelationshipsImporter(
        GraphProjectFromStoreConfig graphProjectConfig,
        GraphLoaderContext loadingContext,
        GraphDimensions dimensions,
        ProgressTracker progressTracker,
        IdMap idMap,
        int concurrency
    ) {
        super(
            RelationshipScanCursorBasedScanner.FACTORY,
            loadingContext,
            dimensions,
            progressTracker,
            concurrency
        );
        this.graphProjectConfig = graphProjectConfig;
        this.loadingContext = loadingContext;
        this.idMap = idMap;
    }

    @Override
    public RecordScannerTaskRunner.RecordScannerTaskFactory recordScannerTaskFactory(
        long nodeCount,
        ImportSizing sizing,
        StoreScanner<RelationshipReference> storeScanner
    ) {
        importContexts = graphProjectConfig
            .relationshipProjections()
            .projections()
            .entrySet()
            .stream()
            .map(
                entry -> {
                    var relationshipType = entry.getKey();
                    var projection = entry.getValue();

                    var importMetaData = SingleTypeRelationshipImporter.ImportMetaData.of(
                        projection,
                        dimensions.relationshipTypeTokenMapping().get(relationshipType),
                        dimensions.relationshipPropertyTokens()
                    );

                    var importerFactory = new SingleTypeRelationshipImporterBuilder()
                        .importMetaData(importMetaData)
                        .nodeCountSupplier(dimensions::nodeCount)
                        .importSizing(sizing)
                        .validateRelationships(graphProjectConfig.validateRelationships())
                        .build();

                    return ImmutableSingleTypeRelationshipImportContext.builder()
                        .relationshipType(relationshipType)
                        .relationshipProjection(projection)
                        .singleTypeRelationshipImporter(importerFactory)
                        .build();
                }
            ).collect(Collectors.toList());

        return RelationshipsScannerTask.factory(
            loadingContext,
            progressTracker,
            idMap,
            storeScanner,
            importContexts
                .stream()
                .map(SingleTypeRelationshipImportContext::singleTypeRelationshipImporter)
                .collect(Collectors.toList())
        );
    }

    @Override
    public RelationshipsAndProperties build() {
        return RelationshipsAndProperties.of(importContexts);
    }
}
