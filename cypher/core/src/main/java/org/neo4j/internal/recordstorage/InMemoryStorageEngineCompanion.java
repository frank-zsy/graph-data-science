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
package org.neo4j.internal.recordstorage;

import org.neo4j.gds.compat.StorageEngineProxy;
import org.neo4j.gds.storageengine.InMemoryTransactionStateVisitor;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.storageengine.api.StorageEngine;
import org.neo4j.token.TokenHolders;

public final class InMemoryStorageEngineCompanion {

    private InMemoryStorageEngineCompanion() {}

    public static StorageEngine create(
        DatabaseLayout databaseLayout,
        TokenHolders tokenHolders,
        InMemoryMetaDataProvider metadataProvider
    ) {
        var storageEngineBuilder = StorageEngineProxy.inMemoryStorageEngineBuilder(
            databaseLayout,
            tokenHolders,
            metadataProvider
        );

        storageEngineBuilder.withCountsStoreFn(StorageEngineProxy::inMemoryCountsStore);
        storageEngineBuilder.withTxStateVisitorFn(InMemoryTransactionStateVisitor::new);
        storageEngineBuilder.withCommandCreationContextSupplier(StorageEngineProxy::inMemoryCommandCreationContext);
        storageEngineBuilder.withStorageReaderFn(StorageEngineProxy::inMemoryStorageReader);

        return storageEngineBuilder.build();
    }
}
