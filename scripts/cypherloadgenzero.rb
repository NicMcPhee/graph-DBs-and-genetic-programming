#!/usr/bin/env ruby
require 'neo4j-core'
session = Neo4j::Session.open
csvfile = ARGV[0]
gen = ARGV[1]
loadCsvQuery = "LOAD CSV WITH HEADERS FROM 'file:C:/Users/1/#{csvfile}' AS line
CREATE (i:individual{id: line.uuid, generation: line.generation, location: line.location})"
results = []
session.query(loadCsvQuery)

nodesCreatedQuery = "MATCH n WHERE n.generation = '#{gen}' RETURN count(n) as count"
response = session.query(nodesCreatedQuery).to_a

puts "Nodes created #{response[0][:count]}"