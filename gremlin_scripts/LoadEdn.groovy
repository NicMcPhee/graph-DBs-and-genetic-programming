// A comment here for coloring to show in file w/ gedit.
import java.io.*
import java.util.zip.GZIPInputStream
import java.nio.file.Path
import java.nio.file.FileSystems

import org.codehaus.groovy.runtime.MethodRankHelper

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

	// Vertex Labels
  individual = mgmt.makeVertexLabel('individual').make()
  run = mgmt.makeVertexLabel('run').make()

  // Edge properties
  parent_type = mgmt.makePropertyKey("parent_type").dataType(String.class).make()
  LD_dist_prop = mgmt.makePropertyKey("LD_dist").dataType(Integer.class).make()

  // Indexing
  successfulIndex = mgmt.buildIndex("successfulIndex", Vertex.class).addKey(successfulProperty).indexOnly(run).buildCompositeIndex()
  uuidIndex = mgmt.buildIndex("uuidIndex",  Vertex.class).addKey(uuid).unique().buildCompositeIndex()
  generationTotalError = mgmt.buildIndex('generationTotalError', Vertex.class).addKey(generation).addKey(total_error).buildMixedIndex("search")
  selectionsIndex = mgmt.buildIndex('selectionsIndex', Vertex.class).addKey(num_children).addKey(num_selections).addKey(num_ancestry_children).buildMixedIndex("search")
  mgmt.commit()
  //println("Done with setting keys.")
}

