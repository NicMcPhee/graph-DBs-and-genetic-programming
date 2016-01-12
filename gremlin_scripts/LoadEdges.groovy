//graph = TitanFactory.open("db.properties");

println("We're in the script")
mgmt = graph.openManagement()
parent_of = mgmt.makeEdgeLabel('parent_of').multiplicity(MULTI).make()
mgmt.commit()
 
g = graph.traversal()
theCounter = 0
countVertex = 0

/*
getOrCreate = { uuid ->
  def p = g.V().has("uuid", uuid)
  if(p.hasNext()){
	println("Found vertex: "+p)
	p.next()
	countVertex++
  }
}
*/  
start = System.currentTimeMillis()
println("Getting the file")
edgeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_edges.csv"

println("Looping the file")
counter = 0
new File(edgeCSV).splitEachLine(",") { fielder ->
	println("Count = ${counter}")
    if (counter > 0) { 
		if((counter % 10000) == 0){
			println("Commiting at: "+counter)
			graph.tx().commit()
		}
		//println("This is what we get "+fielder[0])
   		parentVertex = g.V().has("uuid", fielder[0]).next()
		childVertex = g.V().has("uuid", fielder[1]).next()
	 	theEdge = parentVertex.addEdge('parent_of', childVertex)
		//println("Added the: "+counter+" Edge: "+theEdge + " in " + (System.currentTimeMillis() - start))
	}
	++counter
}
  
println("Total added verticies: "+counter)
println "Loading took (ms): " + (System.currentTimeMillis() - start)
println("Commiting")
graph.tx().commit()
