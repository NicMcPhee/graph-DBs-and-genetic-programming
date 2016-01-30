***********************************************************************************************************************************
# Nov 9

* Notes from 11/9 unless specified otherwise.

graph = TitanFactory.open('conf/titan-berkeleyje-es.properties')

new File("fileName.csv").splitEachLine(","){fields ->
	println("Processing")
	graph.addVertex("uuid", fields[2], ...)
}

g = graph.traversal()

g.V().has('total_error', '100956.0').next() // Get vertex that has a total error of 100956

g.V(id).values() // Not sure if 'id' was a variable or an actual ID

g.V(*Some vertex*).valueMap() // Returns all labeled values for *Some vertex*, uses Saturn in example

***********************************************************************************************************************************

Copied & Pasted From Titan:db
http://s3.thinkaurelius.com/docs/titan/1.0.0/getting-started.html

gremlin> saturn = g.V().has('name', 'saturn').next()
==>v[256]
gremlin> g.V(saturn).valueMap()
==>[name:[saturn], age:[10000]]
gremlin> g.V(saturn).in('father').in('father').values('name')
==>hercules
gremlin> hercules = g.V(saturn).repeat(__.in('father')).times(2).next()
==>v[1536]

The Gremlin overview presented in this section focused on the Gremlin-Groovy language implementation. Additional JVM language implementations of Gremlin are available.
https://github.com/tinkerpop/gremlin/wiki/JVM-Language-Implementations

***********************************************************************************************************************************

"25+ Handy Gremlin Examples and Code Snippets for Graph Database Traversal and Manipulation"
http://www.fromdev.com/2013/09/Gremlin-Example-Query-Snippets-Graph-DB.html

Get The Count Of Vertices With A Attribute Value
g.V('firsName','John').count();

***********************************************************************************************************************************

From wikipedia: Lots of examples of queries with Titan!
Like Declarative Pattern Matching Traversals vs. OLAP Traversal
https://en.wikipedia.org/wiki/Gremlin_(programming_language)#Traversal_Examples

gremlin> g.V().label().groupCount()		// For each vertex in the graph, emit its label, then group and count each distinct label.
==>[occupation:21, movie:3883, category:18, user:6040]

gremlin> g.V().hasLabel('movie').values('year').min()		//What year was the oldest movie made?
==>1919

gremlin> g.V().has('movie','name','Die Hard').inE('rated').values('stars').mean()		// What is Die Hard's average rating?
==>4.121848739495798

***********************************************************************************************************************************

Notes from online lecture:
http://www.slideshare.net/calebwjones/intro-to-graph-databases-using-tinkerpops-titandb-and-gremlin
Suggests Gephi for visualization tool.

Where g is graph traversal.
g.V() // All verticies
g.v() // This verticie (Same with edges)
g.V("thisType", "matchThis")

Where g is a graph factory.
@28:40
g.V.count()
g.E.count()

***********************************************************************************************************************************

Gephi Example
https://www.youtube.com/watch?v=wtiUjna9IMo

***********************************************************************************************************************************

# Nov 16

To run script in Gremlin:
:load /home/casal033/MAP/graph-DBs-and-genetic-programming/gremlin_scripts/LoadNodes.groovy

gremlin> g.V(402206864).valueMap()
==>[generation:[191], total_error:[3554.0], numSelections:[0], numChildren:[0], run_uuid:[884ccc2a-85ec-43d6-b61d-03a33f9e4827], location:[999], uuid:[8536374f-c5b9-46fc-901e-f9d72d9214e5]]

gremlin> g.V().has('total_error', 0.toFloat()).next()
==>v[78712864]
gremlin> g.V().has('total_error', 0f).next()
==>v[78712864]

***********************************************************************************************************************************

# Nov 23

* We're currently deleting the database directory to delete graphs *
Gremlin remove all Vertex:
graph.close()
com.thinkaurelius.titan.core.util.TitanCleanup.clear(graph);

* Ended: Looking at elesticseach for external index based searching *
Install on Bebop next time!

* Not attempted yet *
Adding Edges:
http://gremlindocs.spmallette.documentup.com/#graphaddedge
gremlin> g = new TinkerGraph()
==>tinkergraph[vertices:0 edges:0]
gremlin> v1 = g.addVertex(100)
==>v[100]
gremlin> v2 = g.addVertex(200)
==>v[200]
gremlin> g.addEdge(v1,v2,'friend')
==>e[0][100-friend->200]
gremlin> g.addEdge(1000,v1,v2,'buddy')
==>e[1000][100-buddy->200]
gremlin> g.addEdge(null,v1,v2,'pal',[weight:0.75f])
==>e[1][100-pal->200]

