// Comment for color
import java.io.*
import org.apache.tinkerpop.gremlin.process.traversal.P

// SharedGenes should be in the classpath

// The target node line:format
//	"87:719" [shape=rectangle, width=4, style=filled, fillcolor="0.5 1 1"];
void printNode(fr, maxError, n, color_map) {
println("Printing nodes.")
     width = n['ns']/50
     height = n['nac']/10

	// Make array of stored string
	total_error_vector = n['ev'].split(",")

	zero_one_errors = total_error_vector.collect{ item ->
		if (item.size() > 6) { v = 1 }
		else {
			v = item.toInteger();
			if (v > 0) { v = 1 }
		}
		v
	}

	// (red, green, blue) = color_map[zero_one_errors]
	//println([red, green, blue])

	// For RBM coloring
	// color = String.format("\"#%02x%02x%02x\"", red, green, blue)

	// Limit total errors if above the ceiling limit
	error_ceiling = 100000
	// Get sum of total error from even and odd indicies
	//even_total_error = 0
	//odd_total_error = 0
	/*total_error_vector.eachWithIndex{ item, index ->
		if (index % 2 == 0) {
			if (item.size() > 6) { even_total_error = error_ceiling }
			else { even_total_error += item.toInteger() }
		} else {
			if (item.size() > 6){ odd_total_error = error_ceiling }
			else { odd_total_error += item.toInteger() }
		}
	}*/

	 if (maxError < error_ceiling) {
		error_ceiling = maxError
	 }
	 total_error = n['te']
	 if (total_error > error_ceiling) {
		total_error = error_ceiling
	 }

	// Assigning hue based on percentage of zeros (single coloring)
	num_zeros = 0
	total_error_vector.each{ item ->
		if (item.size() < 6 && item.toInteger() == 0) { num_zeros += 1 }
	}
	// per_num_zeros = num_zeros/200 <-- should be length of error vector
	per_num_zeros = num_zeros/10

 	 hue = 1.0/6 + (5.0/6) * (1 - per_num_zeros)
	 shade = 1.0/6 + (5.0/6)*(1-Math.log(total_error+1)/Math.log(error_ceiling+1))
	 combo = "${hue} 1 ${shade}"
	 color = "\"${combo}\""

	// Assigning hue based on percentage of zeros (dual coloring)
	 /*hue = 1.0/6 + (5.0/6) * (1 - n['pze_even'])
	 shade = 1-(Math.log(even_total_error+1)/Math.log(error_ceiling+1))
	 otherhue = 1.0/6 + (5.0/6) * (1 - n['pze_odd'])
	 othershade = 1-(Math.log(odd_total_error+1)/Math.log(error_ceiling+1))
  	 combo = "${hue} 1 ${shade};0.5:${otherhue} 1 ${othershade}"
	 color = "\"${combo}\""
	*/
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
void printEdge(fr, e, lexicase, filtering) {
	// Add one and multiply by four to make the line visible
	// edgeWidth = (1 - e['DL_dist']/1000.0)*5

	// if (edgeWidth <= 0.5) { edgeWidth = 0.5 }

  edgeWidth = 1

	if(!lexicase) {
		if (e['type'] == "mother") {
			c = "gray40"
			sty = "solid"
		} else {
			c = "gray70"
			sty = "dashed"
		}
	} else {
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
			// c = "#000000"+trans;
			sty = "solid";
		} else if (e['gos'] == ":uniform-close-mutation") {
			// c = "orangered";
			c = "#FF4500"+trans;
			// c = "#000000"+trans;
			sty = "dashed";
		} else {
			// c = "lightgray";
			c = "green";
			sty = "solid";
		}
	}

	if (filtering == "dl_distance") {
		DL_dist = e['DL_dist']/10
		fr.println('"' + e['parent'] + '"' + " -> " + '"' + e['child'] + '"' + " [color=\"${c}\", penwidth=${edgeWidth}, style=\"${sty}\", label=\" ${DL_dist}\"];")
	} else {
		fr.println('"' + e['parent'] + '"' + " -> " + '"' + e['child'] + '"' + " [color=\"${c}\", penwidth=${edgeWidth}, style=\"${sty}\"];")
	}
}

