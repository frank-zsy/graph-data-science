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
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.AdjacencyProperties;
import org.neo4j.gds.api.CSRGraph;
import org.neo4j.gds.api.CompositeRelationshipIterator;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.api.NodeProperty;
import org.neo4j.gds.api.NodePropertyStore;
import org.neo4j.gds.api.PropertyState;
import org.neo4j.gds.api.RelationshipProperty;
import org.neo4j.gds.api.RelationshipPropertyStore;
import org.neo4j.gds.api.Relationships;
import org.neo4j.gds.api.ValueTypes;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.api.schema.GraphSchema;
import org.neo4j.gds.api.schema.NodeSchema;
import org.neo4j.gds.api.schema.PropertySchema;
import org.neo4j.gds.api.schema.RelationshipPropertySchema;
import org.neo4j.gds.api.schema.RelationshipSchema;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.ProcedureConstants;
import org.neo4j.gds.core.huge.CSRCompositeRelationshipIterator;
import org.neo4j.gds.core.huge.HugeGraph;
import org.neo4j.gds.core.huge.NodeFilteredGraph;
import org.neo4j.gds.core.huge.UnionGraph;
import org.neo4j.gds.core.loading.construction.GraphFactory;
import org.neo4j.gds.core.utils.TimeUtil;
import org.neo4j.gds.utils.ExceptionUtil;
import org.neo4j.gds.utils.StringJoining;
import org.neo4j.kernel.database.NamedDatabaseId;
import org.neo4j.values.storable.NumberType;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.neo4j.gds.core.StringSimilarity.prettySuggestions;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

@Value.Style(typeBuilder = "GraphStoreBuilder")
public class CSRGraphStore implements GraphStore {

    private final int concurrency;

    private final NamedDatabaseId databaseId;

    private final IdMap nodes;

    protected final Map<RelationshipType, Relationships.Topology> relationships;

    private final Map<RelationshipType, RelationshipPropertyStore> relationshipProperties;

    private final Set<Graph> createdGraphs;

    private GraphSchema schema;

    private NodePropertyStore nodeProperties;

    private ZonedDateTime modificationTime;

    @Builder.Factory
    public static CSRGraphStore of(
        NamedDatabaseId databaseId,
        GraphSchema schema,
        IdMap nodes,
        @Nullable NodePropertyStore nodePropertyStore,
        Map<RelationshipType, Relationships.Topology> relationships,
        Map<RelationshipType, RelationshipPropertyStore> relationshipPropertyStores,
        int concurrency
    ) {
        // A graph store must contain at least one topology, even if it is empty.
        var topologies = relationships.isEmpty()
            ? Map.of(RelationshipType.ALL_RELATIONSHIPS, GraphFactory.emptyRelationships(nodes).topology())
            : relationships;

        return new CSRGraphStore(
            databaseId,
            schema,
            nodes,
            nodePropertyStore == null ? NodePropertyStore.empty() : nodePropertyStore,
            topologies,
            relationshipPropertyStores,
            concurrency
        );
    }

    protected CSRGraphStore(
        NamedDatabaseId databaseId,
        GraphSchema schema,
        IdMap nodes,
        NodePropertyStore nodeProperties,
        Map<RelationshipType, Relationships.Topology> relationships,
        Map<RelationshipType, RelationshipPropertyStore> relationshipProperties,
        int concurrency
    ) {
        this.databaseId = databaseId;

        this.schema = schema;

        this.nodes = nodes;
        this.nodeProperties = nodeProperties;

        this.relationships = new HashMap<>(relationships);
        this.relationshipProperties = new HashMap<>(relationshipProperties);

        this.concurrency = concurrency;
        this.createdGraphs = new HashSet<>();
        this.modificationTime = TimeUtil.now();
    }

    @Override
    public NamedDatabaseId databaseId() {
        return databaseId;
    }

    @Override
    public GraphSchema schema() {
        return schema;
    }

    @Override
    public ZonedDateTime modificationTime() {
        return modificationTime;
    }

    @Override
    public IdMap nodes() {
        return this.nodes;
    }

    @Override
    public Set<NodeLabel> nodeLabels() {
        return nodes.availableNodeLabels();
    }