***********************************************************************************************************************************

# Nov 30

* When running gremlin script, if graph directory exists and do not have the write permissions, bad error message occurs

* Close graph and mgmt after running script, otherwise badness and exceptions

* We think the default schema is being used for indexing, and we do not know what type of data it's saved as. We want to set this ourselves.
** we do not want to use the default schema **

*** Should we index before loading everything in?

Login as Maggie
Run gremlin on bebop from directory that has scripts with:
/Research/titan-1.0.0-hadoop1/bin/gremlin.sh

Remove graphes and searchindex before running script
Use :load LoadNodes.groovy in gremlin

***********************************************************************************************************************************

# Dec 7

Range with ints:
g.V().has("total_error", inside(0,150)).count()
Ranges with labels:
g.V().has("total_error", inside(0,150)).count()

Indexing is taking a long period of time, we may want ot remove:
uuid = mgmt.getPropertyKey('uuid')
& replace with run  uuid, may improve speed on building index.
(Success with 100 file, but not with 192,000)

Attempting to build index for at least 10 min.
Got return, however top shows us it's still running.
On close of Gremlin:
Dec 07, 2015 7:12:14 PM com.google.common.cache.LocalCache processPendingNotifications

***********************************************************************************************************************************

# Dec 8

Nic did some playing around and confirmed that we did just need to wait for the index building to finish. 
Not sure how long it actually took, but probably between 30 and 60 minutes.

After the index was build, queries are generally really fast. Some potentially useful queries:

// Find the largest generation in the DB
g.V().values('generation').max()

// Find the minimal total error in generation 176
g.V().has('generation', 176).values('total_error').min()

// Print the minimal total error for each of the 191 generations in a run
// You need the `.next()` bit on the end or you get a TinkerPop object
// instead of the value.
191.times { print " " + g.V().has('generation', it).values('total_error').min().next() }

// Loop over each of 191 generations, and print a line of the form `[gen, min, mean10, mean100]`
// for each generations, where `gen` is the generation, `min` is the minimum `total_error`,
// `mean10` is the average of the 10 best total errors, and `mean100` is the average of the 100
// best total errors in that generation.
191.times { println "[" + it + ", " + (g.V().has('generation', it).values('total_error').min().next()) 
                + ", " + (g.V().has('generation', it).values('total_error').order().limit(10).mean().next()) 
                + ", " + (g.V().has('generation', it).values('total_error').order().limit(100).mean().next()) + "]," }

***********************************************************************************************************************************

# Dec 28
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
How to Titan 0.o:
1) `ssh` to bebop
2) Change directories to:
`cd MAP/graph-DBs-and-genetic-programming/gremlin_scripts/`
and run:
`/Research/titan-1.0.0-hadoop1/bin/gremlin.sh`
3) Use this in Gremlin for Loading Vertices:
`:load LoadNodes.groovy`
4) Delete graph and searchindex with:
`rm -r filename`
in:
`cd /Research/titan-1.0.0-hadoop1/tmp/`
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Example of implementing buildMixedIndex:
* http://s3.thinkaurelius.com/docs/titan/current/indexes.html
* 8.1.1. Mixed Index

Generally Running the Groovy Script: 
* Always: graph is open & mgmt is closed afterwards
* with 100: GraphIndexStatusReport returns success
* with all: GraphIndexStatusReport returns failure 
(Needs time, still running after a half hour. 10:42)

Researching Edges Notes:
* Schema consists of: Edge labels and prperty keys 
(you can label verticies, may be useful when referencing how indivual is dervied e.g. 'autocontruction' or 'random')

* Edge label multiplicity options:
  - MULTI: Allows multiple edges of the same label between any pair of vertices.
  - SIMPLE: Allows at most one edge of such label between any pair of vertices.
  - MANY2ONE: Allows at most one outgoing edge of such label on any vertex in the graph but places no constraint on incoming edges. (Used in 'mother' edge example)
	Example Snippet of Usage: 
	mgmt = graph.openManagement()
	parent_of = mgmt.makeEdgeLabel('parent_of').multiplicity(MULTI).make()
	mgmt.commit()

