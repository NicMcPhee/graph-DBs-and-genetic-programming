import static us.bpsm.edn.parser.Parsers.defaultConfiguration;
import us.bpsm.edn.*;
import us.bpsm.edn.parser.*;
import us.bpsm.edn.printer.*;

parser = Parsers.newParser(defaultConfiguration())

rswnDualColor = {  error_vector_string ->

  error_vector = parser.nextValue(Parsers.newParseable(error_vector_string))

  num_cases = error_vector.size() / 2 // half are even and half are odd so we split the count

  total_error_cap = 100000
  total_error_evens = 0
  total_error_odds = 0
  num_evens_zero = 0
  num_odds_zero = 0


  error_vector.withIndex().each { pair ->

    index = pair[1]
    error = pair[0]

    if ( index % 2 == 0 ){
      total_error_evens += error
      if ( error == 0){
        num_evens_zero++
      }
    }
    else {
      total_error_odds += error
      if ( error == 0) {
        num_odds_zero++
      }
    }
  }

  total_error_evens = Math.min(total_error_evens, total_error_cap)
  total_error_odds = Math.min(total_error_odds, total_error_cap)

  percent_evens_zero = num_evens_zero * 1.0 / num_cases
  percent_odds_zero = num_odds_zero * 1.0 / num_cases


  hueEven = 1.0/6 + (5.0/6) * (1 - percent_evens_zero)
  shadeEven = 1-(Math.log(total_error_evens+1)/Math.log(total_error_cap+1))

  hueOdd = 1.0/6 + (5.0/6) * (1 - percent_odds_zero)
  shadeOdd = 1-(Math.log(total_error_odds+1)/Math.log(total_error_cap+1))

  return "\"$hueEven, 1 $shadeEven; 0.5: $hueOdd, 1, $shadeOdd\""
}

simpleAutoEncodingColor = { nodeData ->

  /*
   * This requires you to have already have colorMap loaded up before
   * this is called. colorMap should be a map where the keys are Lists
   * of 1's and 0's. e.g. [1,0,0,1,1,1,0,1]. The associated values should be
   * Lists of length three. e.g. [number, number, number] where all the numbers
   * are in [0,255]
   */

  String error_vector_string = nodeData['error_vector']
  ArrayList error_vector = parser.nextValue(Parsers.newParseable(error_vector_string))

  error_vector = error_vector.collect { num ->
    if ( 0 == num) {
      [0,0]
    }
    else if ( 0 < num && num < 3) {
      [0,1]
    }
    else if ( 3 < num && num < 6) {
      [1,0]
    }
    else {
      [1,1]
    }
  }.flatten()

  try {
    (red, green, blue) = colorMap[error_vector]
    return String.format("\"#%02x%02x%02x\"", red as int , green as int, blue as int)
  } catch (Exception e){
    println("caught an exception simpleAutoEncodingColor")
    throw e
  }
}

plainPercentErrorsZeroColor = { nodeData ->

  String error_vector_string = nodeData['error_vector']
  List error_vector = parser.nextValue(Parsers.newParseable(error_vector_string))

  int total_error = nodeData['total_error']
  int total_error_cap = 100000
  total_error = Math.min(total_error, total_error_cap)


  int num_zeros = error_vector.count{ it == 0 }
  int num_errors = error_vector.size()

  Float hue = 1.0/6 + (5.0/6) * (1 - (num_zeros / num_errors))
  Float shade= 1-(Math.log(total_error+1)/Math.log(total_error_cap+1))

  return "\"$hue, 1, $shade\""

}

printNode = { dot, nodeData ->

  // width = nodeData['num_selections']/50
  // height = nodeData['num_ancestry_children']/10
  width = nodeData['total_copied_to_winner'] / 50
  height = nodeData['num_selections'] / 50

  name = nodeData['uuid']

  generation = nodeData['generation']
  location = nodeData['location']

  // nodeLabel0 = nodeData['percent_copied_to_winner']
  // nodeLabel1 = nodeData['total_copied_to_winner']

  // fillcolor = rswnDualColor(nodeData['error_vector'])
  // fillcolor = plainPercentErrorsZeroColor(nodeData)
  // fillcolor = simpleAutoEncodingColor(nodeData)
  fillcolor = "white"

  attrs = [shape: "rectangle",
           width: width,
           height: height,
           style: "filled",
           tooltip: "\"$generation:$location\"",
           fillcolor: fillcolor]
           // label: "\"$nodeLabel0, $nodeLabel1\""]

  if ( nodeData['total_error'] == 0){
    attrs.shape="trapezium"
    attrs.height = attrs.width / 2
  }

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

  def edgeLabel = edgeData['dl_dist']

  parent = edgeData['parent']
  child = edgeData['child']
  attrs = [color: "\"$c\"",
           penwidth: 1,
           label: "\"$edgeLabel\"",
           style: "\"$sty\""]

  dot.writeEdge(parent, child, attrs)
}

get_ancestors_by_genes = { ancestor_list ->

  inject(ancestor_list).unfold().repeat(
    __.inE('parent_of').where(outV().out('contains').has('copied_to_winner').limit(1)).subgraph('sg').outV().dedup()
  ).cap('sg').next().traversal()
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
	// run_uuid = g.V().hasLabel('run').values('run_uuid').next()
  run_node = g.V().has('successful', within(true,false)).hasLabel('run').next()

  if ( run_node.value('successful') == false) {
    println("Visualization of unsuccessful EC runs is not yet supported. Aborting visualization.")
    System.exit(1)
  }

  run_uuid = run_node.value('run_uuid')
  maxGen = (int) run_node.value('max_generation')

  status('finding the list of winners')
	ancestor_list = []
  g.V().has('total_error',0).fill(ancestor_list)
  status("ancestor_list.size() is ${ancestor_list.size()}")

  anc = get_ancestors_by_genes(ancestor_list)
  status("$anc")

	// maxError = anc.V().values('total_error').max().next()
  // status("maxGen $maxGen, maxError $maxError")

  status("adding num_ancestry_children")
  anc.V().hasLabel('individual').sideEffect(statsSideEffect).iterate()
  //      ^^^^^^^^^^^^^^^^^^^^^ I'm not sure this will work a large graph

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
  .valueMap('uuid', 'num_selections', 'total_error', 'num_ancestry_children', 'percent_copied_to_winner', 'total_copied_to_winner', 'error_vector', 'generation', 'location')
  .sideEffect{printNode(dot, it.get().collectEntries{key, value -> [key, value[0]]})}.iterate()

  status('printing edges')
  anc.E().hasLabel('parent_of')
  .as('e')
  .inV().values('num_selections').as('num_selections')
  .select('e').inV().values('num_ancestry_children').as('num_ancestry_children')
  .select('e').outV().values('uuid').as('parent')
  .select('e').inV().values('uuid').as('child')
  .select('e').inV().values('genetic_operators').as('genetic_operators')
  .select('e').values('dl_dist').as('dl_dist')
  .select('parent','child','genetic_operators', 'num_selections', 'num_ancestry_children', 'dl_dist')
  .sideEffect{ printEdge(dot, it.get())}.iterate()

  dot.close()
  status("Dot file generation took ${System.currentTimeMillis() - start} ms")

}

// print usage tips

