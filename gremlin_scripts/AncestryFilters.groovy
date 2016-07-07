
/* This class defines a set of useful methods/functions
 * for creating ancestry trees. For the most part, these are
 * not general purpose methods. Rather, each one was designed
 * to provide specific insight to specific runs.
 */

class AncestryFilters {


  /*
   * Takes a list of individuals and produces a traversal that
   * contains all their ancestors. There is minor filtering in that
   * any individual who did not contribute any genes at all to its child will
   * not be included
   *
   * Experimental
   * Written for analysis of run with UUID=11ca78d4-6f11-47cc-ad78-56c63eb4c5e2
   */


  static def geneticAncestors(ancestor_list) {

    inject(ancestor_list).unfold().repeat(
      __.inE().hasLabel('parent_of').as('edge')
      .outV().as('nominal_parent').select('edge').unfold().inV().out('contains').in('creates').in('contains').as('genetic_parents')
      .select('edge', 'nominal_parent', 'genetic_parents')
      .where('genetic_parents', eq('nominal_parent')).dedup().select('edge').unfold()
      .subgraph('sg').outV().dedup()).cap('sg').next().traversal()
  }

  /*
   * Takes a list of individuals and produces a traversal of
   * a subgraph containing the ancestors. This filter is not interesting
   * because it pulls in parents that didn't contribute to the *final generation*
   *
   * Experimental
   * tested on run with uuid=b00ebe07-dfa5-427d-a4b8-3ec8a2566729
   *
   * Seems to miss a few edges (in this case only one)
   *
   */
  static def geneticAncestorsWithGeneNodes(ancestor_list){


    return inject(ancestor_list).unfold().repeat(
      __.as('child').out("contains").in("creates").in('contains').dedup().as('gen_paren')
      .outE('parent_of').as('edge').inV().where(eq('child')).select('edge').unfold()
      .subgraph('sg').outV()).cap('sg').next().traversal()
  }

  /*
   * Classic unfiltered ancestry graph. Should work with most of the non-autoconstruction runs.
   */
  static def unfilteredAncestry(ancestry_list){
    inject(ancestry_list).unfold().repeat(__.inE('parent_of').subgraph('sg').outV().dedup()).cap('sg').next().traversal()
  }

  /*
   * Builds an ancestry tree by chasing the *genes* up the tree and adding the
   * relevant parent->child edges to a subgraph.
   *
   * Experiemental. run uuid=b00ebe07-dfa5-427d-a4b8-3ec8a2566729
   *
   */
  static def filterByGenes(ancestry_list){

    println("starting to filterByGenes in AncestryFilters")
    def i = 0

    inject(ancestry_list).unfold().out('contains').repeat(
      __.as('gene').in('creates').in('contains').as('gen_parents') // get the parents that these genes actually came from
      .select('gene').unfold().in('contains').inE('parent_of').as('edge') // get the edges to the alleged parents
      .outV().where(eq('gen_parents')) // filter out the alleged parents that aren't in the list
      .select('edge').unfold().subgraph('sg') // add the parent_of edges to the subgraph
      .select('gene').unfold().dedup().in('creates')) // select the ancestors of the genes and repeat
    .cap('sg').next().traversal()

  }

  /* same as filterByGenes, but does chase up genes that were produced
   * via mutation.
   */
  static def filterByGenesCopiesOnly(ancestry_list){

    println("starting to filterByGenes in AncestryFilters")

    inject(ancestry_list).unfold().out('contains').repeat(
      __.as('gene').in('creates').in('contains').as('gen_parents') // get the parents that these genes actually came from
      .select('gene').unfold().in('contains').inE('parent_of').as('edge') // get the edges to the alleged parents
      .outV().where(eq('gen_parents')) // filter out the alleged parents that aren't in the list
      .select('edge').unfold().subgraph('sg') // add the parent_of edges to the subgraph
      .select('gene').unfold().dedup().inE('creates').has('operations', ':copy').outV()) // select the ancestors of the genes and repeat
    .cap('sg').next().traversal()

  }

  /*
   * A tweaked version of filterByGenesCopiesOnly.
   * However, it produces the same subgraph (for the one run I've looked at)
   */
  static def filterByGenesCopiesOnlyFix(ancestry_list){

    println("starting to filterByGenes in AncestryFilters")

    inject(ancestry_list).unfold().out('contains').repeat(
      __.as('gene').inE('creates').has('operations', ':copy').outV().in('contains').as('gen_parents') // get the parents that these genes actually came from
      .select('gene').unfold().bothE().subgraph('sg').select('gene').unfold().in('contains').inE('parent_of').as('edge') // get the edges to the alleged parents
      .outV().where(eq('gen_parents')) // filter out the alleged parents that aren't in the list
      .select('edge').unfold().subgraph('sg') // add the parent_of edges to the subgraph
      .select('gene').unfold().dedup().inE('creates').has('operations', ':copy').outV()) // select the ancestors of the genes and repeat
    .cap('sg').next().traversal()

  }

  /*
   * Produces a traversal of the graph containing the winning *genes* and all their sources.
   * It will not have any individuals in it.
   */
  static def geneAncestry(ancestry_list){
    inject(ancestry_list).unfold().out('contains').repeat(
      __.inE('creates').subgraph('sg').outV()
    ).cap('sg').next().traversal()
  }

  /*
   * Same as geneAncestry, but only chases back genes that were produced via the ':copy' operation.
   */
  static def geneAncestryCopiesOnly(ancestry_list){
    inject(ancestry_list).unfold().out('contains').repeat(
      __.inE('creates').has('operations',':copy').subgraph('sg').outV()
    ).cap('sg').next().traversal()
  }


  /*
   * Q: Oh no! this isn't a filter! what is it doing in a filtering class?
   * A: It is used to mark nodes for future filtering.
   *
   * This marks all the genes in the tree that are ancestors of give
   * vertexes with key,value
   */


  static def markAncestryGenesCopiesOnly(ancestry_list, key, value){
    inject(ancestry_list).unfold().out('contains').repeat(
      __.property(key,value).inE('creates').has('operations',':copy').subgraph('sg').outV()
    ).cap('sg').next().traversal()
  }

  static def markContributionPercentage(graph){
    def g = graph.traversal()
    g.V().hasLabel('individual').sideEffect{ traverser ->
      def vertex = traverser.get()
      def contributionPercentage = percentGenesCopiedToWinner(vertex)
      vertex.property('percent_copied_to_winner', (float) contributionPercentage)
    }.iterate()
    graph.tx().commit()
  }

  static def markNumberofWinningGenes(graph){

    def g = graph.traversal()
    g.V().hasLabel('individual').sideEffect{ traverser ->

      def vertex = traverser.get()
      def stats = percentGenesCopiedToWinner(vertex)

      vertex.property('total_copied_to_winner', stats['gross'])
      vertex.property('percent_copied_to_winner', (float) stats['percent'])

    }.iterate()
    graph.tx().commit()

  }

  static def percentGenesCopiedToWinner(ver) {
    def genes = ver.vertices(Direction.OUT, 'contains')
    def totGenes = 0
    def winGenes = 0
    genes.each { v ->
      if ( v.values('copied_to_winner')){
        winGenes++
      }
      totGenes++
    }
    if (totGenes == 0){
      return [percent: 0, gross: 0]
    } else {
      return [percent: winGenes / totGenes,
              gross: winGenes]
    }

  }

}
