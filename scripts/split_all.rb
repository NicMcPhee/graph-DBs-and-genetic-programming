# takes as input no arguments for all files in data folder

# takes as input one argument all files to be included 
# seperated by a comma

# creates data base location
dbLocation = "../../data/test.db"

nodeList = ""
edgeList = ""


if (ARGV.length > 0)
	files = ARGV[0]
	fileArr = files.split(",")
	
end

Dir.mkdir("../data/split_csvs") unless File.exists?("../data/split_csvs")

# loops through files splits for batch-import

Dir.glob("../data/*.csv") do |file|
	basename = File.basename(file, ".csv")

	if (ARGV.length > 0)
		next if not (fileArr.include?("#{basename}.csv"))
	end

	dirname = File.dirname(file)
	node_file = File.join(dirname, "split_csvs", basename + "_nodes.csv")
	edge_file = File.join(dirname, "split_csvs", basename + "_edges.csv")
	nodeList += "../#{node_file},"
	edgeList += "../#{edge_file},"
	system "ruby split_for_batch_import.rb #{file}"
end

nodeList = nodeList[0...-1]
edgeList = edgeList[0...-1]

# batch-import call 

Dir.chdir("../bin/batch-import")
system ("./import.sh #{dbLocation} #{nodeList} #{edgeList}")
