
parser = Parsers.newParser(defaultConfiguration())

instruction_key = Keyword.newKeyword('instruction')
close_key = Keyword.newKeyword('close')

plotHelper = { dotWriter, ordered_genome, name, vertex_closure, color_closure, style ->

  dotWriter.openSubgraph("cluster_$name", style)

  for ( i = 0; i < ordered_genome.size(); i++){
    // println i
    vertex = vertex_closure(ordered_genome[i])
    // println vertex
    uuid = vertex.value('uuid')
    position = vertex.value('position')

    content_string = vertex.value('content')
    content_map = parser.nextValue(Parsers.newParseable(content_string))
    instruction = Printers.printString(content_map[instruction_key]).replace('\\', '\\\\')
    close = Printers.printString(content_map[close_key])

    fillcolor = color_closure(ordered_genome[i])
    if ( vertex.values('copied_to_winner').size() == 0){
     shape = "rectangle"
    }
    else {
      shape = "oval"
    }
    dotWriter.writeNode(uuid, [label: "\"$position: $instruction, $close\"",
                         style: "filled",
                         shape: shape,
                         fillcolor: fillcolor])


    if ( i < ordered_genome.size() - 1){
      next_uuid = vertex_closure(ordered_genome[i+1]).value('uuid')
      dotWriter.writeEdge(uuid, next_uuid)
    }

  }
  dotWriter.closeSubgraph()
}

produceDot = { traversal, node_id, dotfile ->

  FileWriter fileWriter = new FileWriter(dotfile)
  def dot = new DotWriter(fileWriter, -1, [node: [shape: "rectangle"]])

  // draw the edges
  traversal.V(node_id)
  .out('contains').as('child_gene')
  .in('creates').as('parent_gene')
  .select('child_gene', 'parent_gene')
  .sideEffect{ it ->

    def map = it.get()

    def parent = map['parent_gene']
    def parent_uuid = parent.value('uuid')

    def child = map['child_gene']
    def child_uuid = child.value('uuid')

    dot.writeEdge(parent_uuid, child_uuid)

  }.iterate()

  // group by parent
  traversal.V(node_id).in('parent_of').sideEffect{ traverser ->
    def parent = traverser.get()
    parentLocation = parent.value('location')
    parentGeneration = parent.value('generation')

    Iterator genome = parent.vertices(Direction.OUT, 'contains')

    arr = []
    genome.each { arr << it} // There's probably a better way to "convert" an Iterator to a List

    sortedGenome = arr.sort{ it.value('position')}

    plotHelper(dot,
                   sortedGenome,
                   "$parentLocation",
                   {it},
                   {"white"},
                   [edge:[style: "invis"],
                    graph:[label: "\"$parentGeneration:$parentLocation\""]])
  }.iterate()

  // group by child
  def child = traversal.V(node_id).next()
  int childGeneration = child.value('generation')
  int childLocation = child.value('location')

  List child_genome = []
  traversal.V(node_id).out('contains').order().by('position', incr).fill(child_genome)

  plotHelper(dot,
                 child_genome,
                 "child",
                 {it},
                 { gene ->
                   if ( gene.value('changes') == '[]'){
                     "white"
                   }
                   else {
                     "orange"
                   }
                 },
                 [edge: [style:"invis"],
                  graph: [label: "\"$childGeneration:$childLocation\""]])
  dot.close()
}


sketchAncestor = { traversal, target_id, source_id, dot_file ->

  // def target = traversal.V(target_id).next()
  Vertex source = traversal.V(source_id).next()
  int source_generation = source.value('generation')
  List source_genes = []
  inject(source).out('contains').order().by('position', incr).fill(source_genes)

  def subgraph = traversal.V(target_id).out('contains').repeat(
    __.inE('creates').subgraph('sg').outV().dedup()).until(__.in('contains').has('generation', source_generation))
  .cap('sg').next()

  def subtraversal = subgraph.traversal()

  // this subgraph looks like this:
  //   *   *   *  ...  *
  //   |   |   |  ...  |
  //   *   *   *  ...  *
  //   |   |   |  ...  |
  //   :   :   :  ...  :
  //
  //   |   |   |       |
  //   *   *   *  ...  *

  List target_genes = []
  traversal.V(target_id).out('contains').order().by('position',incr).fill(target_genes)
  List target_gene_changed = (0..<(target_genes.size())).collect{false} // initialize a list so that we refer to slots by index

  // find out which genes changed
  // target_gene_changed = target_genes.collect{ gene ->
  //   String uuid = gene.value('uuid')
  //   subtraversal.V().has('uuid', uuid).until(values('changes').is(neq('[]'))).repeat(__.in()).count().next() != 0
  // }

  // pair the target and genes with their source (CacheVertex) and with their "changed" boolean

  triples = target_genes.collect{ gene ->

    String uuid = gene.value('uuid')
    boolean changed = subtraversal.V().has('uuid', uuid).until(values('changes').is(neq('[]'))).repeat(__.in()).count().next() != 0

    // Vertex parent_gene = subtraversal.V().has('uuid', uuid).until(or(values('changes').is(neq('[]')), inE().count().is(0))).repeat(__.in()).next()
    Vertex parent_gene = subtraversal.V().has('uuid', uuid).until(inE().count().is(0)).repeat(__.in()).next()

    return [gene: gene,
            parent_gene: parent_gene,
            changed: changed]
  }

  println(triples)

  FileWriter fileWriter = new FileWriter(dot_file)
  DotWriter dot = new DotWriter(fileWriter, -1, [:])

  // draw the edges
  triples.each{ map ->
    parent_uuid = map['parent_gene'].value('uuid')
    child_uuid = map['gene'].value('uuid')
    dot.writeEdge(parent_uuid, child_uuid)
  }


  // group & fill out the parent nodes
  dot.openSubgraph("cluster_0", [:])
  for ( i = 0; i < source_genes.size() ; i++){
    vertex = source_genes[i]
    uuid = vertex.value('uuid')
    position = vertex.value('position')
    instruction = vertex.value('content')
    dot.writeNode(uuid, [label: "\"$position, $instruction\""])

    if ( i < source_genes.size() - 1){
      next_uuid = source_genes[i+1].value('uuid')
      dot.writeEdge(uuid, next_uuid)
    }
  }
  dot.closeSubgraph()

  // group & fill out the child nodes
  dot.openSubgraph("cluster_1", [:])
  for ( i = 0; i < triples.size(); i++) {
    def vertex = triples[i]['gene']
    uuid = vertex.value('uuid')
    position = vertex.value('position')
    instruction = vertex.value('content')
    if ( triples[i]['changed'] == "[]") {
      fillcolor = "white"
    }
    else {
      fillcolor = "\"orange\""
    }
    dot.writeNode(uuid,
                  [ label: "\"$position, $instruction\"",
                    style: "filled",
                    fillcolor: fillcolor])

    if ( i < target_genes.size() - 1){
      next_uuid = target_genes[i+1].value('uuid')
      dot.writeEdge(uuid, next_uuid)
    }
  }
  dot.closeSubgraph()

  dot.close()

}



