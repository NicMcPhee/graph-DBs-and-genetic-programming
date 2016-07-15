
void printNode(dot, nodeData){

  width = nodeData['num_selections']/50
  height = nodeData['num_ancestry_children']/10

  name = nodeData['uuid']

  nodeLabel0 = nodeData['percent_copied_to_winner']
  nodeLabel1 = nodeData['total_copied_to_winner']

  attrs = [shape: "rectangle",
           width: width,
           height: height,
           style: "filled",
           fillcolor: "white",
           label: "\"$nodeLabel0, $nodeLabel1\""]

  dot.writeNode(name, attrs)

}

void printEdge(dot, edgeData){

  transparency = ((edgeData['num_ancestry_children']*20)+30)
  rounded = (int) Math.round(transparency);
  if (rounded > 255) {
    rounded = 255
  }
  trans = Integer.toHexString(rounded).toUpperCase();

  switch (edgeData['genetic_operators']) {
  case "[:alternation :uniform-mutation]":
    c = "#000000"+trans
    sty = "solid"
    break
  case ":alternation":
    c = "#000000"+trans
    sty = "dashed"
    break
  case ":uniform-mutation":
    c = "#FF4500"+trans
    sty = "solid"
    break
  case ":uniform-close-mutation":
    c = "#FF4500"+trans
    sty = "dashed"
    break
  default:
    c = "green"
    sty = "solid"
  }

  parent = edgeData['parent']
  child = edgeData['child']
  attrs = [color: "\"${c}\"",
           penwidth: 1,
           style: "\"${sty}\""]

  dot.writeEdge(parent, child, attrs)
}

def get_ancestors(ancestor_list){

  inject(ancestor_list).unfold().repeat(
    __.as('child').outE('contains').subgraph('sg')
    .inV().inE('creates').subgraph('sg')
    .select('child').unfold()
    .inE('parent_of').where(__.outV().has('total_copied_to_winner', gt(0))).subgraph('sg')
    .outV().dedup()
  ).cap('sg').next().traversal()
}

def statsSideEffect (traverser){

  vertex = traverser.get()

  // count number of children <em>in this ancestry tree</em>
  edges = vertex.edges(Direction.OUT, 'parent_of')
  num_ancestry_children = edges.collect{ edge -> edge.inVertex() }.unique().size()
  vertex.property('num_ancestry_children', num_ancestry_children)

}

status = { str ->
  println("status: $str")
}

loadAncestry = { propertiesFileName, dotFileName ->

  status("setting timer")
	start = System.currentTimeMillis()

  status("loading database")
	graph = TitanFactory.open(propertiesFileName)
	g = graph.traversal()

  status("finding run UUID")
	run_uuid = g.V().hasLabel('run').values('run_uuid').next()

  status('finding the list of winners')
	ancestor_list = []
  g.V().has('total_error',0).fill(ancestor_list)
  status("ancestor_list.size() is ${ancestor_list.size()}")

  // anc = AncestryFilters.filterByGenesCopiesOnly(ancestor_list)
  anc = get_ancestors(ancestor_list)
  status("$anc")

  maxGen = anc.V().values('generation').max().next()
	maxError = anc.V().values('total_error').max().next()
  status("maxGen $maxGen, maxError $maxError")

  status("adding num_ancestry_children")
  anc.V().sideEffect(statsSideEffect).iterate()

  status("opening dot file for writing")
  dot = new DotWriter(dotFileName, maxGen, [node: [shape: "point",
                                                   width: 0.15,
                                                   height: 0.15,
                                                   fillcolor: "\"white\"",
                                                   penwidth: 1,
                                                   label: "\"\""],
                                            edge: [arrowsize: 0.5,
                                                   color: "\"grey\"",
                                                   penwidth: 1,
                                                   style: "\"solid\""]])


  status('printing nodes')
  anc.V().hasLabel('individual')
  .valueMap('uuid', 'num_selections', 'total_error', 'num_ancestry_children', 'percent_copied_to_winner', 'total_copied_to_winner')
  .sideEffect{printNode(dot, it.get().collectEntries{key, value -> [key, value[0]]})}.iterate()

  status('printing edges')
  anc.E().hasLabel('parent_of')
  .as('e')
  .inV().values('num_selections').as('num_selections')
  .select('e').inV().values('num_ancestry_children').as('num_ancestry_children')
  .select('e').outV().values('uuid').as('parent')
  .select('e').inV().values('uuid').as('child')
  .select('e').inV().values('genetic_operators').as('genetic_operators')
  .select('parent','child','genetic_operators', 'num_selections', 'num_ancestry_children')
  .sideEffect{ printEdge(dot, it.get())}.iterate()

  dot.close()
  status("Dot file generation took ${System.currentTimeMillis() - start} ms")

}

// print usage tips

