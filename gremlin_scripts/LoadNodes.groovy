graph = TitanFactory.open("db.properties");


//g = graph.traversal()

nodeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_nodes.csv"

println("We're in the script")

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

println("# of nodes = " + graph.traversal().V().count())

/*

graph.tx().rollback()  //Never create new indexes while a transaction is active
mgmt = graph.openManagement()
//mgmt.set("index.search.elasticsearch.client-only", false)
//mgmt.set("index.search.elasticsearch.local-mode", true)

uuid = mgmt.makePropertyKey('uuid').dataType(String.class).cardinality(Cardinality.SINGLE).make()
generation = mgmt.makePropertyKey('generation').dataType(Integer.class).cardinality(Cardinality.SINGLE).make()
total_error = mgmt.makePropertyKey('total_error').dataType(Float.class).cardinality(Cardinality.SINGLE).make()
mgmt.buildIndex('uuidGenerationTotalError', Vertex.class).addKey(uuid).addKey(generation).addKey(total_error).buildMixedIndex("search")
println("About to commit")
mgmt.commit()
//Wait for the index to become available
graph = TitanFactory.open("db.properties")
mgmt = graph.openManagement()
println("About to wait for index")
mgmt.awaitGraphIndexStatus(graph, 'uuidGenerationTotalError').call()
//Reindex the existing data
println("About to re-open mgmt")
mgmt = graph.openManagement()
println("About to update index")
mgmt.updateIndex(mgmt.getGraphIndex("uuidGenerationTotalError"), SchemaAction.REINDEX).get()
mgmt.commit()

*/
