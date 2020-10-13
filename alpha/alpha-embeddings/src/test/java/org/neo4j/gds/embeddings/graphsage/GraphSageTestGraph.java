/*
 * Copyright (c) 2017-2020 "Neo4j,"
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

public final class GraphSageTestGraph {

    public static final String GDL =
        "  (n0:Restaurant {dummyProp: 5.0, numEmployees: 2.0,   rating: 5.0})" +
        ", (n1:Restaurant {dummyProp: 5.0, numEmployees: 2.0,   rating: 5.0})" +
        ", (n2:Restaurant {dummyProp: 5.0, numEmployees: 2.0,   rating: 5.0})" +
        ", (n3:Restaurant {dummyProp: 5.0, numEmployees: 2.0,   rating: 5.0})" +
        ", (n4:Dish       {dummyProp: 5.0, numIngredients: 5.0, rating: 5.0})" +
        ", (n5:Dish       {dummyProp: 5.0, numIngredients: 5.0, rating: 5.0})" +
        ", (n6:Dish       {dummyProp: 5.0, numIngredients: 5.0, rating: 5.0})" +
        ", (n7:Dish       {dummyProp: 5.0, numIngredients: 5.0, rating: 5.0})" +
        ", (n8:Dish       {dummyProp: 5.0, numIngredients: 5.0, rating: 5.0})" +
        ", (n9:Dish       {dummyProp: 5.0, numIngredients: 5.0, rating: 5.0})" +
        ", (n10:Dish      {dummyProp: 5.0, numIngredients: 5.0, rating: 5.0})" +
        ", (n11:Dish      {dummyProp: 5.0, numIngredients: 5.0, rating: 5.0})" +
        ", (n12:Dish      {dummyProp: 5.0, numIngredients: 5.0, rating: 5.0})" +
        ", (n13:Customer  {dummyProp: 5.0, numPurchases: 5.0})" +
        ", (n14:Customer  {dummyProp: 5.0, numPurchases: 5.0})" +
        ", (n15:Customer  {dummyProp: 5.0, numPurchases: 5.0})" +
        ", (n16:Customer  {dummyProp: 5.0, numPurchases: 5.0})" +
        ", (n17:Customer  {dummyProp: 5.0, numPurchases: 5.0})" +
        ", (n18:Customer  {dummyProp: 5.0, numPurchases: 5.0})" +
        ", (n19:Customer  {dummyProp: 5.0, numPurchases: 5.0})" +

        ", (n0)-[:SERVES]->(n4)" +
        ", (n0)-[:SERVES]->(n5)" +
        ", (n0)-[:SERVES]->(n6)" +
        ", (n0)-[:SERVES]->(n7)" +
        ", (n0)-[:SERVES]->(n8)" +

        ", (n1)-[:SERVES]->(n5)" +
        ", (n1)-[:SERVES]->(n6)" +
        ", (n1)-[:SERVES]->(n8)" +
        ", (n1)-[:SERVES]->(n9)" +
        ", (n1)-[:SERVES]->(n10)" +
        ", (n1)-[:SERVES]->(n11)" +
        ", (n1)-[:SERVES]->(n12)" +

        ", (n2)-[:SERVES]->(n6)" +
        ", (n2)-[:SERVES]->(n8)" +
        ", (n2)-[:SERVES]->(n11)" +
        ", (n2)-[:SERVES]->(n12)" +

        ", (n3)-[:SERVES]->(n4)" +
        ", (n3)-[:SERVES]->(n5)" +
        ", (n3)-[:SERVES]->(n8)" +
        ", (n3)-[:SERVES]->(n10)" +

        ", (n13)-[:ORDERED]->(n4)" +
        ", (n13)-[:ORDERED]->(n5)" +

        ", (n14)-[:ORDERED]->(n4)" +
        ", (n14)-[:ORDERED]->(n10)" +
        ", (n14)-[:ORDERED]->(n12)" +

        ", (n15)-[:ORDERED]->(n9)" +
        ", (n15)-[:ORDERED]->(n12)" +

        ", (n16)-[:ORDERED]->(n7)" +
        ", (n16)-[:ORDERED]->(n8)" +
        ", (n16)-[:ORDERED]->(n10)" +
        ", (n16)-[:ORDERED]->(n12)" +

        ", (n17)-[:ORDERED]->(n4)" +
        ", (n17)-[:ORDERED]->(n5)" +
        ", (n17)-[:ORDERED]->(n9)" +

        ", (n18)-[:ORDERED]->(n5)" +
        ", (n18)-[:ORDERED]->(n6)" +
        ", (n18)-[:ORDERED]->(n11)" +

        ", (n19)-[:ORDERED]->(n7)" +
        ", (n19)-[:ORDERED]->(n11)";


    private GraphSageTestGraph() {}
}