    @Override
    public Set<String> nodePropertyKeys(NodeLabel label) {
        return schema().nodeSchema().properties().getOrDefault(label, Map.of()).keySet();
    }

    @Override
    public Set<String> nodePropertyKeys() {
        return nodeProperties.keySet();
    }

    @Override
    public boolean hasNodeProperty(String propertyKey) {
        return nodeProperties.containsKey(propertyKey);
    }

    @Override
    public boolean hasNodeProperty(NodeLabel label, String propertyKey) {
        return schema().nodeSchema().hasProperty(label, propertyKey);
    }

    @Override
    public boolean hasNodeProperty(Collection<NodeLabel> labels, String propertyKey) {
        return labels
            .stream()
            .allMatch(label -> hasNodeProperty(label, propertyKey));
    }

    @Override
    public void addNodeProperty(
        Set<NodeLabel> labels,
        String propertyKey,
        NodeProperties propertyValues
    ) {
        updateGraphStore((graphStore) -> {
            if (graphStore.hasNodeProperty(propertyKey)) {
                throw new UnsupportedOperationException(formatWithLocale("Node property %s already exists", propertyKey));
            }

            graphStore.nodeProperties = NodePropertyStore.builder()
                .from(graphStore.nodeProperties)
                .putIfAbsent(propertyKey, NodeProperty.of(propertyKey, PropertyState.TRANSIENT, propertyValues))
                .build();

            NodeSchema.Builder schemaBuilder = NodeSchema
                .builder()
                .from(schema().nodeSchema());

            labels.forEach(label -> schemaBuilder.addProperty(
                label,
                propertyKey,
                PropertySchema.of(propertyKey,
                    propertyValues.valueType(),
                    propertyValues.valueType().fallbackValue(),
                    PropertyState.TRANSIENT
                )
            ));

            this.schema = GraphSchema.of(
                schemaBuilder.build(),
                schema().relationshipSchema()
            );
        });
    }

    @Override
    public void removeNodeProperty(String propertyKey) {
        updateGraphStore(graphStore -> {
            graphStore.nodeProperties = NodePropertyStore.builder()
                .from(graphStore.nodeProperties)
                .removeProperty(propertyKey)
                .build();

            NodeSchema.Builder nodeSchemaBuilder = NodeSchema
                .builder()
                .from(schema().nodeSchema())
                .removeProperty(propertyKey);

            this.schema = GraphSchema.of(
                nodeSchemaBuilder.build(),
                schema().relationshipSchema()
            );
        });
    }

    @Override
    public NodeProperty nodeProperty(String propertyKey) {
        return this.nodeProperties.get(propertyKey);
    }

    @Override
    public ValueType nodePropertyType(String propertyKey) {
        return nodeProperty(propertyKey).valueType();
    }

    @Override
    public PropertyState nodePropertyState(String propertyKey) {
        return nodeProperty(propertyKey).propertyState();
    }

    @Override
    public NodeProperties nodePropertyValues(String propertyKey) {
        return nodeProperty(propertyKey).values();
    }

    @Override
    public Set<RelationshipType> relationshipTypes() {
        return relationships.keySet();
    }

    @Override
    public boolean hasRelationshipType(RelationshipType relationshipType) {
        return relationships.containsKey(relationshipType);
    }

    @Override
    public long relationshipCount() {
        long sum = 0L;
        for (var topology : relationships.values()) {
            long elementCount = topology.elementCount();
            sum += elementCount;
        }
        return sum;
    }

    @Override
    public long relationshipCount(RelationshipType relationshipType) {
        return relationships.get(relationshipType).elementCount();
    }

    @Override
    public boolean hasRelationshipProperty(RelationshipType relType, String propertyKey) {
        return relationshipProperties.containsKey(relType) && relationshipProperties
            .get(relType)
            .containsKey(propertyKey);
    }

    @Override
    public ValueType relationshipPropertyType(String propertyKey) {
        return relationshipProperties.values().stream()
            .filter(propertyStore -> propertyStore.containsKey(propertyKey))
            .map(propertyStore -> propertyStore.get(propertyKey).valueType())
            .findFirst()
            .orElse(ValueType.UNKNOWN);
    }

