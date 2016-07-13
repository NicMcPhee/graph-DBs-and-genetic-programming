// A comment here for coloring to show in file w/ gedit.
import java.io.*
import java.util.zip.GZIPInputStream
import java.nio.file.Path
import java.nio.file.FileSystems

import org.codehaus.groovy.runtime.MethodRankHelper

import static us.bpsm.edn.parser.Parsers.defaultConfiguration;
import us.bpsm.edn.*;
import us.bpsm.edn.parser.*;
import us.bpsm.edn.printer.*;

printDebugInfo = true

debugStatus = { str ->
  if (printDebugInfo){
    println("debug: ${str}")
  }
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
  plush_genome_size_prop = mgmt.makePropertyKey("plush_genome_size").dataType(Integer.class).make()
  plush_genome = mgmt.makePropertyKey("plush_genome").dataType(String.class).make()
  total_error = mgmt.makePropertyKey("total_error").dataType(Float.class).make()
  is_random_replacement = mgmt.makePropertyKey("is_random_replacement").dataType(Boolean.class).make()
  error_vector = mgmt.makePropertyKey("error_vector").dataType(String.class).make()
  percent_zero_errors_even_indices = mgmt.makePropertyKey("percent_zero_errors_evens").dataType(Float.class).make()
  percent_zero_errors_odd_indices = mgmt.makePropertyKey("percent_zero_errors_odds").dataType(Float.class).make()

  num_children = mgmt.makePropertyKey("num_children").dataType(Integer.class).make()
  num_selections = mgmt.makePropertyKey("num_selections").dataType(Integer.class).make()
  num_ancestry_children = mgmt.makePropertyKey("num_ancestry_children").dataType(Integer.class).make()

  minimal_contribution_prop = mgmt.makePropertyKey('minimal_contribution').dataType(Boolean.class).make()

  // Gene Node Properties
  gene_content = mgmt.makePropertyKey("content").dataType(String.class).make() // A map of all the information except the tracking
  gene_position = mgmt.makePropertyKey("position").dataType(Integer.class).make() // zero-indexed position of the gene in its genome

	// Vertex Labels
  individual = mgmt.makeVertexLabel('individual').make()
  run = mgmt.makeVertexLabel('run').make()
  gene = mgmt.makeVertexLabel('gene').make()

  // Edge properties
  parent_type = mgmt.makePropertyKey("parent_type").dataType(String.class).make()
  LD_dist_prop = mgmt.makePropertyKey("LD_dist").dataType(Integer.class).make()
  gene_transfer_method = mgmt.makePropertyKey("operations").dataType(String.class).make()

  // Indexing
  successfulIndex = mgmt.buildIndex("successfulIndex", Vertex.class).addKey(successfulProperty).indexOnly(run).buildCompositeIndex()
  uuidIndex = mgmt.buildIndex("uuidIndex",  Vertex.class).addKey(uuid).unique().buildCompositeIndex()
  generationTotalError = mgmt.buildIndex('generationTotalError', Vertex.class).addKey(generation).addKey(total_error).buildMixedIndex("search")
  selectionsIndex = mgmt.buildIndex('selectionsIndex', Vertex.class).addKey(num_children).addKey(num_selections).addKey(num_ancestry_children).buildMixedIndex("search")
  positionIndex = mgmt.buildIndex('positionIndex', Vertex.class).addKey(gene_position).buildCompositeIndex()
  mgmt.commit()
  //println("Done with setting keys.")
}


/* Takes an individual represented as a map from parsed EDN.
 * Takes a graph
 * Takes a traversal of that graph.
 * Addes the individual to the graph and adds edges to its parent if it has
 * any. If it does, they should be loaded in the graph before calling this
 * method.
 *
 * WARNING: This code currently crashes if passed an individual that has
 * a nil genome.
 */
