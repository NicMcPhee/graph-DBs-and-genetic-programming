// A comment here for coloring to show in file w/ gedit.
import java.io.*
import java.util.zip.GZIPInputStream

// Based on an algorithm by maythesource.com:
// http://stackoverflow.com/a/15905916
fastSplit = { s ->
	words = []
	boolean notInsideComma = true
	int start = 0
	int end = 0
	for (int i=0; i<s.length()-1; ++i) {
		if (s.charAt(i) == ',' && notInsideComma) {
			words += [s.substring(start, i)]
			start = i+1
		} else if (s.charAt(i) == '"') {
			notInsideComma = !notInsideComma;
		}
	}
	words += [s.substring(start)]
	return words
}

createPropertiesAndKeys = { graph ->
	mgmt = graph.openManagement()

	// Node properties
	successfulProperty = mgmt.makePropertyKey("successful").dataType(Boolean.class).make()

	run_uuid = mgmt.makePropertyKey("run_uuid").dataType(String.class).make()
	uuid = mgmt.makePropertyKey("uuid").dataType(String.class).make()
	generation = mgmt.makePropertyKey("generation").dataType(Integer.class).make()
	location = mgmt.makePropertyKey("location").dataType(Integer.class).make()
	genetic_operators = mgmt.makePropertyKey("genetic_operators").dataType(String.class).make()
	plush_genome = mgmt.makePropertyKey("plush_genome").dataType(String.class).make()
	total_error = mgmt.makePropertyKey("total_error").dataType(Float.class).make()
	is_random_replacement = mgmt.makePropertyKey("is_random_replacement").dataType(Boolean.class).make()
	error_vector = mgmt.makePropertyKey("error_vector").dataType(String.class).make()
	percent_zero_errors_even_indices = mgmt.makePropertyKey("percent_zero_errors_evens").dataType(Float.class).make()
	percent_zero_errors_odd_indices = mgmt.makePropertyKey("percent_zero_errors_odds").dataType(Float.class).make()

	num_children = mgmt.makePropertyKey("num_children").dataType(Integer.class).make()
	num_selections = mgmt.makePropertyKey("num_selections").dataType(Integer.class).make()
	num_ancestry_children = mgmt.makePropertyKey("num_ancestry_children").dataType(Integer.class).make()

	// Vertex Labels
	individual = mgmt.makeVertexLabel('individual').make()
	run = mgmt.makeVertexLabel('run').make()

	// Edge properties
	parent_type = mgmt.makePropertyKey("parent_type").dataType(String.class).make()

	// Indexing
	successfulIndex = mgmt.buildIndex("successfulIndex", Vertex.class).addKey(successfulProperty).indexOnly(run).buildCompositeIndex()
	uuidIndex = mgmt.buildIndex("uuidIndex",  Vertex.class).addKey(uuid).unique().buildCompositeIndex()
	generationTotalError = mgmt.buildIndex('generationTotalError', Vertex.class).addKey(generation).addKey(total_error).buildMixedIndex("search")
	selectionsIndex = mgmt.buildIndex('selectionsIndex', Vertex.class).addKey(num_children).addKey(num_selections).addKey(num_ancestry_children).buildMixedIndex("search")
	mgmt.commit()
	println("Done with setting keys.")
}