** Apparently there is a type 'UUID', should this be considered for the vetex property key type?
	- http://s3.thinkaurelius.com/docs/titan/current/schema.html
	- Under "Table 5.1. Native Titan Data Types"

** Similar but different: Can we assign the vertex ID as the UUID? May be faster for finding vertex.

** Also I found this in Titan Documentation: "It is strongly encouraged to explicitly define all schema elements and to disable automatic schema creation by setting schema.default=none in the Titan graph configuration."

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Some notes before leaving:

* Running with edges code gives this at end:
==>0
Count = 0
Count = 1
No signature of method: com.thinkaurelius.titan.graphdb.database.StandardTitanGraph.addEdge() is applicable for argument types: (com.thinkaurelius.titan.graphdb.vertices.CacheVertex, com.thinkaurelius.titan.graphdb.vertices.CacheVertex, java.lang.String) values: [v[471160], v[856080], parent_of]

* Want to attempt commenting out the mixed index when adding edges (try just removing first before adding edges)
* May also want to consider placement of making the edge label in the script. It's currently where we create the property keys, could be closer to creating edges.
* Current opened tabs:
	- http://gremlindocs.spmallette.documentup.com/#graphaddedge
	- http://s3.thinkaurelius.com/docs/titan/0.5.4/schema.html#_relation_types
	- http://s3.thinkaurelius.com/docs/titan/current/tx.html
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

***********************************************************************************************************************************

# Dec 29

* Titan Console (aka Gremlin) Commands
https://jaceklaskowski.gitbooks.io/titan-scala/content/titan-gremlin_aka_titan_console.html

* TinkerPop3 Documentation
http://tinkerpop.incubator.apache.org/docs/3.0.1-incubating/

* Size of folder commands:		12/29	12/30
		du -sh graph/	-		137M	138M
		du -sh searchindex/	-	23M		11M

* Tried loading just nodes with full file, still does not finish after running for ~ 45 minutes.
* Making smaller file from edge csv file (Do not have the write permissions to create a new file in the current folder).

* Looking at 1 Million Example (Given Below):
  - A little dated, but I like the idea of a helper function to find verticies. 
  - http://thinkaurelius.com/blog/

~~~~~~~~~~~~~~~~~~~~~~~~ Start Example ~~~~~~~~~~~~~~~~~~~~~~~~
```
g = TitanFactory.open('/tmp/1m')
g.makeKey('userId').dataType(String.class).indexed(Vertex.class).unique().make()
g.makeLabel('votesFor').make()
g.commit()
getOrCreate = { id ->
  def p = g.V('userId', id)
  if (p.hasNext()) ? p.next() : g.addVertex([userId:id])
}
new File('wiki-Vote.txt').eachLine {
  if (!it.startsWith("#")){
    (fromVertex, toVertex) = it.split('\t').collect(getOrCreate)
    fromVertex.addEdge('votesFor', toVertex)
  }
}
g.commit()
```
~~~~~~~~~~~~~~~~~~~~~~~~ END EXAMPLE ~~~~~~~~~~~~~~~~~~~~~~~~

* Wrote script for LoadEdges.groovy
  - Takes a long period of time to find existing nodes in a graph
  - Not sure if indexing is working appropriately

* Need to ask Nic what mathod he used to get the build mixed index working?
  - If it is the case that build mixed index takes time, how do we know when it stops? 
  - (When to load edges?)

***********************************************************************************************************************************

# Dec 30

* Removed the uuid as Nic did in other experiences to get build mixed index to finish.
* Got the build mixed index to succeed after 45min, this did not help to load the edges faster.

* Adding uuid back into the mixed index for better results hopefully.
* No benefits :/

* Creating different sized files to shorten waiting.


***********************************************************************************************************************************

# 7 Jan

* Researching loading. 
	- Take a look at Daniel Kuppitz's GitHub Example and posted comments here:
	- "For Titan you'll need to add a line counter and then graph.tx().commit() every 10.000 lines or so. Also, in your Titan graph configuration, set storage.batch-loading=true to speed things up a bit."
	- https://groups.google.com/forum/#!topic/aureliusgraphs/phHY9jYDHJY

***********************************************************************************************************************************

# 8 Jan

* Add labels to verticies 
	- Generation Nodes for stats on each generation
	- Run Nodes for stats on the whole run

