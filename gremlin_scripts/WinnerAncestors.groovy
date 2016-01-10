// Uncomment these first two lines if you're running this for the first time and don't have the
// graph loaded.

// graph = TitanFactory.open('./db.properties')
// g = graph.traversal()

// Uncomment these two lines if you don't have the ancestral subgraph computed.

// ancG = g.V().has('total_error', 0).repeat(__.inE().subgraph('sg').outV()).times(191).cap('sg').next()
// anc = ancG.traversal()

// The target node line:format
//	"87:719" [shape=rectangle, width=4, style=filled, fillcolor="0.5 1 1"];
void printNode(fr, maxError, n) {
     width = n['nc']/100
     hue = Math.log(n['te']+1)/Math.log(maxError+1)
     color = "\"${hue} 1 1\""
     
     name = '"' + n['id'] + '"'
     
     params = "[shape=rectangle, width="
     params = params + width + ", style=filled, fillcolor="
     params = params + color + "]"
     
     fr.println(name  + " " + params + ";")
}

// The target edge line format:
//	"82:393" -> "83:619";
void printEdge(fr, e) {
     fr.println('"' + e['parent'] + '"' + " -> " + '"' + e['child'] + '"' + ";")
}

maxError = anc.V().values('total_error').max().next()

// Open the DOT file, print the DOT header info,
// and set up the defaults for nodes and edges.
fr = new java.io.FileWriter("/tmp/ancestors.dot")
fr.println("digraph G {")
fr.println('node[shape=point, width=0.15, height=0.15, fillcolor="white", penwidth=1, label=""];')
fr.println('edge[arrowsize=0.5, color="grey"];')

// Process all the vertices
anc.V().
	as('v').values('uuid').as('id').
	select('v').values('numChildren').as('nc').
	select('v').values('total_error').as('te').
	select('id', 'te', 'nc').
	sideEffect{ printNode(fr, maxError, it.get()) }.
	iterate(); null

// Process all the edges
anc.E().
	as('e').outV().values('uuid').as('parent').
	select('e').inV().values('uuid').as('child').
	select('parent', 'child').
	sideEffect{ printEdge(fr, it.get()) }.
	iterate(); null

// Wrap up the DOT syntax and close
fr.println("}")
fr.close()
