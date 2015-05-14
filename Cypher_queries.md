**Note**: There's a number of instances of things like `toInt` and `toFloat` in these examples that will hopefully be able to go away. The initial import into Neo4J had all the numbers imported as strings, but we should be able to change that so they're imported as numbers instead, removing the need for these conversion functions.

# Cypher queries for Tom Helmuth's data

We can use this file to save Cypher queries that we've found useful, both for re-use and as a guide for writing new queries.

## Finding cloning events

In looking at sequence alignment results for an interesting subgraph of the population, Nic noticed that there were quite a lot of cloning events where a child had exactly the same genome as its parent. Lee then asked how diverse that set of cloned genomes was.

This query "loops" over the set of generations, and then for each generation `g` finds all the parent-child pairs from generation `g-1` to generation `g` where the parent and child have the same genome. It then returns the number of such pairs, the number of distinct Plush genomes, the number of distinct Push programs, and the number of distinct values for `total_error`. 

This query takes about 10 seconds on our iMac at home. The `DISTINCT` on the third line is really critical so you only process each generation once.

```{sql}
match (n) 
  where n.generation<>"0" 
with distinct n.generation as gens 
  unwind gens as g 
  match (p {generation: toString(toInt(g)-1)})-->(c {generation: g}) 
  where p.plush_genome = c.plush_genome 
  return g, count(c), 
    count(distinct c.plush_genome), count(distinct c.push_program), count(distinct c.total_error);
```

