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

    Iterator genome = parent.vertices(Direction.OUT, 'contains')

    arr = []
    genome.each { arr << it}

    sortedGenome = arr.sort{ it.value('position')}

    dot.openSubgraph("cluster_$parentLocation", [edge:[style:"invis"]])

    for ( i = 0; i < sortedGenome.size(); i++){
      vertex = sortedGenome[i]
      uuid = vertex.value('uuid')
      position = vertex.value('position')
      instruction = vertex.value('content')
      dot.writeNode(uuid, [label: "\"$position, $instruction\""])

      if ( i < sortedGenome.size() - 1){
        next_uuid = sortedGenome[i+1].value('uuid')
        dot.writeEdge(uuid, next_uuid)
      }
    }
    dot.closeSubgraph()

  }.iterate()


  // group by child
  dot.openSubgraph("cluster_child", [edge:[style:"invis"]])

  List child_genome = []
  traversal.V(node_id).out('contains').order().by('position', incr).fill(child_genome)

  for ( i = 0; i < child_genome.size(); i++){
    def vertex = child_genome[i]
    uuid = vertex.value('uuid')
    position = vertex.value('position')
    instruction = vertex.value('content')
    changes = vertex.value('changes')
    if ( changes == "[]") {
      fillcolor = "white"
    }
    else {
      fillcolor = "\"orange\""
    }
    dot.writeNode(uuid,
                  [ label: "\"$position, $instruction\"",
                    style: "filled",
                    fillcolor: fillcolor])

    if ( i < child_genome.size() - 1){
      next_uuid = child_genome[i+1].value('uuid')
      dot.writeEdge(uuid, next_uuid)
    }
  }
  dot.closeSubgraph()

  dot.close()
}


/*
==>v[83636336]
gremlin> anc.V().has('generation', 6).where(__.inE().values('dl_dist').is(4)).where(__.inE().count().is(1)).in().in()
==>v[51605560]
*/
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



