#!/usr/bin/env ruby

require 'csv'
require 'securerandom'

# We need to read in a CSV like we're getting from Tom Helmuth, and then
# generate two new CSV files, one with the nodes and one with the edges
# (i.e., parent-child relationships).

# We may need to do something with indexing, but auto-indexing may take
# care of that for us.

# The plan is that you'll call this like:
#   ruby split_for_batch_imports data6.csv
# and it will generate two new files: data6_nodes.csv and data6_edges.csv

# The headers for replace-space-with-newline are:
#   uuid,generation,location,parent-uuids,genetic-operators,
#   push-program-size,plush-genome-size,push-program,plush-genome,
#   total-error,TC0,TC1,TC2,...,TC199
# We need to replace all the dashes in the headers with underscores,
# and it would be good to glob all the errors into an array instead of
# individual columns.

input_file = ARGV[0]
node_file = File.basename(input_file, ".csv") + "_nodes.csv"
edge_file = File.basename(input_file, ".csv") + "_edges.csv"

run_uuid = SecureRandom.uuid()

def dashes_to_newlines(str)
  str.gsub('-', '_')
end

printed_headers = false
CSV.open(node_file, "wb", :headers => true, :write_headers => true) do |nodes|
  CSV.open(edge_file, "wb") do |edges|
    num_rows = 0
    CSV.open(input_file, "r",
    :headers => true,
    :header_converters => lambda { |h| h.gsub('-', '_') },
    :converters => :numeric) do |inputs|
      inputs.each do |row|
        if not printed_headers
          nodes << inputs.headers
          printed_headers = true
        end
        nodes << row
        puts "Row!"
        #                  p row
      end
    end
  end
end