    @Override
    public Set<String> relationshipPropertyKeys() {
        return relationshipProperties
            .values()
            .stream()
            .flatMap(relationshipPropertyStore -> relationshipPropertyStore.keySet().stream())
            .collect(Collectors.toSet());
    }

    @Override
    public Set<String> relationshipPropertyKeys(RelationshipType relationshipType) {
        return relationshipProperties.getOrDefault(relationshipType, RelationshipPropertyStore.empty()).keySet();
    }

    @Override
    public RelationshipProperty relationshipPropertyValues(
        RelationshipType relationshipType, String propertyKey
    ) {
        return relationshipProperties.getOrDefault(relationshipType, RelationshipPropertyStore.empty()).get(propertyKey);
    }

    @Override
    public void addRelationshipType(
        RelationshipType relationshipType,
        Optional<String> relationshipPropertyKey,
        Optional<NumberType> relationshipPropertyType,
        Relationships relationships
    ) {
        updateGraphStore(graphStore -> {
            if (!hasRelationshipType(relationshipType)) {
                RelationshipSchema.Builder relationshipSchemaBuilder = RelationshipSchema
                    .builder()
                    .from(schema().relationshipSchema());

                graphStore.relationships.put(relationshipType, relationships.topology());
                relationshipSchemaBuilder.addRelationshipType(relationshipType);

                if (relationshipPropertyKey.isPresent()
                    && relationshipPropertyType.isPresent()
                    && relationships.properties().isPresent()) {
                    addRelationshipProperty(
                        relationshipType,
                        relationshipPropertyKey.get(),
                        relationshipPropertyType.get(),
                        relationships.properties().get(),
                        graphStore
                    );

                    ValueType valueType = ValueTypes.fromNumberType(relationshipPropertyType.get());
                    relationshipSchemaBuilder.addProperty(
                        relationshipType,
                        relationshipPropertyKey.get(),
                        RelationshipPropertySchema.of(
                            relationshipPropertyKey.get(),
                            valueType,
                            valueType.fallbackValue(),
                            PropertyState.TRANSIENT,
                            Aggregation.NONE
                        ));
                }

                this.schema = GraphSchema.of(
                    schema().nodeSchema(),
                    relationshipSchemaBuilder.build()
                );
            }
        });
    }

    @Override
    public DeletionResult deleteRelationships(RelationshipType relationshipType) {
        return DeletionResult.of(builder ->
            updateGraphStore(graphStore -> {
                var removedTopology = graphStore.relationships.remove(relationshipType);
                if (removedTopology != null) {
                    builder.deletedRelationships(removedTopology.elementCount());
                }

                var removedProperties = graphStore.relationshipProperties.remove(relationshipType);

                if (removedProperties != null) {
                    removedProperties
                        .relationshipProperties()
                        .values()
                        .forEach(property -> builder.putDeletedProperty(
                            property.key(),
                            property.values().elementCount()
                        ));
                }

                var relationshipSchema = RelationshipSchema
                    .builder()
                    .from(schema().relationshipSchema())
                    .removeRelationshipType(relationshipType)
                    .build();

                this.schema = GraphSchema.of(
                    schema().nodeSchema(),
                    relationshipSchema
                );
            })
        );
    }

    @Override
    public CSRGraph getGraph(
        Collection<NodeLabel> nodeLabels,
        Collection<RelationshipType> relationshipTypes,
        Optional<String> maybeRelationshipProperty
    ) {
        validateInput(relationshipTypes, maybeRelationshipProperty);
        return createGraph(nodeLabels, relationshipTypes, maybeRelationshipProperty);
    }

    @Override
    public CSRGraph getUnion() {
        var graphs = relationships
            .keySet()
            .stream()
            .flatMap(relationshipType -> {
                if (relationshipProperties.containsKey(relationshipType)
                    && !relationshipProperties.get(relationshipType).isEmpty()) {
                    return relationshipProperties
                        .get(relationshipType)
                        .keySet()
                        .stream()
                        .map(propertyKey -> createGraph(nodeLabels(), relationshipType, Optional.of(propertyKey)));
                } else {
                    return Stream.of(createGraph(nodeLabels(), relationshipType, Optional.empty()));
                }
            })
            .collect(Collectors.toList());

        return UnionGraph.of(graphs);
    }

