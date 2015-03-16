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