* For Gremlin Queries: 25+ Handy Gremlin Examples and Code Snippets for Graph Database Traversal and Manipulation
	- http://www.fromdev.com/2013/09/Gremlin-Example-Query-Snippets-Graph-DB.html

* Create A Index Using Gremlin (Instead of composite index)
	- This is to index the graph with specific field you may want to search frequently. Lets say "myfield" 
	- g.createKeyIndex("myfield",Vertex.class);
	- Note: The index creation can be done for not existing fields therefore incase you want to create a index for existing fields you may need to delete all and then create index.

* For finding the the parents in the first generation of those with 0 total_error in the final round.
`g.V().has('total_error', 0).repeat(__.in('parent_of')).times(191).dedup().count()`

* Querying with mixed indexes 
http://s3.thinkaurelius.com/docs/titan/1.0.0/search-predicates.html

* When creating a visual of the winner line
	- Box size based on numSeltions or numChildren
	- Highlight where the genome changes
	- Compute Levenstein distance between these children and parents genomes
	- Eliminate same parent being both parents (eliminate the extra edge)

* This query goes through each generation, counts the number of individuals in that generation that are an ancestor of a winner, and then prints that out. I'm guessing there's a more efficient way to do this (this duplicates a *lot* of search), but this works reasonably well on the 192 generation run. 

`192.times { gen -> println gen + "\t" + g.V().has('total_error', 0).until(has('generation', gen)).repeat(inE().outV().dedup()).dedup().count().next() }`

***********************************************************************************************************************************

# 9 Jan

Nic figured out how to use subgraph extraction to create a subgraph that contains just the ancestors of winners. 
There are several steps to the process.

The first step is to specify the subgraph:

 * `ancG = g.V().has('total_error', 0).repeat(__.inE().subgraph('sg').outV()).times(191).cap('sg').next()`

The `g.V().has('total_error', 0)` part finds all the "winners". 
The `repeat(__.inE().subgraph('sg').outV()).times(191)` part repeatedly searches out from those starting nodes,
collecting ancestor nodes. We need `inE()` to specify incoming edges, and then `outV()` extracts the outgoing
vertex on that edge (the parent). The `times(191)` part says to go all the way back to the first generation; that
number would need to be updated for different size graphs with different numbers of generations. I (Nic) am entirely
sure what the `cap` bit does, but it seems necessary. The `next()` bit extracts the resulting subgraph.

This command takes a while, but not more than a minute or two.

When this completes, then `ancG` is a graph, just like the kind we load from the property file, but it only contains
the ancestors of the winners. From that we can get a `traversal()` just like on "regular" graphs, and then do regular
queries. E.g.,

 * `anc = ancG.traversal()`
 * `anc.V().count()` (yields 19,935)
 * `anc.E().count()` (yields 39,842)

***********************************************************************************************************************************

# 10 Jan

Nic figured out how to go through all the nodes and edges in a graph, and output them in DOT format for turning
into displayable PDFs. This took a while, and the syntax isn't what I would call entirely intuitive, but it works
and is even beginning to make sense.

Let's start with the query that prints out all the nodes:

