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
