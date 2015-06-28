(defproject fecundity_counts "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clojurewerkz/neocons "3.0.0"]
                 [org.clojure/data.csv "0.1.2"]
                 [rhizome "0.2.5"]]
  :main ^:skip-aot fecundity-counts.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :jvm-opts ["-Xms1g" "-Xmx2g"])
