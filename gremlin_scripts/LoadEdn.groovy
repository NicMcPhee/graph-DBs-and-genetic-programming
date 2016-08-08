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

import org.apache.tinkerpop.gremlin.structure.Edge

printDebugInfo = true

debugStatus = { str ->
  if (printDebugInfo){
    println("debug: ${str}")
  }
}

parser = Parsers.newParser(defaultConfiguration())

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
 */
addIndividualToGraph = { individual, graph, traversal ->

  try {
    /* these are the keys that we care about in the map */
    // TODO: what happens when the map is missing (one of) the keys we want?

    uuid              = individual[Keyword.newKeyword("uuid")].toString()
    generation        = individual[Keyword.newKeyword("generation")]
    location          = individual[Keyword.newKeyword("location")]
    genetic_operators = Printers.printString(individual[Keyword.newKeyword("genetic-operators")])
    plush_genome      = individual[Keyword.newKeyword("genome")]
    plush_genome_string = Printers.printString(plush_genome)
    total_error       = individual[Keyword.newKeyword("total-error")]
    errors            = Printers.printString(individual[Keyword.newKeyword("errors")])
    successful        = total_error == 0
    is_random_replacement = individual[Keyword.newKeyword("is-random-replacement")]

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
      "plush_genome_size", plush_genome.size(),
      // "plush_genome", plush_genome_string,
      "total_error", total_error,
      "location", location,
      "error_vector", errors)

    if ( is_random_replacement != null){
      newVertex.property('is_random_replacement', is_random_replacement)
    }

    // connect the parents
    /*
    tracingK = Keyword.newKeyword("tracing")
    parentK = Keyword.newKeyword("parent")
    positionK = Keyword.newKeyword("position")
    operatorK = Keyword.newKeyword("operator")
    changesK = Keyword.newKeyword("changes")
    randomK = Keyword.newKeyword("random")
    */

    uuidKeyword = Keyword.newKeyword("uuid")
    parentUuidKeyword = Keyword.newKeyword("parent-uuid")

    parentUUIDs = individual[Keyword.newKeyword("parent-uuids")]
    parents = parentUUIDs.collect { uuid ->
      parent = traversal.V().has('uuid', uuid).next()
      parent.addEdge('parent_of', newVertex)
      return parent
    }

    def parentGenes = []
    inject(parents).unfold().out('contains').fill(parentGenes)

    // add the gene nodes
    geneNodes = plush_genome.eachWithIndex { gene, index ->

      def gene_uuid = gene[uuidKeyword]
      def gene_parent_uuid = gene[parentUuidKeyword]
      def gene_string = Printers.printString( gene.findAll {it.getKey() != parentUuidKeyword && it.getKey() != uuidKeyword} )

      def newGene = graph.addVertex(
        label, "gene",
        "uuid", gene_uuid.toString(),
        "content", gene_string,
        "position", index)

      newVertex.addEdge('contains', newGene)

      // connect the gene to its source
      if ( gene_parent_uuid != null ){

        def parentGene = inject(parentGenes).unfold().has('uuid', gene_parent_uuid.toString()).next()
        parentGene.addEdge('creates', newGene)

      }
    }

    return [generation, successful]
  } catch (Exception e) {
    println("caught  ${e.getClass()} processing an individual.")
    println("individual ${individual}\ngraph ${graph}")
    throw e
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
      graph.tx().commit()
    }
    else if ( current.getTag() == runTag) {
      runUUID = current.getValue()[Keyword.newKeyword("run-uuid")].toString()
    }
    else {
      println("skipped item with unknown tag: ${current.getTag()}")
    }

    if ((uncommittedIndividuals % maxUncommittedIndividuals) == 0){
      println("Now past: ${totalCount}")
      // graph.tx().commit()
      // uncommittedIndividuals = 0
    }
  }
  // a final commit
  graph.tx().commit()

  return [largestGeneration, successfulRun, runUUID]
}

// markAncestryGenesCopiesOnly = { ancestry_list, key, value ->
//   inject(ancestry_list).unfold().out('contains').repeat(
//     __.property(key,value).inE('creates').has('operations',':copy').outV()
//   ).iterate()
// }