[This Rpub](http://rpubs.com/NicMcPhee/65471) has a plot showing what this data looks like for one lexicase run of replace-space-with-newline.

If you returned things like `p.uuid` and `c.uuid` then you'd get the UUIDs of the parent-child pairs which might be useful in some settings.

## Computing the average program size

When we started this, we couldn't "loop" over a variable like the generation. They've added the `unwind` command that now allows us to get that effect. This query computes the average program size for each generation; the resulting table then can be loaded into something like R and plotted straight away.

The `DISTINCT` in the second line is really critical so you only process each generation once. 

```{sql}
MATCH (n) 
WITH DISTINCT n.generation AS gens 
  UNWIND gens AS g 
  MATCH (c {generation: g}) 
  RETURN g, avg(toInt(c.push_program_size));
```

## Look at who does well on certain test cases

This assumes the test cases are separate fields instead of together in an array (which is probably where we want to go), but it won't be that hard to change to array indices. Make sure you include a `LIMIT` or some other sort of limiting or this can generate an _enormous_ amount of stuff.

```{sql}
match (n {generation: "40"}) 
return id(n), n.TC75, n.TC89, n.TC105, n.TC109 
order by toInt(n.TC75), toInt(n.TC89), toInt(n.TC105), toInt(n.TC109) 
limit 100;
```

## Find percentiles of field values

This query finds the 66% percentile for the `total_error` field for generation 41. Similar things can find other percentiles for other fields.

```{sql}
MATCH (n {generation: "41"}) 
RETURN percentileCont(toInt(n.total_error), 0.66);
```

## Find the recent ancestors of a node

This query finds all the ancestors of the given node (specified by UUID) going back 7 generations. Be careful how far back in time you push something like this, because it gets very slow and the output gets very large the farther you go back. This particular query takes just under two minutes on our home iMac, and returns a graph of 46 nodes and 53 relationships.

```{sql}
match (n {uuid: "\"50475edb-4f8c-43b3-b00a-aab3ed977ef8\""})<-[*0..7]-(a) 
return distinct(a);
```

## Find "big" crossovers

This query finds all crossovers where:
 * There are two distinct parents
 * The child's `total_error` is less than 25% of _both_ parent's `total_error`

My real goal would be to have a query that would work for both single parent and two parent situations (like the one below), but I couldn't figure out how to get both to happen.

```{sql}
match (q)-->(n)<--(p) 
where p.uuid < q.uuid 
  AND toFloat(n.total_error) < 0.25 * toFloat(p.total_error) 
  AND toFloat(n.total_error) < 0.25 * toFloat(q.total_error) 
return n.generation, n.uuid, n.total_error, p.uuid, p.total_error, q.uuid, q.total_error;
```
There are (to Nic) a surprsing number of these; not sure if that's a Push thing or a lexicase thing. In run 6 of lexicase replace-space-with-newline this query returns 104 such crossover events.

## Find ancestors of winners with lots of children

This doesn't count _all_ children, but just children that are also ancestors of winners.

```{sql}
match (p)-->(c)-[*0..7]->(w {total_error: "0"}) 
return distinct id(p), count(distinct c) 
order by count(distinct c); 
```

To count _all_ children use this:

```{sql}
match (p)-->(c)-[*0..7]->(w {total_error: "0"}) 
match (p)-->(n) 
return distinct id(p), count(distinct n) 
order by count(distinct n) desc 
limit 20;
```

## Counting ancestors of winners

This tells us how many ancestors of any winner we had in each of the given generations. It gets increasingly
slow as you push back in time, and will depend a lot on the branching factor of the geneology you're searching.

```{sql}
match (n) 
  where toInt(n.generation) > 70 
  with distinct n.generation as gens 
unwind gens as g 
  match (p {generation: g})-[*]->(c {total_error: "0"}) 
  return g, count(distinct p) 
  order by toInt(g) asc;
```

## Who has the most children, grandchildren, etc.?

The `4` in the first line indicates how far down the tree to go; a 1 there would count children, a 2 would count grandchildren, etc.

```{sql}
 match (n)-[* 4]->(m) 
 return id(n), count(distinct m) 
 order by count(distinct m) desc 
 limit 40;
```

# Finding ancestry chains where parents are "similar" to children

So I struggled with this for quite a lot, and even [opened a question on StackOverflow](http://stackoverflow.com/questions/29465925/seeking-neo4j-cypher-query-for-long-but-nearly-unique-paths) but eventually came to the conclusion that we can't efficiently generate long paths that are filtered on edge field values. I had hoped, for example, that this query would chase back through the small number of paths from a winner along only `least_total_error` edges:

```{sql}
match (a)-[:PARENT_OF* {least_total_error: true}]->(w {total_error: 0}) 
return distinct a;
```

That totally didn't work. Using the nifty new profiling tools provided in Neo4j 2.2, I found that the problem is that Neo4j essentially finds _every_ path through the graph (and there are millions and millions of those) and _then_ filters out the ones that contain "bad" edges (edges where `least_total_error` is `false`). 

It also appears to do the same thing with `w` (only filters after it generates the _huge_ pool of paths), but that we can fix with a `WITH` clause:

```{sql}
match (w {total_error: 0}) 
with w
match (a)-[:PARENT_OF* {least_total_error: true}]->(w) 
return distinct a;
```

Unfortunately there doesn't appear to be any way to "pre-filter" the pool of edges like the query above "pre-filters" the pool of nodes for `w`.

We _can_ however, get good performance if we limit the path to a particular edge _label_, so I added an `LTE` edge between every pair of nodes that had a `least_total_error: true` edge:

```{sql}
match (a)-[r:PARENT_OF {least_total_error: true}]->(b) 
create (a)-[:LTE]->(b);
```

I'd resisted this idea at first because I worried that this would be super slow, but it's not. This query added about 91K new edges in under a second!

Once all those new edges are in place, then we can do the query we want:

```{sql}
match (w {total_error: 0}) 
with w 
match (a)-[:LTE*]->(w) 
return distinct id(a), a.generation, a.total_error 
order by a.generation 
limit 200;
```

This generates the 123 nodes going back to the beginning of the run in about 4 seconds.

This means that if there's any kind of special edge property that we want to chase pseudo-linearly like this, we _have_ to make new edges for it. That can happen in the Ruby import scripts or (because it's apparently really fast) we can do it after the fact in the DB like above. But we have to do it somewhere or we just won't be able to chase those paths.

# Adding ancestor edges to winner

## Direct approach

This query takes a forever to do for the whole graph, so don't even try it. If it worked, though, it could really pays off as we could use the new `ANCESTOR_OF` edge to prevent Neo4J from "re-searching" for relationships over and over again in other queries. This is set up to just provide `ANCESTOR_OF` links to the winning individuals, but it could easily be expanded to provide shortcut edges for _every_ ancestor relationship. That would just take a lot longer and add a ton more edges, so I haven't tried that yet.

```{sql}
match (a)-[*1..]->(n {total_error: 0}) 
merge (a)-[r:ANCESTOR_OF]->(n) 
  on create set r.numPaths=1
  on match set r.numPaths=coalesce(r.numPaths, 0)+1;
```

This works by 
* Finding (matching) all paths of length at least 1 to a winner (`total_error` = 0).
* Use `merge` to create a new relationship with the label `ANCESTOR_OF`, or find/name one if one already exists.
* If we're creating a new relationship, set it's `numPaths` field to 1.
* If that relationship already existed, increment it's `numPaths` field by 1.

At the end, the `numPaths` field should tell us how many distinct paths there are from the ancestor node to the winning node.

I let this run for hours over an entire run DB and eventually killed it. It would probably make more sense to do this using something like Mazerunner so we can distribute the effort.

## Faster approach?

The previous approach takes forever, at least on my laptop, and then eventually errored out for some reason (perhaps having to do with the laptop going to sleep). So play B is to try to take advantage of the fact that generations allow us to do this incrementally, and hopefully avoid essentially searching every single path in the universe.

This assumes the run ends on generation 87. This is a case where it would be good to have a `Run` node that could, for example, include information on how many generations there are in that run as a way of avoiding this kind of magic number.

This approach is awesome, **except that it doesn't work**. It assumed that the new relationships would be added "on the fly", but the (usually very nice) transactional nature of Cyper means that in fact no new edges will be added until the query is "done". This means that running generation 83 won't have any idea what got put in during generation 84, etc.

The idea is usable, though, and could be moved into something like a Ruby script that would perform an appropriate queryonce for each generation. So frustrating.

Hmph.

```{sql}
match (p {generation: 86})-[:PARENT_OF]->(w {total_error: 0})
merge (p)-[r:ANCESTOR_OF]->(w)
  on create set r.numPaths=1;

with range(85, 80, -1) as gens
unwind gens as g
match (p {generation: g})-[:PARENT_OF]->(c)-[:ANCESTOR_OF]->(w {total_error: 0})
merge (p)-[r:ANCESTOR_OF]->(w)
  on create set r.numPaths=1
  on match set r.numPaths=coalesce(r.numPaths, 0)+1;
```

# Counting parents and max children across generations

The following query tells us for each generation (a) how many individuals were in fact parents and (b) what the maximum number of children any individual had:

```{sql}
unwind range(0, 87) as gen 
  match (n {generation: gen}) 
with gen, n 
  match (n)-[:PARENT_OF]->(c) 
with gen, n, count(distinct c) as ncs 
  return gen, count(distinct n), max(ncs) 
    order by gen asc;
```

* The `unwind` essentially "loops" through the generations.
* The first `match` and `with` restricts the subsequent searches to paths that start in that generation, which greatly speeds things up.
* The second `with` is necessary to limit or give a name to the count of distinct children so we can apply `max` to it in the `return.

# Counting individuals with high percentages of selections

@thelmuth would like to be able to count how many individuals in a run are selected more than 1%, 5%, and 10% of the total number of selections in their generation. The following query does that, assuming (on the second line) that the run has 88 generations (we could compute the max generation at the start of the query, but I was lazy). I suspect there may be ways to make this more efficient (it takes about 80 seconds on a single run of 88 generations), but it seems to be a good place to start.

I think that if we created a Generation node that we used to cache things like the total number of selections, we could substantially speed up (and simplify) queries like this.

```{sql}
unwind [0.01, 0.05, 0.1] as targetPercentage
unwind range(0, 88) as gen
match (p {generation: gen})
match (p)-[r:PARENT_OF]->(c)
with targetPercentage, gen, 1.0*count(distinct r) as totalSelections
match (p {generation: gen})
match (p)-[r:PARENT_OF]->(c)
with targetPercentage, gen, count(distinct r)/totalSelections as percentage, p
where percentage >= targetPercentage
return targetPercentage, count(distinct p)
order by targetPercentage asc;
```

This is the same thing, but breaks the data out by generation.

```{sql}
unwind [0.01, 0.05, 0.1] as targetPercentage
unwind range(0, 88) as gen
match (p {generation: gen})
match (p)-[r:PARENT_OF]->(c)
with targetPercentage, gen, 1.0*count(distinct r) as totalSelections
match (p {generation: gen})
match (p)-[r:PARENT_OF]->(c)
with targetPercentage, gen, count(distinct r)/totalSelections as percentage, p
where percentage >= targetPercentage
return gen, targetPercentage, count(distinct p), max(percentage)
order by gen asc, targetPercentage asc;
```
