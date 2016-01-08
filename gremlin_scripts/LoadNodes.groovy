//nodeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_nodes_100.csv"
//nodeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_nodes2000.csv"
//nodeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_nodes.csv"
//storage.batch-loading=true //for batch graph, use in properties
graph = TitanFactory.open('./db.properties')

mgmt = graph.openManagement()
run_uuid = mgmt.makePropertyKey("run_uuid").dataType(String.class).make()
uuid = mgmt.makePropertyKey("uuid").dataType(String.class).make()		
generation = mgmt.makePropertyKey("generation").dataType(Integer.class).make()
location = mgmt.makePropertyKey("location").dataType(Integer.class).make()
total_error = mgmt.makePropertyKey("total_error").dataType(Float.class).make()
numSelections = mgmt.makePropertyKey("numSelections").dataType(Integer.class).make()
numChildren = mgmt.makePropertyKey("numChildren").dataType(Integer.class).make()
uuidIndex = mgmt.buildIndex("uuidIndex",  Vertex.class).addKey(uuid).unique().buildCompositeIndex()
uuidGenerationTotalError = mgmt.buildIndex('uuidGenerationTotalError', Vertex.class).addKey(uuid).addKey(generation).addKey(total_error).buildMixedIndex("search")
mgmt.commit()

println("Done with setting keys.")
start = System.currentTimeMillis()
println("We're in the parse section!")
// Adding all verticies to graph
theCount = 0
nodeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_nodes.csv"
new File(nodeCSV).splitEachLine(",") { fields ->
println("Count = ${theCount}")
    if (theCount > 0) { 
		if((theCount % 10000) == 0){
			graph.tx().commit()
			println("Commiting at: "+theCount)
		}
		graph.addVertex("run_uuid", fields[1], "uuid", fields[2], "generation", fields[3].toInteger(), 
				"location", fields[4].toInteger(), "total_error", fields[5].toFloat(), 
				"numSelections", fields[6].toInteger(), "numChildren", fields[7].toInteger())
	}
	++theCount
}
graph.tx().commit()

println "Loading took (ms): " + (System.currentTimeMillis() - start)

