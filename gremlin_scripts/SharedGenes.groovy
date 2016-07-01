import java.util.function.BiPredicate;
import static us.bpsm.edn.parser.Parsers.defaultConfiguration;
import us.bpsm.edn.*;
import us.bpsm.edn.parser.*;

class SharedGenes implements BiPredicate {

  /* This class expects to receive two strings.
   * They should be maps specified in EDN format.
   * Calling test will return true only if the two
   * maps have at least one common element.
   *
   * You can use this in a where() step in tinkerpop.
   * First, create an instance:
   *     sharedGenes = new SharedGenes()
   * Then in the query:
   *     .where('child_genome', new P(sharedGenes, 'parent_genome'))
   */
  def boolean test(t, u){
    def pp = Parsers.newParser(defaultConfiguration())
    def genome0 = pp.nextValue(Parsers.newParseable(t))
    def genome1 = pp.nextValue(Parsers.newParseable(u))

    return genome0.any { g0 ->
      genome1.any { g1 ->
        g0 == g1
      }
    }
  }


}