def get_ancestors (max_gen, ancestor_list, filter_closure){
  /* max_gen - number of generations we want to trace upwards
   * ancestor_list - a list of vertices whose ancestry you would like to trace
   * filter - a closure that takes a traversal containing vertices and produces
   *          a folded traversal containing edges to be included in the subgraph
   *
   * returns a traversal
   */
  ancG = inject(ancestor_list).unfold().repeat(filter_closure(__).unfold().subgraph('sg').outV().dedup()).times(max_gen).cap('sg').next()
  return ancG.traversal()
}

/* pretty sure that this code is terribly broken */
def get_ancestors_of_auto_run (max_gen, ancestor_list, filter) {
	if (filter){
		ancG = inject(ancestor_list).unfold().repeat(__.has('is_random_replacement', false).inE().hasNot('minimal_contribution').
			subgraph('sg').outV().dedup()).times(max_gen).cap('sg').next()
	} else {
		ancG = inject(ancestor_list).unfold().repeat(__.has('is_random_replacement', false).inE().
			subgraph('sg').outV().dedup()).times(max_gen).cap('sg').next()
	}
	anc = ancG.traversal()
	return anc
}

def get_ancestors_of_lexicase_run (max_gen, ancestor_list, filtering_closure) {

  ancG = get_ancestors(max_gen, ancestor_list, filtering_closure)
	anc = ancG.traversal()
	println(anc)
	return anc
}

/* There are two different kinds of filters that we apply to our alternation only graph.
 * First, we can walk up the tree and skip parents who didn't contribute genetic information
 * to its immediate child. Alternatively, we can work our way up the tree and remove any individual
 * who doesn't share any genes with the winning individual whose line we are chasing....
 * Hmmm... that would require building a tree for each of the winners and then chasing merging them
 * back together.
 */
// def get_genetic_ancestors_of_lexicase_run (max_gen, ancestor_list ){

  // hasWinnerGenes = new GenePool(ancestor_list)

  // println("debug: building ancestry tree")
  // ancG = inject(ancestor_list).unfold().repeat(__.inE()
                                               // .filter {e -> hasWinnerGenes.test(e.get().vertex(OUT))}
                                               // .subgraph('sg')
                                               // .outV().dedup()).cap('sg').next()
	// anc = ancG.traversal()
	// println(anc)
	// return anc
// }

createGeneChecker = { anc_list ->

  parser = Parsers.newParser(defaultConfiguration())

  anc_genomes = anc_list.collect { individual ->
    genomeString = individual.values('plush_genome').next()
    parser.nextValue(Parsers.newParseable(genomeString))
  }

  anc_genes = anc_genomes.flatten().unique()

  return { vertex ->
    vertex_genome_string = vertex.values('plush_genome').next()
    vertex_genome = parser.nextValue(Parsers.newParseable(vertex_genome_string))

    return anc_genes.any { g1 ->
      vertex_genome.any { g2 ->
        g1 == g2
      }
    }
  }
}


