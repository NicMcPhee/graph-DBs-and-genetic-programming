(ns neocons-scripts.pageranks
  (:require [clojure.string]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.relationships :as nrl]
            [clojurewerkz.neocons.rest.index :as ni]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [selmer.parser :as selmer])
  (:use neocons-scripts.core)
  (:gen-class))

(defn delete-inverted-gene-edges
  [edge-label]
  (doseq [gen (range 0 (inc (max-generation)))]
    (cy/tquery
     *neo4j-connection*
     (selmer/render
      (long-query
         "match (i:Individual {generation: {{gen}}})"
         "match (i)-[:HasGene]->(:Gene)-[e:{{edge-label}}]->(:Gene)"
         "delete e")
      {:gen gen, :edge-label edge-label}))))

(defn do-invert-gene-edges
  [field-name edge-label]
  (dorun
   (map
    (fn [gen]
      (cy/tquery
       *neo4j-connection*
       (selmer/render
        (long-query
         "match (i:Individual {generation: {{gen}}})"
         "match (i)-[:HasGene]->(pg:Gene)-[:SourceGeneOf]->(cg:Gene)"
         "where pg.{{field-name}} = cg.{{field-name}}"
         "create (cg)-[:{{edge-label}}]->(pg)")
        {:gen gen, :field-name field-name, :edge-label edge-label})))
    (range 0 (inc (max-generation))))))

(defn invert-gene-edges
  "Take all the SourceGeneOf edges and creates 'reverse' edges for use in Page Rank
   calculations. This creates one edge for when the instruction matches, and another
   edge for when the close count matches. This will (hopefully) allow the Page Rank
   algorithm to more heavily weight situations where a gene is an exact copy over
   situations where one of the components was mutated."
  []
  (do-invert-gene-edges "instruction" "GetsInstructionFrom")
  (do-invert-gene-edges "close" "GetsCloseFrom"))

(defn invert-parent-of-edges
  []
  (doseq [gen (range 0 (inc (max-generation)))]
    (cy/tquery
      *neo4j-connection*
      (selmer/render
        (long-query
	 "match (p:Individual {generation: {{gen}}})-[:ParentOf]->(c:Individual)"
	 "create (c)-[:ChildOf]->(p)")
	{:gen gen}))))

(defn set-page-rank
  [r count]
  (let [id (long (get r "id(node)"))
        score (get r "score")]
    (when (zero? (rem count 1000))
    	  (println "Processing node " + count + "\n"))
    (nn/set-property *neo4j-connection* id "pageRank" score)))

(defn calculate-pageranks
  [node-label edge-labels]
  (let [edge-types (clojure.string/join "|" edge-labels)
        results (cy/tquery
		 *neo4j-connection*
		 (selmer/render
		  (long-query
		   "MATCH (n:{{node-label}})"
		   "WITH COLLECT(n) AS nodes"
		   ; "CALL apoc.algo.pageRankWithConfig(nodes,{iterations:10,types:'{{edge-types}}'}) YIELD node, score"
		   "CALL apoc.algo.pageRankWithConfig(nodes,{types:'{{edge-types}}'}) YIELD node, score"
		   "RETURN id(node), score")
		  {:node-label node-label, :edge-types edge-types}))]
     (println "Computed page ranks.\n")
     (dorun
      (pmap set-page-rank results (range)))))

(defn calculate-individual-pageranks
  []
  (calculate-pageranks "Individual" ["ChildOf"]))

(defn calculate-gene-pageranks
  []
  (calculate-pageranks "Gene" ["GetsInstructionFrom"])) ; , "GetsCloseFrom"]))

(defn print-all-pageranks
  []
  (doseq [ind (cy/tquery
               *neo4j-connection*
               (long-query
                "match (i:Individual)-[:HasTotalError]->(te:TotalError)"
                "match (i)-[e:ParentOf]->(c:Individual)"
                "with i.generation as generation, i.pageRank as pageRank, te.TotalError as totalError, count(e) as numSelections, count(distinct c) as numChildren"
                "return generation, totalError, pageRank, numSelections, numChildren"))]
    (spit "all_pageranks.csv" (str (clojure.string/join "\t" (vals ind)) "\n") :append true)))
