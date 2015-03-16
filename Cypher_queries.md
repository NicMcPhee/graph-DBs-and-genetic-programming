# Cypher queries

We can use this file to save Cypher queries that we've found useful, both for re-use and as a guide for writing new queries.

## Finding cloning events

In looking at sequence alignment results for an interesting subgraph of the population, Nic noticed that there were quite a lot of cloning events where a child had exactly the same genome as its parent. Lee then asked how diverse that set of cloned genomes was.

This query "loops" over the set of generations, and then for each generation `g` finds all the parent-child pairs from generation `g-1` to generation `g` where the parent and child have the same genome. It then returns the number of such pairs, the number of distinct Plush genomes, the number of distinct Push programs, and the number of distinct values for `total_error`. It takes about 10 seconds on our iMac at home.

```{cypher}
match (n) 
  where n.generation<>"0" 
with distinct n.generation as gens 
  unwind gens as g 
  match (p {generation: toString(toInt(g)-1)})-->(c {generation: g}) 
  where p.plush_genome = c.plush_genome 
  return g, count(c), 
    count(distinct c.plush_genome), count(distinct c.push_program), count(distinct c.total_error);
```