markGeneChangesSideEffect = { traverser ->

  def gene = traverser.get()
  def owner = gene.vertices(Direction.IN, 'contains')[0]
  def creator = gene.vertices(Direction.IN, 'creates')[0]
  // println("$gene, $owner, $creator")

  Map content = parser.nextValue(Parsers.newParseable(gene.value('content')))
  Map creator_content = parser.nextValue(Parsers.newParseable(creator.value('content')))

  Set keys = content.keySet() + creator_content.keySet() // Thanks to Elliot Beach (@e-beach) for this line
  List changed_keys = keys.findAll{ k -> content[k] != creator_content[k] } as List
  changed_keys.sort() // avoid having some with [:close :silent] and some with [:silent :close]

  gene.property('changes', Printers.printString(changed_keys))
}

markGeneChanges = { graph, lastGenIndex ->
  /* Takes the graph and the lastGenIndex.
   * The function compairs each gene in the graph to
   * its parent gene and places an indicator on the vertex to show
   * what changed.
   * The lastGenIndex allows us to loop over the generations which provides
   * a convenient way to periodicly commit.
   */
  def g = graph.traversal()

  // (0..lastGenIndex).each { generation ->
  //   g.V().has('generation', generation).out('contains')
  //   .sideEffect(markGeneChangesSideEffect).iterate()
  //   graph.tx().commit()
  // }
  (0..lastGenIndex).each { generation ->
    g.V().has('generation', generation).out('contains')
    .choose(
      __.in('creates'),
      __.sideEffect(markGeneChangesSideEffect),
      __.property('changes', ":new")).iterate()

    graph.tx().commit()
    print "$generation, "
    System.out.flush()
  }
  print '\n'

}

markAncestryGenesCopiesOnly = { ancestry_list, key, value ->
  inject(ancestry_list).unfold().out('contains').repeat(
    __.property(key,value).has('changes','[]').inE('creates').outV()
    // We only proceed up the graph when the gene is no different from its parent
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

  debugStatus("marked generation: ")
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
    print "$generation, "
    System.out.flush()
  }
  print '\n'

}


addNumSelections = { graph, maxGen ->
  g = graph.traversal()

  (0..maxGen).each { gen ->
    g.V().has('generation', gen).sideEffect
    {
      num_selections = it.get().edges(Direction.OUT, 'parent_of').size();
      it.get().property('num_selections', num_selections)
    }.iterate();
    graph.tx().commit()
    print "$gen, "
    System.out.flush()
  }
  println()
}

addNumChildren = { graph, maxGen ->
  g = graph.traversal()

  (0..maxGen).each { gen ->
    g.V().has('generation', gen).sideEffect {
      edges = it.get().edges(Direction.OUT, 'parent_of');
      num_children = edges.collect { it.inVertex() }.unique().size();
      it.get().property('num_children', num_children)
    }.iterate();
    graph.tx().commit()
    print "$gen, "
    System.out.flush()
  }
  println()
}

genome_items = { node ->
    genome = node.value('plush_genome')
    genome.
        findAll(/\{:instruction (.+?), :close ([0-9]+)\}/).
        collect({(it =~ /\{:instruction (.+?), :close ([0-9]+)\}/)[0][1..-1]}).
        flatten() as Object[]
}

damerauLevenshteinDistance = { ArrayList s, ArrayList t ->

  /*
   * This function is a straight foward ADAPTION of the helper
   * method in org.codehaus.groovy.runtime.MethodRankHelper
   *
   * That method is copyright 2003-2016 The Apache Software Foundation.
   * Licensed under the Apache License, Version 2.0 (the "License");
   * you may not use this function except in compliance with the License.
   * You may obtain a copy of the License at
   *
   * http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
   *
   * The author was Hjalmar Ekengren, and the implemenation was based on
   * Chas Emerick's implementation of Levenshtein Distance implementation
   * for jakarta commons.
   */

  int DL_SUBSTITUTION = 1
  int DL_DELETE = 1 //This is also the cost for a insert
  int DL_TRANSPOSITION = 1

  if (s == null || t == null) {
    throw new IllegalArgumentException("ArrayLists must not be null")
  }

  int n = s.size() // length of s
  int m = t.size() // length of t

  if (n == 0) {
    return m
  } else if (m == 0) {
    return n
  }

  int[][] vals = new int[3][n + 1]


  _d = [] //placeholder to assist in rotating vals

  // indexes into arrays s and t
  // i - iterates through s
  // j - iterates through t

  Object t_j = null // jth object of t

  int cost = 0

  for (i = 0; i <= n; i++) {
    vals[1][i] = i * DL_DELETE
  }


  for (j = 1; j <= m; j++) {
    t_j = t[j - 1]
    vals[0][0] = j * DL_DELETE

    for (i = 1; i <= n; i++) {
      cost = s[i - 1].equals(t_j)? 0 : DL_SUBSTITUTION
      // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
      vals[0][i] = Math.min(Math.min(vals[0][i - 1] + DL_DELETE, vals[1][i] + DL_DELETE), vals[1][i - 1] + cost)

      //Check for transposition
      if(i > 1 && j > 1 && s[i -1].equals(t[j -2]) && s[i- 2].equals(t_j)){
        vals[0][i] = Math.min(vals[0][i], vals[2][i-2] + DL_TRANSPOSITION)
      }
    }

    // rotate all value arrays upwards(older rows get a higher index)
    _d = vals[2]
    vals[2] = vals[1]
    vals[1] = vals[0]
    vals[0] = _d
  }

  return vals[1][n]
}