loadAncestry = { propertiesFileName, csvFilePath, filtering, lexicase, successful ->
  /* filtering - should be one of "none", "genes", "dl_distance"
   */
	start = System.currentTimeMillis()

	graph = TitanFactory.open(propertiesFileName)
	g = graph.traversal()

	run_uuid = g.V().hasLabel('run').values('run_uuid').next()

	ancestor_list = []
	if (successful){
		g.V().has('total_error', 0).fill(ancestor_list)
	} else {
		winners = []
		g.V().has('total_error', 0).has('run_uuid', run_uuid).fill(winners)
		gen300 = []
		g.V().has('generation', 300).has('run_uuid', run_uuid).fill(gen300)
		ancestor_list = winners+gen300
	}

  switch (filtering) {
  case "none":
    filter = {traversal -> traversal.inE()}
    break
  case "genes":
    geneChecker = createGeneChecker(ancestor_list)
    filter = {traversal ->  traversal.inE().filter{e -> geneChecker(e.get().vertex(OUT))} }
    break
  case "dl_distance":
    filter = {traversal -> traversal.inE()hasNot('minimal_contribution') }
    break
  }

	if (lexicase) {
		// anc = get_ancestors_of_lexicase_run(300, ancestor_list, filter)
		// anc = get_genetic_ancestors_of_lexicase_run(300, ancestor_list)
    // final_filter = {traversal - > filter(traversal)}
    anc = get_ancestors(300, ancestor_list, filter)
	} else {
		anc = get_ancestors_of_auto_run(1000, ancestor_list, filter)
	}

  println(anc)

	if (successful){
		maxGen = anc.V().values('generation').max().next()
	} else {
		maxGen = 300
	}

	maxError = anc.V().values('total_error').max().next()

	(0..maxGen).each { gen ->
    anc.V().has('generation', gen).sideEffect {
      edges = it.get().edges(Direction.OUT);
      num_ancestry_children = edges.collect { it.inVertex() }.unique().size();
      it.get().property('num_ancestry_children', num_ancestry_children) }.iterate();
    graph.tx().commit();
    println gen }

	// Open the DOT file, print the DOT header info.
	fr = new java.io.FileWriter(csvFilePath)

	fr.println("digraph G {")

	// Generate nodes for all the generations
	// "Gen 82" -> "Gen 83" -> "Gen 84" -> "Gen 85" -> "Gen 86" -> "Gen 87" [style=invis];
	fr.println((0..maxGen).collect{ '"Gen ' + it + '"' }.join(" -> ") + " [style=invis];")

	// Set up the defaults for nodes and edges.
	fr.println('node[shape=point, width=0.15, height=0.15, fillcolor="white", penwidth=1, label=""];')
	fr.println('edge[arrowsize=0.5, color="grey", penwidth=1, style="solid"];')

	// Process all the vertices
	anc.V().hasLabel('individual').
		as('v').values('uuid').as('id').
		select('v').values('num_selections').as('ns').
		select('v').values('total_error').as('te').
		select('v').values('num_ancestry_children').as('nac').
		select('v').values('error_vector').as('ev').
		//select('v').values('percent_zero_errors_evens').as('pze_even').
		//select('v').values('percent_zero_errors_odds').as('pze_odd').
		select('id', 'te', 'ns', 'nac', 'ev').//, 'pze_even', 'pze_odd').
		// sideEffect{ printNode(fr, maxError, it.get(), color_map) }.
		 sideEffect{ printNode(fr, maxError, it.get(), null) }.
		iterate(); null

	// Process all the edges
  anc.E().hasLabel('parent_of').
		as('e').inV().values('num_selections').as('ns').
		select('e').inV().values('num_ancestry_children').as('nc').
		select('e').outV().values('uuid').as('parent').
		select('e').inV().values('uuid').as('child').
		select('e').inV().values('genetic_operators').as('gos').
		// select('e').values('DL_dist').as('DL_dist').
		// select('e').values('parent_type').as('type').
		// select('parent', 'child', 'gos', 'ns', 'nc', 'DL_dist', 'type').
		select('parent', 'child', 'gos', 'ns', 'nc').
		sideEffect{ printEdge(fr, it.get(), lexicase, filtering) }.
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
	println "Loading took (ms): " + (System.currentTimeMillis() - start)
}


println("Java Import is loaded!")
//loadAncestry = { propertiesFileName, csvFilePath, filter, lexicase, successful
println("To load a CSV file use a call like:\n\
For successful lexicase w/ filtering: \tloadAncestry('genome_db.properties', '/Research/RSWN/lexicase/run0_RBM_color_filtered_30000.dot', true, true, true)\n\
For successful auto w/ filtering: \tloadAncestry('autoconstruction_db.properties', '/Research/RSWN/recursive-variance-v3/run5_RBM_color_filtered_60000.dot', true, false, true)\n\
where you replace 'genome_db.properties' with the name of your properties file\n\
and '/Research/RSWN/lexicase/run2_RBM_color_full.dot' with the path to your output file.\n\
filter is a boolean for being filtered (true) or not (false). \n\
lexicase is boolean for being a lexicase run (true) or autoconstruction (false). \n\
successful is a boolean for if run(s) are successful (true) or not (false), successful runs are still included if false is chosen.")