    @Override
    public void canRelease(boolean canRelease) {
        createdGraphs.forEach(graph -> graph.canRelease(canRelease));
    }

    @Override
    public CompositeRelationshipIterator getCompositeRelationshipIterator(
        RelationshipType relationshipType, List<String> propertyKeys
    ) {
        if (!relationshipTypes().contains(relationshipType)) {
            throw new IllegalArgumentException(
                prettySuggestions(
                    formatWithLocale(
                        "Unknown relationship type `%s`.",
                        relationshipType
                    ),
                    relationshipType.name(),
                    relationshipTypes().stream().map(RelationshipType::name).collect(Collectors.toSet())
                )
            );
        }

        var availableProperties = relationshipPropertyKeys(relationshipType);
        if (!availableProperties.containsAll(propertyKeys)) {
            var missingPropertyKeys = propertyKeys
                .stream()
                .filter(propertyKey -> !availableProperties.contains(propertyKey))
                .collect(Collectors.toList());
            throw new IllegalArgumentException(formatWithLocale(
                "Missing property keys %s for relationship type %s. Available property keys are %s",
                StringJoining.join(missingPropertyKeys),
                relationshipType.name,
                StringJoining.join(availableProperties)
            ));
        }

        var adjacencyList = relationships.get(relationshipType).adjacencyList();

        var relationshipPropertyStore = relationshipProperties.get(relationshipType);
        var properties = propertyKeys.isEmpty()
            ? CSRCompositeRelationshipIterator.EMPTY_PROPERTIES
            : propertyKeys
                .stream()
                .map(relationshipPropertyStore::get)
                .map(RelationshipProperty::values)
                .map(Relationships.Properties::propertiesList)
                .toArray(AdjacencyProperties[]::new);

        return new CSRCompositeRelationshipIterator(
            adjacencyList,
            propertyKeys.toArray(new String[0]),
            properties
        );
    }

    @Override
    public void release() {
        createdGraphs.forEach(Graph::release);
        releaseInternals();
    }

    private void releaseInternals() {
        var closeables = Stream.<AutoCloseable>builder();
        if (this.nodes instanceof AutoCloseable) {
            closeables.accept((AutoCloseable) this.nodes);
        }
        this.relationships.values().forEach(rel -> closeables.add(rel.adjacencyList()));
        this.relationshipProperties.forEach((propertyName, properties) ->
            properties.values().forEach(prop -> closeables.add(prop.values().propertiesList()))
        );

        var errorWhileClosing = closeables.build().distinct().flatMap(closeable -> {
            try {
                closeable.close();
                return Stream.empty();
            } catch (Exception e) {
                return Stream.of(e);
            }
        }).reduce(null, ExceptionUtil::chain);
        if (errorWhileClosing != null) {
            ExceptionUtil.throwIfUnchecked(errorWhileClosing);
            throw new RuntimeException(errorWhileClosing);
        }
    }

    @Override
    public long nodeCount() {
        return nodes.nodeCount();
    }

    private synchronized void updateGraphStore(Consumer<CSRGraphStore> updateFunction) {
        updateFunction.accept(this);
        this.modificationTime = TimeUtil.now();
    }

    private void addRelationshipProperty(
        RelationshipType relationshipType,
        String propertyKey,
        NumberType propertyType,
        Relationships.Properties properties,
        CSRGraphStore graphStore
    ) {
        graphStore.relationshipProperties.compute(relationshipType, (relType, propertyStore) -> {
            RelationshipPropertyStore.Builder builder = RelationshipPropertyStore.builder();
            if (propertyStore != null) {
                builder.from(propertyStore);
            }
            return builder.putIfAbsent(
                propertyKey,
                RelationshipProperty.of(
                    propertyKey,
                    propertyType,
                    PropertyState.TRANSIENT,
                    properties,
                    ValueTypes.fromNumberType(propertyType).fallbackValue(),
                    Aggregation.NONE
                )
            ).build();
        });
    }

