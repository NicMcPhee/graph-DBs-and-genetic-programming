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


// Uncomment these two lines if you don't have the ancestral subgraph computed with autoconstruction.
/*
ancG = g.V().has('total_error', 0).repeat(__.has('is_random_replacement', false).inE().subgraph('sg').outV().dedup()).times(800).cap('sg').next()
anc = ancG.traversal()
*/

// This gets kind of complicated because we have to collect together both the
// winners (for the successful runs) and the individuals in the last generation
// (for the unsuccessful runs). We first tried both `or` and `union`, but both
// of those took forever and were really infeasible. It turns out that if we
// extract both sets independently (which is fast thanks to indexing). save them
// in Groovy lists, and then use `inject` and `unfold` to insert them into a
// pipeline, then everything works.

// Run 0
//target_run_uuid = '752e0990-b8ce-4712-b6d6-c9a6f120f89d'
// Run 1 (mags)
target_run_uuid = '3b2ba747-d2d0-4efe-9a35-f64ad8e82792'
// Auto Run 5
//target_run_uuid = 'cc98e4b9-2cd5-4ae0-b7cc-d4eeacf4d2b1'
// Auto Run 7
//target_run_uuid = '2b7bc47a-7afb-40b1-bd4c-5e22ea6bb5dc'

winners = []
// The `; null` just spares us a ton of printing
g.V().has('total_error', 0).has('run_uuid', target_run_uuid).fill(winners); null

// The 300 here assumes that unsuccessful runs go out to generation 300. We should
// try to remove that magic constant, maybe by querying for the largest generation?
gen300 = []
//g.V().has('generation', 300).has('run_uuid', target_run_uuid).fill(gen300); null

// The `unfold()` is necessary to convert the two big lists (which is how
// `inject()` adds `winners` and `gen300` to the pipeline) into a sequence
// of their contents.
//ancG = inject(winners).inject(gen300).unfold().repeat(__.inE().subgraph('sg').outV().dedup()).times(800).cap('sg').next()
ancG = inject(winners).inject(gen300).unfold().repeat(__.inE().has('DL_dist', lt(900)).subgraph('sg').outV().dedup()).times(977).cap('sg').next()
anc = ancG.traversal()

maxGen = anc.V().values('generation').max().next()
maxError = anc.V().values('total_error').max().next()
//maxDist = anc.E().values('DL_dist').max().next()

(0..maxGen).each { gen -> anc.V().has('generation', gen).sideEffect { edges = it.get().edges(Direction.OUT); num_ancestry_children = edges.collect { it.inVertex() }.unique().size(); it.get().property('num_ancestry_children', num_ancestry_children) }.iterate(); graph.tx().commit(); println gen }

