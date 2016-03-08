// Uncomment these first two lines if you're running this for the first time and don't have the
// graph loaded.
import java.io.*

graph = TitanFactory.open('genome_db.properties')
g = graph.traversal()

def all_ancestors(starters) {
    result = starters
    last_generation = starters
    while (last_generation.size() > 0) {
        current_generation = []
	inject(last_generation).unfold().has('is_random_replacement', false).inE().outV().dedup().fill(current_generation)
	result += current_generation
	last_generation = current_generation
    }
    return result
}

/*
winners = []
// The `; null` just spares us a ton of printing
g.V().has('total_error', 0).fill(winners) ; null

ancestors = all_ancestors(winners) ; null
ancG = inject(ancestors).unfold().inE().subgraph('sg').cap('sg').next()
anc = ancG.traversal()
*/

/*
// Uncomment these two lines if you don't have the ancestral subgraph computed.

// ancG = g.V().has('total_error', 0).repeat(__.has('is_random_replacement', false).inE().subgraph('sg').outV().dedup()).times(800).cap('sg').next()
// anc = ancG.traversal()
*/

// This gets kind of complicated because we have to collect together both the
// winners (for the successful runs) and the individuals in the last generation
// (for the unsuccessful runs). We first tried both `or` and `union`, but both
// of those took forever and were really infeasible. It turns out that if we
// extract both sets independently (which is fast thanks to indexing). save them
// in Groovy lists, and then use `inject` and `unfold` to insert them into a
// pipeline, then everything works.

// Run 0
target_run_uuid = '2c040008-1ef9-4019-90c3-6f4a171f3147'

winners = []
// The `; null` just spares us a ton of printing
g.V().has('total_error', 0).has('run_uuid', target_run_uuid).fill(winners); null

// The 300 here assumes that unsuccessful runs go out to generation 300. We should
// try to remove that magic constant, maybe by querying for the largest generation?
gen300 = []
g.V().has('generation', 300).has('run_uuid', target_run_uuid).fill(gen300); null

// The `unfold()` is necessary to convert the two big lists (which is how
// `inject()` adds `winners` and `gen300` to the pipeline) into a sequence
// of their contents.
ancG = inject(winners).inject(gen300).unfold().repeat(__.inE().subgraph('sg').outV().dedup()).times(977).cap('sg').next()
anc = ancG.traversal()

maxGen = anc.V().values('generation').max().next()
maxError = anc.V().values('total_error').max().next()

(0..maxGen).each { gen -> anc.V().has('generation', gen).sideEffect { edges = it.get().edges(Direction.OUT); num_ancestry_children = edges.collect { it.inVertex() }.unique().size(); it.get().property('num_ancestry_children', num_ancestry_children) }.iterate(); graph.tx().commit(); println gen }

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

     // hue = 1.0/3 + (2.0/3)*Math.log(total_error+1)/Math.log(error_ceiling+1)
     hue = 1.0/3 + (2.0/3) * (1 - n['pze_even'])
     otherhue = 1.0/3 + (2.0/3) * (1 - n['pze_odd'])
	 idk = "${hue} 1 1;0.5:${otherhue} 1 1"
     color = "\"${idk}\""
     
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

	// Add one and multiply by four to make the line visible
	edgeWidth = (1+(e['ns']/1000))*4
	// May want a var to track the max for 'ns' and 'nc'
	// this could be used to make a more meaningfull transparency/width
	transparency = ((e['nc']*20)+30)
	rounded = (int) Math.round(transparency);
	if (rounded > 255) {
		rounded = 255
	} 

	trans = Integer.toHexString(rounded).toUpperCase();
	if (e['gos'] == "[:alternation :uniform-mutation]"){
		// c = "black";
		c = "#000000"+trans;
		sty = "solid";
	} else if (e['gos'] == ":alternation") {
		// c = "black";
		c = "#000000"+trans;
		sty = "dashed";
	} else if (e['gos'] == ":uniform-mutation") {
		// c = "orangered";
		c = "#FF4500"+trans;
		sty = "solid";
	} else if (e['gos'] == ":uniform-close-mutation") {
		// c = "orangered";
		c = "#FF4500"+trans;
		sty = "dashed";
	} else {
		// c = "lightgray";
		c = "red";
		sty = "solid";
	}
	fr.println('"' + e['parent'] + '"' + " -> " + '"' + e['child'] + '"' + " [color=\"${c}\", penwidth=${edgeWidth}, style=\"${sty}\"];")
}

// Open the DOT file, print the DOT header info.
// fr = new java.io.FileWriter("/Research/RSWN/recursive-variance-v3/data7_ancestors.dot")
fr = new java.io.FileWriter("/Research/RSWN/lexicase/run1_half_and_half_node.dot")
fr.println("digraph G {")

// Generate nodes for all the generations
// "Gen 82" -> "Gen 83" -> "Gen 84" -> "Gen 85" -> "Gen 86" -> "Gen 87" [style=invis];
fr.println((0..maxGen).collect{ '"Gen ' + it + '"' }.join(" -> ") + " [style=invis];")

// Set up the defaults for nodes and edges.
fr.println('node[shape=point, width=0.15, height=0.15, fillcolor="white", penwidth=1, label=""];')
fr.println('edge[arrowsize=0.5, color="grey", penwidth=1, style="solid"];')

// Process all the vertices
anc.V().
	as('v').values('uuid').as('id').
	select('v').values('num_selections').as('ns').
	select('v').values('total_error').as('te').
	select('v').values('num_ancestry_children').as('nac').
	select('v').values('percent_zero_errors_evens').as('pze_even').
	select('v').values('percent_zero_errors_odds').as('pze_odd').
	select('id', 'te', 'ns', 'nac', 'pze_even', 'pze_odd').
	sideEffect{ printNode(fr, maxError, it.get()) }.
	iterate(); null

// Process all the edges
anc.E().
	as('e').inV().values('num_selections').as('ns').
	select('e').inV().values('num_ancestry_children').as('nc').
	select('e').outV().values('uuid').as('parent').
	select('e').inV().values('uuid').as('child').
	select('e').inV().values('genetic_operators').as('gos').
	// select('e').values('parent_type').as('type').
	select('parent', 'child', 'gos', 'ns', 'nc'). // , 'type').
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