```
anc.V().
	as('v').values('uuid').as('id').
	select('v').values('numChildren').as('nc').
	select('v').values('total_error').as('te').
	select('id', 'te', 'nc').
	sideEffect{ printNode(fr, maxError, it.get()) }.
	iterate(); null
```
Taking this apart:

 * `anc.V()` just gets us all the vertices, like we've been doing.
 * `as('v')` is important and took me a while to figure out; it gives a name to the current vertex being processed and allows us to "jump back" to that vertex with a `select('v')` command.
 * `.values('uuid').as('id')` extracts the UUID from that node and gives it a name ('id').
 * `.select('v')` tells it to jump back to (whole) vertex. If we didn't have that, all subsequent steps would be applied to the UUID (which is just a string) coming from the previous step.
 * `.values('numChildren').as('nc')` and `select('v').values('total_error').as('te')` are just like the UUID bit above.
 * `select('id', 'te', 'nc')` bundles together the ID, total error, and number of children in a little map, which is what we'll pass to our `printNode` function below.
 * `sideEffect{ printNode(fr, maxError, it.get()) }` was a piece that perhaps took me the longest. I couldn't figure out how to "print stuff out", especially in a way that wasn't interleaved with all the Gremlin output. So in the end I opened up a FileWriter (that's what `fr` is) and wrote a function `printNode` that printed a node line to that file. That function is fairly ugly but uninformative; see below. The `sideEffect` step lets you do things that have side effects (like print out node lines) without affecting what gets sent down the pipeline. There's really nothing here "down the pipeline", but if there was it would just receive the result of the `select('id', 'te', 'nc')` step. The `get()` bit is important -- it extracts the map from the little Tinkerpop bundle that came from `select`.
 * `iterate(); null` took a little while to figure out. Adding the `null` statement at the end means that the whole "line" returns null, and allows us to skip the vast ocean of printing to the terminal that would go on if we ran this on a big graph. Interestingly, though, Tinkerpop/Gremlin is lazy and only processes the elements in a pipeline that are actually _used_ in some way. Thus if you just stuck `; null` after the `sideEffect` line, Tinkerpop would say "Hey, no one actually needs _anything_ from this pipeline, so lets just skip it all and do nothing!". Not good. Adding `iterate()` to the end of hte pipeline "forces" it to process its contents even if we're not going to look at the outputs.

In writing this up, I suspect we may not need the three `values()` lines and could just pass the node (or something like `valuesMap()`) directly into `printNode`. We may need them (or something like them) in the query that processes edges, but I'm not sure.

The edge processing query:

```
anc.E().
        as('e').outV().values('uuid').as('parent').
        select('e').inV().values('uuid').as('child').
        select('parent', 'child').
        sideEffect{ printEdge(fr, it.get()) }.
        iterate(); null
```questions

is much the same, but it uses `outV()` and `inV()` to get the output vertex (parent) and input vertex (child) for the given edge.

As mentioned above, `printNode` is pretty ugly (lots of string manipulation), so I'll just include `printEdge` here:

```groovy
void printEdge(fr, e) {
     fr.println('"' + e['parent'] + '"' + " -> " + '"' + e['child'] + '"' + ";")to visit
}
```

`e` is a map with two entries, and in groovy (like Ruby and Python) we can access map entries with an array-like syntax: `e['parent']. So this prings a line to the file of the form:

```
"e9e7e755-3fcd-46ec-b3a4-e02f010f9531" -> "dd87f949-10ba-44e8-a034-f77b945d81e8";
```

where the first string is the parent UUID and the second is the child UUID; in DOT this will cause an edge to be drawn from the parent node to the child node.

***********************************************************************************************************************************

# 12 Jan

* Tasks to consider:
	- Figure out how to parse 'raw' run files into Titan - DONE
	- Dynamic displays w/ something Gephi/Sigma.js/etc - Focus on later in semester.
	- Do we write something for GECCO? - Yes! At least try.
	- Dig through the 'interesting' individuals in current run
	- Do all of this on Amazon!
	- For dot pdf:
		- Change height of boxes proportional to number of children - DONE
		- Remove weird chircles at end of each generation - DONE
	- For Titan graph db:
		- Compute the Levenshtein distance between parent/child verticies
		- Compute the distance between error vectors
		- Add the difference between total_errors - Can be stored on edges.
		- Mark 'mother_of' & 'father_of' edges versus just 'parent_of' - DONE
		- Add 'Generation' nodes and 'Run' nodes - Focus on later.

***********************************************************************************************************************************

# 13 Jan

* Looked at runs generated before Lee's changes:
	- data8.csv.gz (310M) had a winner in 457 generations
	- data18.csv.gz (440M) had a winner in 693 generations
	- data16.csv.gz (3.1G) had a total_error of 46

* In the newest runs:
	- data7 looks promising, but has a huge genome. The total_error is currently 3, but isn't finished yet.
	- data5 is the only run with a winner thus far at 977 generations
	- Everything else (all other runs) has a best total_error in the tirple digits.

***********************************************************************************************************************************

# 14 Jan

* Look at link, under "Changing Schma Elements":
http://s3.thinkaurelius.com/docs/titan/current/schema.html

***********************************************************************************************************************************

# 15 Jan

* With the larger graphs, we can load them and see where they become reproductively competent.
* Then we can reload from that point, and limit our graph.

* Lines for adding num_selections, num_children, and num_ancestry_children:

(0..310).each { gen -> g.V().has('generation', gen).sideEffect { num_selections = it.get().edges(Direction.OUT).size(); it.get().property('num_selections', num_selections) }.iterate(); graph.tx().commit(); println gen }

(0..310).each { gen -> g.V().has('generation', gen).sideEffect { edges = it.get().edges(Direction.OUT); num_children = edges.collect { it.inVertex() }.unique().size(); it.get().property('num_children', num_children) }.iterate(); graph.tx().commit(); println gen }

(0..310).each { gen -> anc.V().has('generation', gen).sideEffect { edges = it.get().edges(Direction.OUT); num_ancestry_children = edges.collect { it.inVertex() }.unique().size(); it.get().property('num_ancestry_children', num_ancestry_children) }.iterate(); graph.tx().commit(); println gen }

* Commits are important!!!
* Adding num_ancestry_children will be removed upon closing the graph. Needs to be recomputed each time using anc traversals.

* Ancesty graph:
	- Played with the height and width ratios for the dot pdf. There a 5:1 ratio where the height is 5 times larger than the width for the same number of children.
	- Played with box colors in the upper 2/3 range, havn't seen this yet.
	- Added two different grays for the pdf, dark gray for 'mother' and a light gray for 'father'.

***********************************************************************************************************************************

# 19 Jan

GECCO Paper deadlines
Start Tuesday January 26, 2016 : End Wednesday January 27, 2016

* Planning:
	- We'll need to format our csv reader to align with the other csv files. (csv includes the program now)
	- Get a larger range of colors for dot pdf
	- Remove the mother and father distintion
	- May have to play with vertical alignment of multiple graphs
	- Have it determin the last generation - NOT HARDCODED
	- Have function for making ancestry graph (takes in graph and run uuids) and seperate function for creating dot file 

***********************************************************************************************************************************

# 20 Jan

There's a Groovy CSV library that we might want to look into: http://xlson.com/groovycsv/

We might want to revisit the use of `BatchGraph` as described in the ["Powers of 10" articles](http://thinkaurelius.com/2014/05/29/powers-of-ten-part-i/) since `BatchGraph` appears to provide the intermediate commits so we don't have to do them by hand. In the other hand, it's not obvious if `BatchGraph` is still in Tinkerpop stuff. [This API page](http://tinkerpop.apache.org/javadocs/3.0.0.M8-incubating/full/org/apache/tinkerpop/gremlin/structure/util/batch/BatchGraph.html) does suggest that maybe it is?

We were able to load two runs into the same database and plot their winning ancestries!

We learned that if you add the `num_children` property, etc., _after_ constructing the winning ancestor subgraph, those properties don't propogate from `g` to `anc`. You then have to regenerate `anc` and re-add the `num_ancestry_children` property.

***********************************************************************************************************************************

# 23 Jan

* The RSWN runs from thelmuth's dissertation.
	Winners:
	- data0, 1, 5, 6, 8, 90, 91, 93, 95, 97, 99

	Non-Winners:
	- data2, 3, 4, 7, 9, 92, 94, 96, 98

* We need a way of telling which runs are which in Titan.
	- Maybe this is a good time to add labels to verticies, and also add the run verticies?

* For advertising the project
	- Make bookmarks with link to githubpage (to be made)

* Added labels (run and individual) and learned they're not indexed.
* Added run verticies 
	- Added `successful` as a property
	- Added `data_file` as a property to recall which file we're looking at
	- Probably want `max_generation` as a property


* Working on making ancestry graphs for successful and unsuccessful runs. Trying to figure out how to do BOTH at once with:
`ancG = g.V().or(__.has('total_error', 0), __.has('generation', 300)).repeat(__.inE().subgraph('sg').outV()).times(977).cap('sg').next()`
However, this is taking a long time. Wondering if the `or` is a problem, or maybe the `has('generation', 300)` aspect since this returns 1000 per unsuccessful run.

With:
`ancG = g.V().or(__.has('total_error', 0), __.has('generation', 300)).repeat(__.inE().subgraph('sg').outV().dedup()).times(977).cap('sg').next()`
Play with `dedup()` try with and without to see if it makes a difference.

***********************************************************************************************************************************

# 30 Jan

Ghost Script! Used to resize pdfs:
gs -sDEVICE=pdfwrite -dFIXEDMEDIA -dPDFFitPage -dCompatibilityLevel=1.4 -dPDFSETTINGS=/screen -sPAPERSIZE=letter -dNOPAUSE -dQUIET -dBATCH -sOutputFile=output_file.pdf input_file.pdf

