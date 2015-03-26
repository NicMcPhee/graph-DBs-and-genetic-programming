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
#   uuid,generation,location,parent_uuids,genetic_operators,
#   push_program_size,plush_genome_size,push_program,plush_genome,
#   total_error,TC0,TC1,TC2,...,TC199
# We need to replace all the dashes in the headers with underscores,
# and it would be good to glob all the errors into an array instead of
# individual columns.

input_file = ARGV[0]
p input_file
dirname = File.dirname(input_file)
basename = File.basename(input_file, ".csv")
node_file = File.join(dirname, "split_csvs", basename + "_nodes.csv")
edge_file = File.join(dirname, "split_csvs", basename + "_edges.csv")

run_uuid = SecureRandom.uuid()

dashes_to_newlines = lambda { |str| str.gsub('-', '_') }

def parse_parent_uuids(str)
  str = str[1...-1].gsub("\"","")
  parent_uuids = str.split(" ")
end

def parse_gen_ops(str)
  if str.index("[") == 0
   str = str[1...-1]
  end
  str = str.gsub("-", "_").gsub(":", "")
  genetic_ops = str.split(" ").join(",")
end

def add_int_string(arr)
  for i in 0..arr.length-1
    if arr[i].end_with?("error")
      arr[i] += ":float:individuals"
    elsif arr[i].end_with?("tion", "size")
      arr[i] += ":int"
    end

  end
 intarr = arr
end


# name:string:users

printed_headers = false
CSV.open(node_file, "wb") do |nodes|
  CSV.open(edge_file, "wb") do |edges|
    num_rows = 0
    edges << ["uuid:string:individuals", "uuid:string:individuals", "type", "gen_ops:string_array", "error_diff:float", "least_total_error:boolean"]
    total_error_hash  = Hash.new
    CSV.open(input_file, "r",
    :headers => true,
    :header_converters => dashes_to_newlines,
    :converters => [:numeric]) do |inputs|
      inputs.each do |row|
        if not printed_headers
          headers = inputs.headers[0..9]
	  headers = add_int_string(headers)
          headers -= ["parent_uuids"]
	  headers += ["label"]
	  headers -= ["genetic_operators"]
          headers += ["run_uuid"]
	  headers += ["test_cases"]
	  headers[headers.index("test_cases")]= "test_cases:float_array"
          headers[headers.index("uuid")] = "uuid:string:individuals"
          headers[headers.index("label")] = "i:label"
	  nodes << headers
          printed_headers = true
        end
        parent_ids = parse_parent_uuids(row["parent_uuids"])
	genetic_ops = parse_gen_ops(row["genetic_operators"])
	row.delete("parent_uuids")
	row.delete("genetic_operators")

	tc_arr = row.select {|key, value| key =~ /\ATC\d*\z/}
	test_cases = Hash[tc_arr.to_a]
	row.delete_if {|key, value| key =~ /\ATC\d*\z/}
	
	row["label"] = "individuals"

        row["run_uuid"] = run_uuid
        row["plush_genome"] = row["plush_genome"].gsub("\\", "\\\\\\")
        row["push_program"] = row["push_program"].gsub("\\", "\\\\\\")
	row["test_cases"] = test_cases.values.join(",")
	
	
	total_error_hash[row["uuid"]] = row["total_error"]
	
        nodes << row
	parent_count = 0
        parent_ids.each do |parent_uuid|
	  least_total_error = true
	  total_error = total_error_hash[parent_uuid]-row["total_error"]
	  
	  if parent_ids.length == 2
		least_total_error = total_error_hash[parent_ids[parent_count]] < total_error_hash[parent_ids[1-parent_count]]
	  end
	  # p "#{parent_uuid} #{row["uuid"]} #{total_error} #{total_error_hash[parent_uuid]} #{row["total_error"]} #{least_total_error}"
          edges << [parent_uuid, row["uuid"], "PARENT_OF", genetic_ops, total_error, least_total_error]
          parent_count += 1
	end
      end
    end
  end
end
# Syntax for calling batch_import:
#    ./import.sh test.db ../data/data6_nodes.csv ../data/data6_edges.csv

