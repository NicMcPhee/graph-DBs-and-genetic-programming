// Uncomment these first two lines if you're running this for the first time and don't have the
// graph loaded.

// graph = TitanFactory.open('./db.properties')
// g = graph.traversal()

// Uncomment these two lines if you don't have the ancestral subgraph computed.

// ancG = g.V().has('total_error', 0).repeat(__.has('is_random_replacement', false).inE().subgraph('sg').outV()).times(977).cap('sg').next()
ancG = g.V().or(__.has('total_error', 0), __.has('generation', 300)).repeat(__.inE().subgraph('sg').outV().dedup()).times(977).cap('sg').next()
anc = ancG.traversal()

(0..310).each { gen -> anc.V().has('generation', gen).sideEffect { edges = it.get().edges(Direction.OUT); num_ancestry_children = edges.collect { it.inVertex() }.unique().size(); it.get().property('num_ancestry_children', num_ancestry_children) }.iterate(); graph.tx().commit(); println gen }

// The target node line:format
//	"87:719" [shape=rectangle, width=4, style=filled, fillcolor="0.5 1 1"];
void printNode(fr, maxError, n) {
     width = n['ns']/50
     height = n['nac']/10

	 error_ceiling = 10000
	 if (maxError < error_ceiling) {
		error_ceiling = maxError
	 }
	 total_error = n['te']
	 if (total_error > error_ceiling) {
		total_error = error_ceiling
	 }
     hue = 1.0/3 + (2.0/3)*Math.log(total_error+1)/Math.log(error_ceiling+1)
     color = "\"${hue} 1 1\""
     
     name = '"' + n['id'] + '"'
     
     params = "[shape=rectangle, width="
     params = params + width + ", height="
	 params = params + height + ", style=filled, fillcolor="
	 // params = params + width + ", style=filled, fillcolor="
     params = params + color + "]"
     
     fr.println(name  + " " + params + ";")
}

// The target edge line format:
//	"82:393" -> "83:619";
void printEdge(fr, e) {
	/*
	if (e['type'] == "mother") {
		c = "gray40";
	} else {
		c = "gray70";
	}
	*/
	c = "lightgray"
    fr.println('"' + e['parent'] + '"' + " -> " + '"' + e['child'] + '"' + " [color=\"${c}\"];")
}

maxGen = anc.V().values('generation').max().next()
maxError = anc.V().values('total_error').max().next()

// Open the DOT file, print the DOT header info.
fr = new java.io.FileWriter("/tmp/two_runs_ancestors.dot")
fr.println("digraph G {")

// Generate nodes for all the generations
// "Gen 82" -> "Gen 83" -> "Gen 84" -> "Gen 85" -> "Gen 86" -> "Gen 87" [style=invis];
fr.println((0..maxGen).collect{ '"Gen ' + it + '"' }.join(" -> ") + " [style=invis];")

// Set up the defaults for nodes and edges.
fr.println('node[shape=point, width=0.15, height=0.15, fillcolor="white", penwidth=1, label=""];')
fr.println('edge[arrowsize=0.5, color="grey"];')

// Process all the vertices
anc.V().
	as('v').values('uuid').as('id').
	select('v').values('num_selections').as('ns').
	select('v').values('total_error').as('te').
	select('v').values('num_ancestry_children').as('nac').
	select('id', 'te', 'ns', 'nac').
	sideEffect{ printNode(fr, maxError, it.get()) }.
	iterate(); null

// Process all the edges
anc.E().
	as('e').outV().values('uuid').as('parent').
	select('e').inV().values('uuid').as('child').
	// select('e').values('parent_type').as('type').
	select('parent', 'child'). // , 'type').
	sideEffect{ printEdge(fr, it.get()) }.
	iterate(); null

// Add all the "rank=same" entries to line up the generations, e.g.,
//    { rank=same; "Gen 186", "493c28e7-8ea1-4cfa-8a17-bbcf9cf38501" }
(maxGen+1).times { gen ->
	if (anc.V().has('generation', gen).hasNext()) {
		fr.println "{ rank=same; \"Gen " + gen + "\", \"" +
			anc.V().has('generation', gen).values('uuid').next() + "\" }"
	}
}

// Wrap up the DOT syntax and close
fr.println("}")
fr.close()