    private CSRGraph createGraph(
        Collection<NodeLabel> nodeLabels,
        RelationshipType relationshipType,
        Optional<String> maybeRelationshipProperty
    ) {
        Optional<IdMap> filteredNodes = getFilteredIdMap(nodeLabels);
        Map<String, NodeProperties> filteredNodeProperties = filterNodeProperties(nodeLabels);
        return createGraphFromRelationshipType(
            filteredNodes,
            filteredNodeProperties,
            relationshipType,
            maybeRelationshipProperty
        );
    }

    private CSRGraph createGraph(
        Collection<NodeLabel> filteredLabels,
        Collection<RelationshipType> relationshipTypes,
        Optional<String> maybeRelationshipProperty
    ) {
        Optional<IdMap> filteredNodes = getFilteredIdMap(filteredLabels);
        Map<String, NodeProperties> filteredNodeProperties = filterNodeProperties(filteredLabels);

        List<CSRGraph> filteredGraphs = relationships.keySet().stream()
            .filter(relationshipTypes::contains)
            .map(relationshipType -> createGraphFromRelationshipType(
                filteredNodes,
                filteredNodeProperties,
                relationshipType,
                maybeRelationshipProperty
            ))
            .collect(Collectors.toList());

        filteredGraphs.forEach(graph -> graph.canRelease(false));
        createdGraphs.addAll(filteredGraphs);
        return UnionGraph.of(filteredGraphs);
    }

    @NotNull
    private Optional<IdMap> getFilteredIdMap(Collection<NodeLabel> filteredLabels) {
        boolean loadAllNodes = filteredLabels.containsAll(nodeLabels());

        return loadAllNodes || schema().nodeSchema().containsOnlyAllNodesLabel()
            ? Optional.empty()
            : Optional.of(nodes.withFilteredLabels(filteredLabels, concurrency));
    }

    private CSRGraph createGraphFromRelationshipType(
        Optional<IdMap> filteredNodes,
        Map<String, NodeProperties> filteredNodeProperties,
        RelationshipType relationshipType,
        Optional<String> maybeRelationshipProperty
    ) {
        var graphSchema = GraphSchema.of(
            schema().nodeSchema(),
            schema()
                .relationshipSchema()
                .singleTypeAndProperty(relationshipType, maybeRelationshipProperty)
        );

        var topology = relationships.get(relationshipType);
        var properties = maybeRelationshipProperty.map(propertyKey -> relationshipProperties
            .get(relationshipType)
            .get(propertyKey).values());

        var initialGraph = HugeGraph.create(
            nodes,
            graphSchema,
            filteredNodeProperties,
            topology,
            properties
        );

        return filteredNodes.isPresent()
            ? new NodeFilteredGraph(initialGraph, filteredNodes.get())
            : initialGraph;
    }

    private Map<String, NodeProperties> filterNodeProperties(Collection<NodeLabel> labels) {
        if (this.nodeProperties.isEmpty()) {
            return Collections.emptyMap();
        }
        if (labels.size() == 1 || schema().nodeSchema().containsOnlyAllNodesLabel()) {
            return this.nodeProperties.nodePropertyValues();
        }


        return schema().nodeSchema().filter(new HashSet<>(labels))
            .allProperties()
            .stream()
            .collect(toMap(
                Function.identity(),
                this::nodePropertyValues
            ));
    }

    private void validateInput(
        Collection<RelationshipType> relationshipTypes,
        Optional<String> maybeRelationshipProperty
    ) {
        if (relationshipTypes.isEmpty()) {
            throw new IllegalArgumentException(formatWithLocale(
                "The parameter '%s' should not be empty. Use '*' to load all relationship types.",
                ProcedureConstants.RELATIONSHIP_TYPES
            ));
        }

        relationshipTypes.forEach(relationshipType -> {
            if (!relationships.containsKey(relationshipType)) {
                throw new IllegalArgumentException(formatWithLocale(
                    "No relationships have been loaded for relationship type '%s'",
                    relationshipType
                ));
            }

            maybeRelationshipProperty.ifPresent(relationshipProperty -> {
                if (!hasRelationshipProperty(relationshipType, relationshipProperty)) {
                    throw new IllegalArgumentException(formatWithLocale(
                        "Property '%s' does not exist for relationships with type '%s'.",
                        maybeRelationshipProperty.get(),
                        relationshipType
                    ));
                }
            });
        });
    }

}
