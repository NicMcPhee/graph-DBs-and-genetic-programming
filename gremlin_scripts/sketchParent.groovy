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
    dot.writeNode(uuid, [label: "\"$position, $instruction\""])

    if ( i < child_genome.size() - 1){
      next_uuid = child_genome[i+1].value('uuid')
      dot.writeEdge(uuid, next_uuid)
    }
  }
  dot.closeSubgraph()

  dot.close()
}
