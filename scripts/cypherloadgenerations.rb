#!/usr/bin/env ruby
require 'neo4j-core'
session = Neo4j::Session.open
csvfile = ARGV[0]
gen = ARGV[1]
loadCsvQuery = "LOAD CSV WITH HEADERS FROM 'file:C:/Users/1/#{csvfile}' AS line
MATCH (p:individual) where p.id IN split(line.parent_uuids, ' ')
MERGE (i:individual{id: line.uuid, generation: line.generation, location: line.location})
CREATE (p)-[:PARENT {t: line.genetic_operators}]->(i)"

session.query(loadCsvQuery)

results = []

nodesCreatedQuery = "MATCH (n) WHERE n.generation = '#{gen}' RETURN count(n) as count"
relsCreatedQuery = "MATCH (n)<-[r]-(p) WHERE n.generation = '#{gen}' RETURN count(r) as relcount"

nodeResponse = session.query(nodesCreatedQuery).to_a
relResponse = session.query(relsCreatedQuery).to_a

puts "Nodes created #{nodeResponse[0][:count]}, Rels created #{relResponse[0][:relcount]}"