addIndividualToGraph = { individual, graph, traversal ->

  try {
    /* these are the keys that we care about in the map */
    // TODO: what happens when the map is missing (one of) the keys we want?

    uuid              = individual[Keyword.newKeyword("uuid")].toString()
    generation        = individual[Keyword.newKeyword("generation")]
    genetic_operators = Printers.printString(individual[Keyword.newKeyword("genetic-operators")])
    plush_genome      = individual[Keyword.newKeyword("genome")]
    plush_genome_string = Printers.printString(plush_genome)
    total_error       = individual[Keyword.newKeyword("total-error")]
    errors            = Printers.printString(individual[Keyword.newKeyword("errors")])
    successful        = total_error == 0

    // add the vertex
    newVertex = graph.addVertex (
      label, "individual",
      // "run_uuid", runUUID, <-- why would we want the not-very-useful run UUID on *every node*!
      "uuid", uuid,
      "generation", generation,
      // "location", fields[2].toInteger() <-- The edn export currently doesn't produce this.
      // We should use UUIDs OR generation:location. Both server to uniquely indentify a node
      // inside a run and we don't need both.
      "genetic_operators", genetic_operators,
      // "plush_genome_size" <-- TODO add this to EDN export
      "plush_genome", plush_genome_string,
      "total_error", total_error,
      "error_vector", errors)

    // connect the parents
    tracingK = Keyword.newKeyword("tracing")
    parentK = Keyword.newKeyword("parent")
    positionK = Keyword.newKeyword("position")
    operatorK = Keyword.newKeyword("operator")
    changesK = Keyword.newKeyword("changes")
    randomK = Keyword.newKeyword("random")

    parentUUIDs = individual[Keyword.newKeyword("parent-uuids")]
    parents = parentUUIDs.collect { uuid ->
      parent = g.V().has('uuid', uuid).next()
      parent.addEdge('parent_of', newVertex)
      return parent
    }
    // add the gene nodes
    geneNodes = plush_genome.eachWithIndex { gene, index ->

      tracingInfo = gene[tracingK]
      geneString = Printers.printString( gene.findAll {it.getKey() != tracingK} )

      newGene = graph.addVertex(
        label, "gene",
        "content", geneString,
        "position", index)

      newVertex.addEdge('contains', newGene)

      // connect the gene to its source
      if ( tracingInfo[changesK] != randomK ){
        // debugStatus("connecting gene to parent")
        // debugStatus("changes: ${tracingInfo[changesK]}")
        // debugStatus(tracingInfo)

        // parentUUID = parentUUIDs[(int) tracingInfo[parentK]] // null in gen0, but then all the operators will be :random
        // debugStatus("parentUUID ${parentUUID}")
        parent = parents[(int) tracingInfo[parentK]]
        positionInParent = (int) tracingInfo[positionK] // null in gen0
        // debugStatus("positionInParent ${positionInParent}")

        // parentGene = g.V().has('uuid', parentUUID).outE().hasLabel('contains').inV().has('position', positionInParent).next()
        parentGene = inject(parent).out('contains').has('position', positionInParent).next()
        // parentGene = parent.vertices(Direction.OUT, 'contains').findAll { g -> g.value('position') == positionInParent}

        edge = parentGene.addEdge('creates', newGene)
        // debugStatus("added edge")
        edge.property('operations', Printers.printString(tracingInfo[changesK]))
        // debugStatus("set edge property to ${Printers.printString(tracingInfo[changesK])}")
        if ( Printers.printString(tracingInfo[changesK]) == null){
          debugStatus("found a null")
        }
      }
    }

    return [generation, successful]
  } catch (Exception e) {
    f = new PrintWriter('/tmp/stderr')
    f.println("caught  ${e.getClass()} processing an individual.")
    e.printStackTrace(f)
    f.println("individual ${individual}\ngraph ${graph}")
    f.close()
    System.exit(2)
  }

}


parseEdnFile = { graph, zippedEdnFile ->
  g = graph.traversal()

  //println("We're in the parse section!")

  fileStream = new FileInputStream(zippedEdnFile)
  gzipStream = new GZIPInputStream(fileStream)
  inputReader = new InputStreamReader(gzipStream)
  bufferedReader = new BufferedReader(inputReader)
  // debugStatus("BufferedReader is Readable: ${bufferedReader instanceof java.lang.Readable}")
  // parsable = Parsers.newParsable(bufferedReader as Readable)
  parsable = Parsers.newParseable(bufferedReader)
  parser = Parsers.newParser(defaultConfiguration());
  next = { parser.nextValue(parsable) }

  debugStatus("opened file for parsing")
  individualTag = Tag.newTag("clojush", "individual")
  runTag = Tag.newTag("clojush", "run")

  // loopingCount is increased when we add an individual to the graph
  // we commit to the graph every time we reach loopingMax
  uncommittedIndividuals = 0
  final maxUncommittedIndividuals = 1000

  // A count of all items added to the graph
  totalCount = 0

  successfulRun = false
  largestGeneration = 0
  runUUID = null

  while ((current = next()) != Parser.END_OF_INPUT) {

    if ( !(current instanceof TaggedValue) ){
      println("skipped item with no tag")
    }
    else if ( current.getTag() == individualTag) {
      (generation, successful) = addIndividualToGraph(current.getValue(), graph, g)
      uncommittedIndividuals += 1
      totalCount += 1
      if ( successful ) {
        successfulRun = true
      }
      if ( generation > largestGeneration ){
        largestGeneration = generation
      }
    }
    else if ( current.getTag() == runTag) {
      runUUID = current.getValue()[Keyword.newKeyword("run-uuid")].toString()
    }
    else {
      println("skipped item with unknown tag: ${current.getTag()}")
    }

    if ((uncommittedIndividuals % maxUncommittedIndividuals) == 0){
      println("Commiting at: ${totalCount}")
      graph.tx().commit()
      uncommittedIndividuals = 0
    }
  }
  // a final commit
  graph.tx().commit()

  return [largestGeneration, successfulRun, runUUID]
}

