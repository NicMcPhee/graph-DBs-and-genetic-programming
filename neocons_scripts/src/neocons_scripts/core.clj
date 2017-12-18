(ns neocons-scripts.core
  (:require [clojure.string]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.relationships :as nrl]
            [clojurewerkz.neocons.rest.index :as ni]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [selmer.parser :as selmer])
  (:gen-class))

;; +---------------------+
;; | label               |
;; +---------------------+
;; | "Gene"              |
;; | "Individual"        |
;; | "RunConfigurations" |
;; | "SingleError"       |
;; | "TotalError"        |
;; +---------------------+

;; +---------------------+
;; | relationshipType    |
;; +---------------------+
;; | "HasGene"           |
;; | "HasTotalError"     |
;; | "SourceGeneOf"      |
;; | "ParentOf"          |
;; | "RunConfigurations" |
;; | "ContainsError"     |
;; +---------------------+

(defn long-query
  [& parts]
  (clojure.string/join " " parts))

(defn connect
  [user password]
  (def *neo4j-connection* (nr/connect "http://localhost:7474/db/data" user password)))

(defn count-individuals []
  (cy/tquery *neo4j-connection* "MATCH (i:Individual) return count(i)"))

(defn max-generation []
  (get (first (cy/tquery *neo4j-connection* "MATCH (i:Individual) return max(i.generation)"))
       "max(i.generation)"))

(defn build-indices []
  (ni/create *neo4j-connection* "Individual" "generation")
  (ni/create *neo4j-connection* "Individual" "location")
  (ni/create *neo4j-connection* "Individual" "ancestorOfWinner")
  (ni/create *neo4j-connection* "TotalError" "TotalError")
  (ni/create *neo4j-connection* "Gene" "instruction")
  (ni/create *neo4j-connection* "Gene" "close")
  (ni/create *neo4j-connection* "Gene" "ancestorOfWinner"))

(defn mark-ancestors-of-winners []
  (let [winners (cy/tquery *neo4j-connection*
                           "MATCH (w:Individual)-[:HasTotalError]->(e:TotalError {TotalError: 0}) SET w.ancestorOfWinner = true return w")]
    (when (empty? winners)
      (throw (IllegalStateException. "There are no winners in this database")))
    (doseq [child-gen (range (max-generation) 0 -1)]
      (dorun
       (cy/tquery
        *neo4j-connection*
        (selmer/render
         "MATCH (c:Individual {generation: {{gen}}, ancestorOfWinner: true})<-[:ParentOf]-(p:Individual) SET p.ancestorOfWinner = true"
         {:gen child-gen}))))
    (dorun
     (cy/tquery
      *neo4j-connection*
      "MATCH (g:Gene)<-[:HasGene]-(w:Individual)-[:HasTotalError]->(e:TotalError {TotalError: 0}) SET g.ancestorOfWinner = true"))
    (doseq [child-gen (range (max-generation) 0 -1)]
      (dorun
       (cy/tquery
        *neo4j-connection*
        (selmer/render
         (long-query
          "MATCH"
          "(pg:Gene)-[:SourceGeneOf]->(cg:Gene {ancestorOfWinner: true})<-[:HasGene]-(c:Individual {generation: {{gen}}})"
          "SET pg.ancestorOfWinner = true")
         {:gen child-gen}))))
    ))

(defn mark-gene-contributions []
  (doseq [child-gen (range (max-generation) 0 -1)]
    (dorun
     (cy/tquery
      *neo4j-connection*
      (selmer/render
       (long-query
        "MATCH (c:Individual {generation: {{gen}}})<-[r:ParentOf]-(p:Individual)"
        "WHERE (c)-[:HasGene]->(:Gene)<-[:SourceGeneOf]-(:Gene)<-[:HasGene]-(p)"
        "SET r.contributesGene = true")
       {:gen child-gen})))))

(defn mark-instruction-contributions []
  (dorun
   (pmap
    (fn [gen]
      (cy/tquery
       *neo4j-connection*
       (selmer/render
        (long-query
         "MATCH (c:Individual {generation: {{gen}}})<-[r:ParentOf]-(p:Individual)"
         "MATCH (c)-[:HasGene]->(cg:Gene)<-[:SourceGeneOf]-(pg:Gene)<-[:HasGene]-(p)"
         "WHERE cg.instruction = pg.instruction"
         "SET r.contributesInstruction = true")
        {:gen gen})))
    (range (max-generation) 0 -1))))

(defn delete-recombination-nodes []
  (cy/tquery *neo4j-connection* "match (r:Recombination)-[e]-() delete e")
  (cy/tquery *neo4j-connection* "match (r:Recombination) delete r"))