addLevenshteinDistances = { graph, maxGen ->

  /*
   * This method computes the LD distance between all [parent,child] pairs
   * and stores it on the 'parent_of' edge between them. Instead of just treating
   * each instruction map as a character, we linearize the genomes so that they
   * look like $gene0_key0 gene0_{key1} ... gene0_{key{N_0}} gene1_{key0} gene1_{key1} ... gene1_{key{N_1}} ... geneM_{gene{N_M}}$
   * We then compute the LD distance on those lists. All our runs so far have
   * the same keys in every instruction map (a.k.a gene), but this code should
   * continue to work properly if that changes.
   *
   */
  def g = graph.traversal()

  (0..maxGen).each { gen ->

    g.V().has('generation', gen).sideEffect{ traversal ->

      Vertex parent = traversal.get()
      List raw_parent_genes = inject(parent).out('contains').order().by('position', incr).values('content').fold().next()
      Iterator parent_of_edges = parent.edges(Direction.OUT,'parent_of')

      if ( raw_parent_genes.size() == 0){
        parent_of_edges.each { edge ->
          Vertex child = edge.inVertex()
          int dl_dist = inject(child).outE('contains').count().next()
          edge.property('dl_dist', dl_dist)
        }
      }
      else {
        linearizeMap = { map ->
            List keys = map.keySet() as List
            keys.sort()
            keys.collect{ k -> map[k]}
        }

        List linearized_parent_genes = raw_parent_genes.collect{ raw_gene ->
          parser.nextValue(Parsers.newParseable(raw_gene))
        }.collectMany(linearizeMap)


        parent_of_edges.each { edge ->
          Vertex child = edge.inVertex()
          List raw_child_genes = inject(child).out('contains').order().by('position', incr).values('content').fold().next()

          List linearized_child_genes = raw_child_genes.collect{ raw_gene ->
            parser.nextValue(Parsers.newParseable(raw_gene))
          }.collectMany(linearizeMap)

          int dl_dist = damerauLevenshteinDistance(linearized_parent_genes, linearized_child_genes)
          edge.property('dl_dist', dl_dist)
        }
      }
    }.iterate()
    graph.tx().commit()
    print "$gen, "
    System.out.flush()
  }
  println()
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
  debugStatus("finished parsing EDN file")

  debugStatus("adding DL distances")
  addLevenshteinDistances(graph, maxGen)
  debugStatus('finished adding DL distances')

  debugStatus('marking gene changes')
  markGeneChanges(graph, maxGen)
  debugStatus('marked gene changes')

  if (successful){
    debugStatus('adding successful-run-only information')

    g = graph.traversal()
    ancL = []
    g.V().has('total_error',0).fill(ancL)
    debugStatus('found winners')

    markAncestryGenesCopiesOnly(ancL, 'copied_to_winner', true)
    graph.tx().commit()
    debugStatus('marked genes as copied')

    markNumberOfWinningGenes(graph, maxGen)
    graph.tx().commit()
    debugStatus('stored counts of genes')
  }

  debugStatus("adding num selections and num children")
  addNumSelections(graph, maxGen)
  addNumChildren(graph, maxGen)

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
