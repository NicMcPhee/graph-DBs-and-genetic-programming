import groovy.io.FileType

all_ancestors = { starters ->
  ancG = inject(starters).
    unfold().
    repeat(__.inE().
      hasNot('minimal_contribution').
      subgraph('sg').
      outV().
      dedup()).
    times(977).cap('sg').next()
  return ancG
}

make_individual_directory = { node, base_name ->
  generation = node.value('generation')
  location = node.value('location')
  ind_label = "${base_name}/${generation}_${location}"
  dir = new File(ind_label).mkdir()
  return ind_label
}

generate_raw_genome_file = { node, dir_name ->
  new File("${dir_name}/raw_genome.txt").withWriter { out ->
    out.println(node.value('plush_genome'))
  }
}

genome_items = { node ->
    genome = node.value('plush_genome')
    genome.
        findAll(/\{:instruction (.+?), :close ([0-9]+)\}/).
        collect({(it =~ /\{:instruction (.+?), :close ([0-9]+)\}/)[0][1..-1]})
}

generate_genome_diff_file = { node, dir_name ->
  genes = genome_items(node)
  new File("${dir_name}/genome_diff_file.txt").withWriter { out ->
    genes.each { gene ->
      (instruction, close_count) = gene
      out << "${instruction}\t${close_count}\n"
    }
  }
}

generate_raw_program_file = { node, dir_name ->
  new File("${dir_name}/raw_program.txt").withWriter { out ->
    out.println(node.value('push_program'))
  }
}

generate_program_diff_file = { node, dir_name ->
  program = node.value('push_program')[1..-2]
  program = program.replaceAll(/\(/, "\n(").replaceAll(/\)/, ")\n")
  new File("${dir_name}/program_diff_file.txt").withWriter { out ->
    out.println(program)
  }
}

generate_raw_error_vector_file = { node, dir_name ->
  new File("${dir_name}/raw_error_vector.txt").withWriter { out ->
    out.println(node.value('error_vector'))
  }
}

generate_error_vector_diff_file = { node, base_name ->
  errors = node.value('error_vector').split(",")
  new File("${dir_name}/error_vector_diff_file.txt").withWriter { out ->
    errors.eachWithIndex { error, idx ->
      out << "TestCase" << idx << "\t" << error << "\n"
    }
  }
}

generate_error_vector_even_odd_file = { node, base_name ->
  errors = node.value('error_vector').split(",")
  new File("${dir_name}/error_vector_even_odd_file.txt").withWriter { out ->
    errors.eachWithIndex { error, idx ->
      if (idx % 2 == 0) {
	out << "TestCase" << idx << "\t" << error << "\n"
      }
    }
    errors.eachWithIndex { error, idx ->
      if (idx % 2 == 1) {
	out << "TestCase" << idx << "\t" << error << "\n"
      }
    }
  }
}

process_individual = { node, base_name ->
  dir_name = make_individual_directory(node, base_name)
  generate_raw_genome_file(node, dir_name)
  generate_genome_diff_file(node, dir_name)
  generate_raw_program_file(node, dir_name)
  generate_program_diff_file(node, dir_name)
  generate_raw_error_vector_file(node, dir_name)
  generate_error_vector_diff_file(node, dir_name)
  generate_error_vector_even_odd_file(node, dir_name)
}

/*
 * Extract the subgraph of "interesting" contributions from the given graph,
 * and then generate the following files for EACH individual in that subgraph:
 *   * The raw genome file
 *   * The genome file, with just the instruction and close count (no labels),
 *     one per line
 *   * The raw program file
 *   * The program file, with newlines inserted before every '('
 *   * The raw error vector file
 *   * The error vector, one value per line
 *   * The error vector, one value per line, but with all the even indexed
 *     values first, followed by all the odd indexed values
 * We'll create a new directory for every individual using the name
 * "generation_location", in the directory indicated by base_name.
 */
extractRunFiles = { graph, base_name ->
  winners = []
  graph.traversal().V().has('total_error', 0).fill(winners)
  ancG = all_ancestors(winners)
  anc = ancG.traversal()
  anc.V().sideEffect { process_individual(it.get(), base_name) }.iterate()
}

extractRunFiles(graph, "/tmp/run0_files")
