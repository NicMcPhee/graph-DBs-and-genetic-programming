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

println("Getting the file")
edgeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_edges.csv"

println("Looping the file")
counter = 0
theCount = 0
new File(edgeCSV).splitEachLine(",") { fielder ->
	++counter
    if (counter > 1) { 
		if(theCount == 10000){
			graph.tx().commit()
			theCount = 0;
			println("Commiting!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
		}
		//println("This is what we get "+fielder[0])
   		parentVertex = g.V().has("uuid", fielder[0]).next()
		childVertex = g.V().has("uuid", fielder[1]).next()
	 	theEdge = parentVertex.addEdge('parent_of', childVertex)
		println("Added the: "+counter+" Edge: "+theEdge)
	}
}
  
println("Total added verticies: "+counter)
println("Commiting")
graph.tx.commit()