parseCsvFile = { graph, zippedCsvFile, runUUID ->
  g = graph.traversal()

  //println("We're in the parse section!")

  fileStream = new FileInputStream(zippedCsvFile)
  gzipStream = new GZIPInputStream(fileStream)
  inputReader = new InputStreamReader(gzipStream)
  reader = new BufferedReader(inputReader)

  theCount = 0 // How many rows have we read?
  successful = false // Was this run successful (i.e., zero error?)
  maxGen = 0 // The last (& largest) generation number in this run
  while ((line = reader.readLine()) != null) {
    if (theCount % 10000 == 0) {
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
      if (fields[9] == "") { is_rand = true }
      else { is_rand = fields[9].toBoolean()}
      */

      // The case to int here will be bad if the error values are ever
      // actually floating point values, but it works for the integer values
      // in the problems we're currently looking at.
      error_vector_values = fields[10..-1].collect { (int) (it.toFloat()) }

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
      "genetic_operators", fields[4],
      "plush_genome_size", fields[6],
      "plush_genome", fields[8],
      // For autoconstruction runs
      //"total_error", total_error, "is_random_replacement", is_rand, "error_vector", errors,
      "total_error", total_error, "error_vector", errors,
      "percent_zero_errors_evens", percent_zeros_even_indices,
      "percent_zero_errors_odds", percent_zeros_odd_indices)

      if (fields[3].length() > 5) {
        motherUuid = fields[3][4..39]
        // println "<" + motherUuid + "> ::: <" + fatherUuid + ">"
        mother = g.V().has("uuid", motherUuid).next()
        motherEdge = mother.addEdge('parent_of', newVertex)
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

  return [maxGen, successful]
}

/* Takes an individual represented as a map from parsed EDN.
 * Takes a graph
 * Takes a traversal of that graph.
 * Addes the individual to the graph and adds edges to its parent if it has
 * any. If it does, they should be loaded in the graph before calling this
 * method.
 */
addIndividualToGraph = { individual, graph, traversal ->

  /* these are the keys that we care about in the map */
  // TODO: what happens when the map is missing the (on of) the keys we want?

  uuid              = individual[Keyword.newKeyword("uuid")].toString
  generation        = individual[Keyword.newKeyword("generation")]
  genetic_operators = Printers.printString(individual[Keyword.newKeyword("genetic-operators")])
  plush_genome      = Printers.printString(individual[Keyword.newKeyword("plush-genome")])
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
    "plush_genome", plush_genome,
    "total_error", total_error,
    "error_vector", errors
  )

  parentUUIDs = individual[Keyword.newKeyword("parent-uuids")]
  if ( null != parentUUIDs ){
    parentUUIDs.each { uuid ->
      parent = g.V().has("uuid", parentUUIDs[0]).next()
      edge = parent.addEdge('parent_of', newVertex)
    }
    // edge0.property("parent_type", "mother") //<-- We don't need parent types do we?
  }

  return [generation, successful]

}


parseEdnFile = { graph, zippedEdnFile, runUUID ->
  g = graph.traversal()

  //println("We're in the parse section!")

  fileStream = new FileInputStream(zippedCsvFile)
  gzipStream = new GZIPInputStream(fileStream)
  inputReader = new InputStreamReader(gzipStream)
  parsable = Parsers.newParsable(new BufferedReader(inputReader))
  parser = Parsers.newParser(defaultConfiguration());
  next = { p.nextValue(parsable) }

  individualTag = Tag.newTag("clojush", "individual")

  // loopingCount is increased when we add an individual to the graph
  // we commit to the graph every time we reach loopingMax
  uncommittedIndividuals = 0
  final maxUncommittedIndividuals = 1000

  // A count of all items added to the graph
  totalCount = 0

  successfulRun = false
  largestGeneration = 0

  while ((current = next()) != Parser.END_OF_INPUT) {

    // Question: if datafile has a non-tagged item in the stream
    // we will crash with a "getValue() doesn't exist for type
    // map" or something like that. Should we explicitly check that
    // every time and crash gracefully/display an error?
    if ( current.getTag() == individualK) {
      (generation, successful) = addIndividualToGraph(current.getValue(), g)
      uncommittedIndividuals += 1
      totalCount += 1
      if ( successful ) {
        successfulRun = true
      }
      if ( generation > largestGeneration ){
        largestGeneration = generation
      }
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

  return [largestGeneration, successfulRun]
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

addMinimalContributionProperty = { graph, maxGen ->
  g = graph.traversal()

  (0..maxGen).each { gen ->
    g.V().has('generation', gen).sideEffect { node_traverser ->
      child = node_traverser.get()
      child_size = child.value('plush_genome_size')
      parent_edges = child.edges(Direction.IN).sort{ it.value('DL_dist') }
      unique_parents = parent_edges.collect{ it.outVertex() }.unique()
      if (unique_parents.size() == 2) {
        parent_edges = child.edges(Direction.IN).sort{ it.value('DL_dist') }
        distances = parent_edges.collect{ it.value('DL_dist') }
        parent_edges = child.edges(Direction.IN).sort{ it.value('DL_dist') }
        if (distances[0] / 10 < 0.2*child_size || distances[1] >= 2 * distances[0]) {
          // println "${child_size}, ${distances[0]}, and ${distances[1]}"
          parent_edges[1].property('minimal_contribution', true)
        }
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

loadEdn = { propertiesFileName, EdnDataFile ->

  start = System.currentTimeMillis()

  // TODO get the following in the run output
  runUUID = java.util.UUID.randomUUID()

  graph = TitanFactory.open(propertiesFileName)

  createPropertiesAndKeys(graph)

  // TODO (maxGen, successful) = parseCsvFile(graph, csvFilePath, runUUID)
  (maxGen, successful) = parseEdnFile(graph, EdnDataFile, runUUID)

  // TODO addLevenshteinDistances(graph, maxGen)
  // TODO addMinimalContributionProperty(graph, maxGen)

  addNumSelections(graph, maxGen)
  addNumChildren(graph, maxGen)

  // If we put this before addNumSelections, etc., can we pull
  // maxGen out of the run node and not need to pass it as an
  // argument to those functions?
  path = FileSystems.getDefault().getPath(csvFilePath)
  runFileName = path.getFileName().toString()
  addRunNode(graph, runUUID, runFileName, successful, maxGen)

  println "Loading took (ms): " + (System.currentTimeMillis() - start)

  return graph
}

println("The necessary functions are now loaded.")

// println("To load a CSV file use a call like:\n\
// \tgraph = loadCsv('genome_db.properties', '/Research/RSWN/lexicase/data2.csv.gz')\n\
// \tg = graph.traversal()\n\
// where you replace 'genome_db.properties' with the name of your properties file\n\
// and '/Research/RSWN/lexicase/data0.csv.gz' with the path to your compressed CSV file.")