markAncestryGenesCopiesOnly = { ancestry_list, key, value ->
  inject(ancestry_list).unfold().out('contains').repeat(
    __.property(key,value).inE('creates').has('operations',':copy').outV()
  ).iterate()
}

getGeneCounts = { vertex ->
  genes = vertex.vertices(Direction.OUT, 'contains')
  totGenes = 0
  winGenes = 0
  genes.each { v ->
    if ( v.values('copied_to_winner')){
      winGenes++;
    }
    totGenes++;
  }
  return [winning_genes: winGenes, total_genes: totGenes]
}

markNumberOfWinningGenes = { graph, lastGenIndex ->

  g = graph.traversal()
  (0..lastGenIndex).each { generation ->
    g.V().has('generation', generation).sideEffect{ traverser ->

      vertex = traverser.get()
      stats = getGeneCounts(vertex)

      if ( stats['winning_genes'] != 0){
        vertex.property('total_copied_to_winner', stats['winning_genes'])
        vertex.property('percent_copied_to_winner',  stats['winning_genes'] /(float) stats['total_genes'])
      }

    }.iterate()
    graph.tx().commit()
  }

}


addNumSelections = { graph, maxGen ->
  g = graph.traversal()

  (0..maxGen).each { gen ->
    g.V().has('generation', gen).sideEffect
    {
      num_selections = it.get().edges(Direction.OUT).size();
      it.get().property('num_selections', num_selections)
    }.iterate();
    graph.tx().commit();
    println gen }
}

addNumChildren = { graph, maxGen ->
  g = graph.traversal()

  (0..maxGen).each { gen ->
    g.V().has('generation', gen).sideEffect {
      edges = it.get().edges(Direction.OUT);
      num_children = edges.collect { it.inVertex() }.unique().size();
      it.get().property('num_children', num_children)
    }.iterate();
    graph.tx().commit();
    println gen }
}

genome_items = { node ->
    genome = node.value('plush_genome')
    genome.
        findAll(/\{:instruction (.+?), :close ([0-9]+)\}/).
        collect({(it =~ /\{:instruction (.+?), :close ([0-9]+)\}/)[0][1..-1]}).
        flatten() as Object[]
}

addLevenshteinDistances = { graph, maxGen ->
  g = graph.traversal()

  (0..maxGen).each { gen ->
      g.V().has('generation', gen).sideEffect { node_traverser ->
        node = node_traverser.get()
        items = genome_items(node)
        child_edges = node.edges(Direction.OUT)
        child_edges.each { child_edge ->
          child = child_edge.inVertex()
          child_items = genome_items(child)
          dist = MethodRankHelper.damerauLevenshteinDistance(items, child_items)
          child_edge.property('DL_dist', dist)
        }
      }.iterate()
      graph.tx().commit()
      println gen
  }
}


addRunNode = { graph, runUUID, runFileName, successful, maxGen ->
  runVertex = graph.addVertex(label, "run", "run_uuid", runUUID, "data_file", runFileName, "successful", successful, "max_generation", maxGen)
  graph.tx().commit()
}

loadEdn = { propertiesFileName, ednDataFile ->

  start = System.currentTimeMillis()

  debugStatus("creating database")
  // TODO add check for missing file. The error that TitanFactory gives is non-obvious
  graph = TitanFactory.open(propertiesFileName)

  debugStatus("setting properties and keys on graph")
  createPropertiesAndKeys(graph)

  debugStatus("parsing EDN file")
  (maxGen, successful, runUUID) = parseEdnFile(graph, ednDataFile)

  // TODO addLevenshteinDistances(graph, maxGen)

  if (successful){

    debugStatus('adding gene counts')
    g = graph.traversal()
    ancL = []
    g.V().has('total_error',0).fill(ancL)

    debugStatus('marking genes as copied')
    markAncestryGenesCopiesOnly(ancL, 'copied_to_winner', true)
    graph.tx().commit()

    debugStatus('storing counts of genes')
    markNumberOfWinningGenes(graph, maxGen)
    graph.tx().commit()
  }

  debugStatus("adding num selections and num children")
  addNumSelections(graph, maxGen)
  addNumChildren(graph, maxGen)

  // If we put this before addNumSelections, etc., can we pull
  // maxGen out of the run node and not need to pass it as an
  // argument to those functions?
  debugStatus("adding run node")
  path = FileSystems.getDefault().getPath(ednDataFile)
  runFileName = path.getFileName().toString()
  addRunNode(graph, runUUID, runFileName, successful, maxGen)

  println "Loading took (ms): " + (System.currentTimeMillis() - start)

  return graph
}

println("The necessary functions are now loaded.")

println("To load an EDN file use a call like:\n\
\tgraph = loadEdn('run0.properties', 'run0.edn.gz')\n\
\tg = graph.traversal()\n\
where you replace 'run0.properties' with the name of your properties file\n\
and 'run0.edn.gz' with the path to your compressed EDN file.\n\
Paths may be relative or absolute.")
