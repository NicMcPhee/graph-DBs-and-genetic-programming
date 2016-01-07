//graph = TitanFactory.open("db.properties");

println("We're in the script")
mgmt = graph.openManagement()
parent_of = mgmt.makeEdgeLabel('parent_of').multiplicity(MULTI).make()
mgmt.commit()
 
g = graph.traversal()
theCounter = 0
countVertex = 0
getOrCreate = { uuid ->
  def p = g.V().has('uuid', uuid)
  if(p.hasNext()){
	//println("Found vertex"+counter)
	p.next()
	countVertex++
  }else{
	 theCounter++
	 graph.addVertex("run_uuid", "", "uuid", uuid, "generation", counter, 
				"location", counter, "total_error", counter.toFloat(), 
				"numSelections", counter, "numChildren", counter)

  }
}
  
println("Getting the file")
edgeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_edges.csv"

println("Looping the file")
counter = 0
new File(edgeCSV).splitEachLine(",") { fielder ->
	++counter
    if (counter > 1) { 
	//println("This is what we get "+fielder[0])
    parentVertex = getOrCreate(fielder[0])
	childVertex = getOrCreate(fielder[1])
	if(countVertex > 1){
 	theEdge = parentVertex.addEdge('parent_of', childVertex)
	println("Added the: "+counter+" Edge: "+theEdge)
	}
  }
}
  
println("Total added verticies: "+theCounter)
println("Commiting")
//graph.tx.commit()

/*
mgmt = graph.openManagement()

// vv**************** Testing creating edge label ***********************vv
println("We're making an edgeLabel")
parent_of = mgmt.makeEdgeLabel('parent_of').multiplicity(MULTI).make()
println("We're DONE making an edgeLabel")
mgmt.commit()

// vv**************** Testing adding edge to existing vertices ***********************vv
edgeCSV = "/Research/autoconstruction_neo4j/replace-space-with-newline__d_edges.csv"

g = graph.traversal()

counter = 0
new File(edgeCSV).splitEachLine(",") { fielder ->
	println("Count = ${counter}")
    ++counter
    if (counter > 1) { 
		parent = g.V().has("uuid", fielder[0]).next()
		child = g.V().has("uuid", fielder[1]).next()
		graph.addEdge(parent, child, 'parent_of')
	}
}

graph.tx().commit()
*/