(defn semantic-combinations []
  (dorun
   (map
    (fn [gen]
      (cy/tquery
       *neo4j-connection*
       (selmer/render
        (long-query
         "MATCH (p1:Individual)-[r1:ParentOf]->(c:Individual {generation: {{gen}}})<-[r2:ParentOf]-(p2:Individual)"
         "WHERE id(r1)<id(r2)"
         "WITH DISTINCT p1, p2, c"
         "MATCH (p1)-[:HasTotalError]->(ps1:TotalError)"
         "MATCH (p2)-[:HasTotalError]->(ps2:TotalError)"
         "MATCH (c)-[:HasTotalError]->(cs:TotalError)"
         "WITH DISTINCT p1, p2, c, ps1, ps2, cs"
         "MERGE (ps1)-[:Recombines]->(rc:Recombination)<-[:Recombines]-(ps2)"
                                        ; "  ON CREATE SET rc.inputPairs = 1"
                                        ; "  ON MATCH SET rc.inputPairs = coalesce(rc.inputPairs, 0) + 1"
         "MERGE (rc)-[out:Generates]->(cs)")
                                        ; "  ON CREATE SET out.numCreated = 1"
                                        ; "  ON MATCH SET out.numCreated = coalesce(out.numCreated, 0) + 1")
        {:gen gen})))
    (range (max-generation) 0 -1))))

(defn semantic-combination-counts []
  (dorun
   (map
    (fn [gen]
      (cy/tquery
       *neo4j-connection*
       (selmer/render
        (long-query
         "MATCH (r:Recombination)"
         "MATCH (p1:Individual)-[:HasTotalError]->(ps1:TotalError)-[r1:Recombines]->(r)"
         "MATCH (p2:Individual)-[:HasTotalError]->(ps2:TotalError)-[r2:Recombines]->(r)"
         "WHERE id(r1)<id(r2) AND p1.location<=p2.location"
         "MATCH (p1)-[po1:ParentOf]->(c:Individual)<-[po2:ParentOf]-(p2)"
         "WHERE po1<>po2"
         "MATCH (r)-[g:Generates]->(ce:TotalError)<-[:HasTotalError]-(c)"
         "WITH r, g, count([p1, p2]), count(c)"

         "MATCH (p1:Individual)-[r1:ParentOf {contributesInstruction: true}]->(c:Individual {generation: {{gen}}})<-[r2:ParentOf {contributesInstruction: true}]-(p2:Individual)"
         "WHERE r1<>r2 AND p1.location<=p2.location"
         "WITH DISTINCT p1, p2, c"
         "MATCH (p1)-[:HasTotalError]->(ps1:TotalError)"
         "MATCH (p2)-[:HasTotalError]->(ps2:TotalError)"
         "MATCH (c)-[:HasTotalError]->(cs:TotalError)"
         "WITH DISTINCT p1, p2, c, ps1, ps2, cs"
         "MERGE (ps1)-[:Recombines]->(rc:Recombination)<-[:Recombines]-(ps2)"
                                        ; "  ON CREATE SET rc.inputPairs = 1"
                                        ; "  ON MATCH SET rc.inputPairs = coalesce(rc.inputPairs, 0) + 1"
         "MERGE (rc)-[out:Generates]->(cs)"
                                        ; "  ON CREATE SET out.numCreated = 1"
                                        ; "  ON MATCH SET out.numCreated = coalesce(out.numCreated, 0) + 1"
         "RETURN p1.location, p2.location, c.location, ps1.TotalError, ps2.TotalError, cs.TotalError")
        {:gen gen})))
    (range (max-generation) 0 -1))))

;; match (i:Individual {generation: 39})-[:HasTotalError]->(ie:TotalError)
;;        match (j:Individual {generation: 39})-[:HasTotalError]->(je:TotalError) where i.location<=j.location
;;        match (ie)-[ri:Recombines]->(r:Recombination)<-[rj:Recombines]-(je) where id(ri)<id(rj)
;;        match (i)-[:ParentOf]->(c:Individual)<-[:ParentOf]-(j)
;;        match (r)-[:Generates]->(ce:TotalError)<-[:HasTotalError]-(c)
;;        with ie.TotalError as tie, je.TotalError as tje, ce.TotalError as te, count(c) as cc return tie, tje, te, cc order by cc limit 100;

(defn gene-uuid [gene]
  (get-in gene [:data :uuid]))

(defn gene-instruction [gene]
  (get-in gene [:data :instruction]))

(defn graph-gene-ancestry [out-file]
  (spit out-file "digraph G {\n")
  (let [gene-pairs
        (cy/tquery
         *neo4j-connection*
         "match (pg:Gene {ancestorOfWinner: true})-[r]->(cg:Gene {ancestorOfWinner: true}) return pg, cg")]
    (dorun
     (doseq [p gene-pairs]
       (spit out-file
        (selmer/render
         "  \"{{pg}}\" -> \"{{cg}}\";\n"
         {:pg (gene-uuid (get p "pg"))
          :cg (gene-uuid (get p "cg"))})
        :append true)
       (spit out-file
        (selmer/render
         "  \"{{pg}}\" [label=\"{{pInstr}}\"];\n"
         {:pg (gene-uuid (get p "pg"))
          :pInstr (gene-instruction (get p "pg"))})
        :append true))))
  (let [adjacent-genes
        (cy/tquery
         *neo4j-connection*
         "match (lg:Gene {ancestorOfWinner: true})<-[:HasGene]-(:Individual)-[:HasGene]->(rg:Gene {ancestorOfWinner: true}) where lg.position+1=rg.position return lg, rg")]
    (dorun
     (doseq [p adjacent-genes]
       (spit
        out-file
        (selmer/render
         "  \"{{lg}}\" -> \"{{rg}}\";\n"
         {:lg (gene-uuid (get p "lg"))
          :rg (gene-uuid (get p "rg"))})
        :append true))))
  (spit out-file "}\n" :append true))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
