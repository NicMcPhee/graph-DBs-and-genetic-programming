graph = TitanFactory.open("db.properties");


//g = graph.traversal()

// nodeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_nodes_100.csv"
nodeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_nodes.csv"

println("We're in the script")
mgmt = graph.openManagement()
mgmt.makePropertyKey('uuid').dataType(String.class).cardinality(Cardinality.SINGLE).make()
mgmt.makePropertyKey('generation').dataType(Integer.class).cardinality(Cardinality.SINGLE).make()
mgmt.makePropertyKey('total_error').dataType(Float.class).cardinality(Cardinality.SINGLE).make()
mgmt.commit()

count = 0
new File(nodeCSV).splitEachLine(",") { fields ->
	println("Count = ${count}")
    ++count
    if (count > 1) { 
		graph.addVertex("run_uuid", fields[1], "uuid", fields[2], "generation", fields[3].toInteger(), 
				"location", fields[4].toInteger(), "total_error", fields[5].toFloat(), 
				"numSelections", fields[6].toInteger(), "numChildren", fields[7].toInteger())
	}
}

graph.tx().commit()

graph.tx().rollback()
//println("Graph open? "+ graph.isOpen())
mgmt = graph.openManagement()
//println("Graph open? open manage "+ graph.isOpen())
uuid = mgmt.getPropertyKey('uuid')
generation = mgmt.getPropertyKey('generation')
total_error = mgmt.getPropertyKey('total_error')
mgmt.buildIndex('uuidGenerationTotalError', Vertex.class).addKey(uuid).addKey(generation).addKey(total_error).buildMixedIndex("search")
println("About to commit")
mgmt.commit()
graph.tx().commit()

println("About to wait for index")
// Block until the SchemaStatus transitions from INSTALLED to REGISTERED
mgmt.awaitGraphIndexStatus(graph, 'uuidGenerationTotalError').status(SchemaStatus.REGISTERED).call()

// Reindex using TitanManagement
mgmt = graph.openManagement()
println("About to get graph the index")
i = mgmt.getGraphIndex('uuidGenerationTotalError')
println("About to update index")
mgmt.updateIndex(i, SchemaAction.REINDEX)
println("About to commit")
mgmt.commit()

// Enable the index
println("About to Enable the index")
mgmt.awaitGraphIndexStatus(graph, 'uuidGenerationTotalError').status(SchemaStatus.ENABLED).call()
//mgmt.awaitGraphIndexStatus(graph, 'uuidGenerationTotalError').call()
println("About to close the graph")
graph.close()

