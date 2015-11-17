graph = TitanFactory.open('conf/titan-berkeleyje-es.properties')

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

