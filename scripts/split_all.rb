

Dir.glob("../data/*.csv") do |file|
	p file
	system "ruby split_for_batch_import.rb #{file}"
end
