
/*
 * This class makes it a little easier to print .dot files
 * for making graphs.
 *
 * TODO: add method to allow locking individuals to a rank.
 */

class DotWriter {

  FileWriter writer
  int subgraphDepth

  /*
   * This contructor destroys current contents of the file.
   * If you want to append to a file... try the other contructor,
   * but that probably isn't what you want either...
   */
  DotWriter(String file, int lastGenerationIndex, Map defaultAttributes){
    this(new FileWriter(file), lastGenerationIndex, defaultAttributes)
  }


  /*
   *
   * lastGenerationIndex - The index of the last generation
   * defaults - A map specifying what defaults to use for nodes when not otherwise specified.
   *            The form should be: [node: [attr0: val0,
   *                                        attr1: val1,
   *                                        ...],
   *                                 edge: [attr0: val0,
   *                                        attr1: val1,
   *                                        ...]]
   *           It is valid to omit any of the keys. All the keys should be Strings.
   *           All val* will be printed via their .toString() with no additional processing.
   *           Add quotes occordingly.
   */
  DotWriter(FileWriter fileWriter, int lastGenerationIndex, Map defaultAttributes){

    writer = fileWriter
    subgraphDepth = 0

    writer.println("digraph G {")
    if ( lastGenerationIndex >= 0){
      writer.println((0..lastGenerationIndex).collect{"\"Gen ${it}\""}.join(" -> ") + " [style=invis];")
    }
    defaultAttributes.each { pair ->
      if (["node", "edge"].contains(pair.key)){
        writer.print(pair.key)
        writeAttributes(pair.value)
      }
    }
  }

  /*
   * sourceName - the name of the node that this edge is leaving
   * destName - the name of the node that this edge is entering
   * attrs - (optional) a map used to create the attribute list for the edge
   */
  void writeEdge(String sourceName, String destName, Map attrs = [:]){
    writer.print("\"$sourceName\" -> \"$destName\" ")
    writeAttributes(attrs)
  }

  /*
   * nodeName - the name that this node should have in the dot file
   * attrs - (optional) a map used to create the attribute list for the node
   */
  void writeNode(String nodeName, Map attrs = [:]){
    writer.print("\"$nodeName\" ")
    writeAttributes(attrs)
  }

  /*
   * Private helper method. Used to print a list of attributes and then close the
   * statement that is currently being written.
   */
  private void writeAttributes(Map attrs){
    writer.print("[")
    attrs.each { pair ->
      if (pair.key instanceof String){
        writer.print("${pair.key}=${pair.value},")
      }
    }
    writer.print("];\n")
  }

  /* Starts a subgraph with the given name and attributes.
   * All subsequent calls to writeEdge and writeNode will
   * place the items within this subgraph.
   */
  void openSubgraph(name, defaultAttributes) {

    subgraphDepth += 1

    writer.println("subgraph $name {")
    defaultAttributes.each { pair ->
      writer.print(pair.key)
      writeAttributes(pair.value)
    }
  }

  /*
   * Closes the most recently opened subgraph.
   * Returns true if there was a subgraph to be closed.
   * Otherwise returns false.
   */
  boolean closeSubgraph(){
    if ( subgraphDepth > 0) {
      writer.println("}")
      subgraphDepth -= 1
      return true
    }
    else {
      return false
    }
  }

  /*
   * Closes the any open subgraphs, the graph, and the file
   * to which data is being written.
   */
  void close(){

    while ( closeSubgraph() ) {}

    writer.println("}")
    writer.close()
  }
}
