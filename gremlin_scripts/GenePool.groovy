
import org.apache.tinkerpop.gremlin.process.traversal.P
import com.thinkaurelius.titan.graphdb.vertices.CacheVertex

import static us.bpsm.edn.parser.Parsers.defaultConfiguration;
import us.bpsm.edn.parser.*;

// import us.bpsm.edn.*;
// import us.bpsm.edn.parser.CollectionBuilder;

class GenePool<V> extends P<V> {

  // GenePool() {
  //   super(null,null)
  // }
  def parser 
  def winnerGenes

  GenePool(winners){

    super(null,null)
    println("GenePool: creating a gene pool")

    parser = Parsers.newParser(defaultConfiguration())
    println("GenePool: created the parser")

    def winnerGenomes = winners.collect { winner ->
      def genomeString = winner.values('plush_genome').next()
      parser.nextValue(Parsers.newParseable(genomeString))
    }
    println("GenePool: parsed the genomes")

    winnerGenes = winnerGenomes.flatten().unique()
    println("GenePool: deduped the genes")
  }

  @Override
  def boolean test(vertex){

    def testGeneString = vertex.values('plush_genome').next()
    def testGenome = parser.nextValue(Parsers.newParseable(testGeneString))

    return winnerGenes.any { g1 ->
      testGenome.any { g2 ->
        g1 == g2
      }
    }
  }

  @Override
  def String toString(){
    "--GenePool string representation--"
  }
}