parseCsvFile = { graph, zippedCsvFile, runUUID ->
	start = System.currentTimeMillis()
	g = graph.traversal()

	println("We're in the parse section!")

	fileStream = new FileInputStream(zippedCsvFile)
	gzipStream = new GZIPInputStream(fileStream)
	inputReader = new InputStreamReader(gzipStream)
	reader = new BufferedReader(inputReader)

	theCount = 0 // How many rows have we read?
	successful = false // Was this run successful (i.e., zero error?)
	maxGen = 0 // The last (& largest) generation number in this run
	while ((line = reader.readLine()) != null) {
		if (theCount % 1000 == 0) {
			println("Count = ${theCount}")
		}
		// The first line is the headers, which we currently ignore. We really
		// should read them and use them to drive a lot of the stuff that we end
		// up fiddling by hand below.
		if (theCount > 0) {
			fields = this.fastSplit(line)
			// This only makes sense if we're using is-random-replacement
			// (and it's in location 9).
			/*
			if (fields[9] == "") {
			fields[9] = true
			}
			*/

			error_vector_values = fields[10..-1].collect { it.toFloat() }
			errors_with_indices = error_vector_values.withIndex() // [[a, 0], [b, 1], [c, 2], ...]
			num_zero_errors_even_indices = errors_with_indices.findAll { it[1] % 2 == 0 && it[0] == 0 }.size
			num_zero_errors_odd_indices = errors_with_indices.findAll { it[1] % 2 == 1 && it[0] == 0 }.size
			// Evens are printing tests
			percent_zeros_even_indices = num_zero_errors_even_indices * 1.0 / (error_vector_values.size / 2)
			// Odds are returning tests
			percent_zeros_odd_indices = num_zero_errors_odd_indices * 1.0 / (error_vector_values.size / 2)

			errors = fields[10..-1].join(",")

			if((theCount % 1000) == 0){
				println("Commiting at: "+theCount)
				graph.tx().commit()
			}
			// Remember to change this to fields[9] when working with non autoconstuctive runs!
			total_error = fields[9].toFloat()
			if (total_error == 0) {
				successful = true;
			}
			gen = fields[1].toInteger()
			if (gen > maxGen){
				maxGen = gen;
			}
			newVertex = graph.addVertex(label, "individual", "run_uuid", runUUID,
			"uuid", fields[0],
			"generation", fields[1].toInteger(), "location", fields[2].toInteger(),
			"genetic_operators", fields[4], "plush_genome", fields[8],
			// "total_error", total_error, "is_random_replacement", fields[9].toBoolean())
			"total_error", total_error, "error_vector", errors,
			"percent_zero_errors_evens", percent_zeros_even_indices,
			"percent_zero_errors_odds", percent_zeros_odd_indices)
			// errors.each { newVertex.property("error_vector", it) }

			if (fields[3].length() > 5) {
				motherUuid = fields[3][4..39]
				// println "<" + motherUuid + "> ::: <" + fatherUuid + ">"
				mother = g.V().has("uuid", motherUuid).next()
				motherEdge = mother.addEdge('parent_of', newVertex)
				// We commented out the properties because they're not meaningful
				// for the non-autoconstructive runs.
				motherEdge.property("parent_type", "mother")

				if (fields[3].length() > 48) {
					fatherUuid = fields[3][45..-5]
					father = g.V().has("uuid", fatherUuid).next()
					fatherEdge = father.addEdge('parent_of', newVertex)
					fatherEdge.property("parent_type", "father")
				}

			}
		}
		++theCount
	}
	reader.close()
	graph.tx().commit()
	println "Loading took (ms): " + (System.currentTimeMillis() - start)

	return [maxGen, successful]
}

addNumSelections = { graph, maxGen ->
	g = graph.traversal()

	(0..maxGen).each { gen -> g.V().has('generation', gen).sideEffect { 
			       num_selections = it.get().edges(Direction.OUT).size(); 
			       it.get().property('num_selections', num_selections) }.iterate(); 
			   graph.tx().commit(); 
			   println gen }
}

addNumChildren = { graph, maxGen ->
	g = graph.traversal()

	(0..maxGen).each { gen -> g.V().has('generation', gen).sideEffect { 
			       edges = it.get().edges(Direction.OUT); 
			       num_children = edges.collect { it.inVertex() }.unique().size(); 
			       it.get().property('num_children', num_children) }.iterate(); 
			   graph.tx().commit(); 
			   println gen }
}

addRunNode = { graph, runUUID, runFileName, successful, maxGen ->
	runVertex = graph.addVertex(label, "run", "run_uuid", runUUID, "data_file", runFileName, "successful", successful, "max_generation", maxGen)
	graph.tx().commit()
}

runUUID = java.util.UUID.randomUUID()

graph = TitanFactory.open('./genome_db.properties')
g = graph.traversal()

createPropertiesAndKeys(graph)

runFileName = "data0.csv.gz"
nodeCSVzipped = "/Research/RSWN/lexicase/" + runFileName

(maxGen, successful) = parseCsvFile(graph, nodeCSVzipped, runUUID)
addNumSelections(graph, maxGen)
addNumChildren(graph, maxGen)
// If we put this before addNumSelections, etc., can we pull
// maxGen out of the run node and not need to pass it as an
// argument to those functions?
addRunNode(graph, runUUID, runFileName, successful, maxGen)