// The target node line:format
//	"87:719" [shape=rectangle, width=4, style=filled, fillcolor="0.5 1 1"];
void printNode(fr, maxError, n, color_map) {
     width = n['ns']/50
     height = n['nac']/10

	// Make array of stored string
	total_error_vector = n['ev'].split(",")
	//println(total_error_vector)
	even_total_error = 0
	odd_total_error = 0
	// Limit total errors if above the ceiling limit
	//error_ceiling = 100000
	// Get sum of total error from even and odd indicies
/*	total_error_vector.eachWithIndex{ item, index ->
		if (index % 2 == 0) {
			if (item.size() > 6) { even_total_error = error_ceiling }
			else { even_total_error += item.toInteger() }
		} else {
			if (item.size() > 6){ odd_total_error = error_ceiling }
			else { odd_total_error += item.toInteger() }
		}
	}*/
	zero_one_errors = total_error_vector.collect{ item ->
		if (item.size() > 6) { 
			v = 1
		} else { 
			v = item.toInteger(); 
			if (v > 0) {
				v=1
			}
		}
		v
	}
	println(zero_one_errors)
	(red, green, blue) = color_map[zero_one_errors]
	println([red, green, blue])
	num_zeros = 0
	total_error_vector.eachWithIndex{ item, index ->
		if (item.size() < 6 && item.toInteger() == 0) {
			num_zeros+= 1
			//println("found one")
		}
	}
	per_num_zeros = num_zeros/200
	println("The percentage value is "+ per_num_zeros)
	error_ceiling = 100000
	 if (maxError < error_ceiling) {
		error_ceiling = maxError
	 }
	 total_error = n['te']
	 if (total_error > error_ceiling) {
		total_error = error_ceiling
	 }


	// For bad hash function coloring
	//color = String.format("\"#%02x%02x%02x\"", n['red'], n['green'], n['blue'])
	color = String.format("\"#%02x%02x%02x\"", red, green, blue)

	// Assigning the hue of a node to be the total_error (single coloring)
/*
	 hue = 1.0/6 + (5.0/6) * (1 - per_num_zeros)
	 shade = 1.0/6 + (5.0/6)*(1-Math.log(total_error+1)/Math.log(error_ceiling+1))
	 combo = "${hue} 1 ${shade}"
	 color = "\"${combo}\""
*/

	// Assigning hue based on percentage of zeros
	 /*hue = 1.0/6 + (5.0/6) * (1 - n['pze_even'])
	 shade = 1-(Math.log(even_total_error+1)/Math.log(error_ceiling+1))
	 otherhue = 1.0/6 + (5.0/6) * (1 - n['pze_odd'])
	 othershade = 1-(Math.log(odd_total_error+1)/Math.log(error_ceiling+1))
  	 combo = "${hue} 1 ${shade};0.5:${otherhue} 1 ${othershade}"
	 color = "\"${combo}\""
	*/
     name = '"' + n['id'] + '"'
     
	 // For nodes to have same colored borders
     // params = "[shape=rectangle, color="+color+ ", width="
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
    // For autoconstruction
	/*if (e['type'] == "mother") { 
		c = "gray40" 
		sty = "solid"
	} else { 
		c = "gray70" 
		sty = "dashed"
	}*/

	// Add one and multiply by four to make the line visible
	// edgeWidth = (1+(e['ns']/1000))*4
	edgeWidth = (1 - e['DL_dist']/1000.0)*5
	
	// Played with edge width based on max difference, 
	// not too helpful at this time.
	// edgeWidth = (1-(Math.log(e['DL_dist']+1)/Math.log(maxDist+1)))*5
	// edgeWidth = (1-(e['DL_dist']/maxDist))*5

	if (edgeWidth <= 0.5) { edgeWidth = 0.5 }
	
	// May want a var to track the max for 'ns' and 'nc'
	// this could be used to make a more meaningfull transparency/width
	
	transparency = ((e['nc']*20)+30)
	rounded = (int) Math.round(transparency);
	if (rounded > 255) {
		rounded = 255
	}
	

	// For non-autoconstruction edge coloring
	/*
	if(e['gos'] == ":uniform-mutation" || e['gos'] == ":uniform-close-mutation"){
		rounded = 50
	}else{
		rounded = (int) Math.round((edgeWidth/5) * 255)
		if (rounded < 50) { rounded = 50 }
	}
	*/
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
		// c = "#FF4500"+trans;
		c = "#000000"+trans;
		sty = "solid";
	} else if (e['gos'] == ":uniform-close-mutation") {
		// c = "orangered";
		// c = "#FF4500"+trans;
		c = "#000000"+trans;
		sty = "dashed";
	} else {
		// c = "lightgray";
		c = "green";
		sty = "solid";
	}

	fr.println('"' + e['parent'] + '"' + " -> " + '"' + e['child'] + '"' + " [color=\"${c}\", penwidth=${edgeWidth}, style=\"${sty}\"];")
}

// Open the DOT file, print the DOT header info.
fr = new java.io.FileWriter("/Research/RSWN/lexicase/run1_generic_name.dot")
//fr = new java.io.FileWriter("/Research/RSWN/recursive-variance-v3/run5_uni_color_shaded.dot")
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
	select('v').values('error_vector').as('ev').
	select('v').values('percent_zero_errors_evens').as('pze_even').
	select('v').values('percent_zero_errors_odds').as('pze_odd').
	//select('v').values('red').as('red').
	//select('v').values('green').as('green').
	//select('v').values('blue').as('blue').
	select('id', 'te', 'ns', 'nac', 'ev', 'pze_even', 'pze_odd').//, 'red', 'green', 'blue').
	sideEffect{ printNode(fr, maxError, it.get(), color_map) }.
	iterate(); null

// Process all the edges
anc.E().
	as('e').inV().values('num_selections').as('ns').
	select('e').inV().values('num_ancestry_children').as('nc').
	select('e').outV().values('uuid').as('parent').
	select('e').inV().values('uuid').as('child').
	select('e').inV().values('genetic_operators').as('gos').
	select('e').values('DL_dist').as('DL_dist').
	//select('e').values('parent_type').as('type').
	select('parent', 'child', 'gos', 'ns', 'nc', 'DL_dist').//, 'type').
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
