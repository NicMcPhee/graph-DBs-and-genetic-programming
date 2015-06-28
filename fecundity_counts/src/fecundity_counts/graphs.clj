(ns fecundity-counts.graphs
  (:require [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.relationships :as nrl]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [clojurewerkz.neocons.rest.paths :as np]
            [clojurewerkz.neocons.rest.index :as ni]
            [clojure.core.reducers :as r]
            [clojure.java.io :as io]
            [rhizome.viz :as rviz]
            [rhizome.dot :as rdot]
            [fecundity-counts.core :as core])
  (:gen-class))

(use 'rhizome.viz)

;; (def g
;;     {:a [:b :c]
;;          :b [:c]
;;          :c [:a]})

;; (view-graph (keys g) g
;;     :node->descriptor (fn [n] {:label (str "Node: " n)}))

(def run6id "e2e96342-ab91-4b85-900a-fd9d9e2ed094")

; query: match (n:Individual {run_uuid: "e2e96342-ab91-4b85-900a-fd9d9e2ed094"}) with n order by n.num_selections desc limit 440 return id(n), n.generation, n.num_selections order by n.generation;

(defn get-hyper-selections
  "Get the num-hyper-selections individuals from the specified run
   with the highest number of selections."
   [run-uuid num-hyper-selections]
  (let [match (str "MATCH (n:Individual {run_uuid: \"" run-uuid "\"})")
        with (str "WITH n ORDER BY n.num_selections desc limit " num-hyper-selections)
        return "RETURN n, id(n) ORDER BY n.generation"
        query (clojure.string/join " " [match with return])]
    (map #(assoc (get-in % ["n" :data]) :id (% "id(n)"))
         (cy/tquery core/conn query))))

;;          first
;;          vals
;;          first)))

(def hyper-selections (get-hyper-selections run6id 440))

(first hyper-selections)

(def hyper-sels-by-gen
  (map (partial sort-by :id)
       (partition-by :generation hyper-selections)))

(first hyper-sels-by-gen)

(defn nodes->start
  "Convert a collection of nodes (from get-hyper-selections)
  to a labelled list that can be used in a START section of
  a Cypher query."
  [node-label nodes]
  (str node-label
       "=node("
       (clojure.string/join "," (map :id nodes))
       ")"))

(nodes->start "s" (first hyper-sels-by-gen))

(defn get-edges-from-db
  "Get all edges (if there are any) from nodes whose IDs are in the
  first collection to nodes whose IDs are in the second collection."
  [source-nodes destination-nodes]
  (let [start "START"
        sources (str (nodes->start "s" source-nodes) ", ")
        destinations (nodes->start "d" destination-nodes)
        match "MATCH (s)-[:PARENT_OF]->(d)"
        return "RETURN s, id(s), d, id(d)"
        query (clojure.string/join " " [start sources destinations match return])]
    (map (fn [e] {(assoc (get-in e ["s" :data]) :id (e "id(s)"))
                  (assoc (get-in e ["d" :data]) :id (e "id(d)"))})
         (cy/tquery core/conn query))))

(def edges (get-edges-from-db (first hyper-sels-by-gen) (second hyper-sels-by-gen)))

edges

(apply merge-with concat
       (map (fn [e] {(first (keys e)) [(first (vals e))]}) edges))

(defn edges->graph
  "Convert collection of edges labelled 'id(s)' for source
  and 'id(d)' for destination to a map that maps source IDs to
  collections of destination IDs"
  [edges]
  (apply merge-with concat
         (map (fn [e] {(first (keys e)) [(first (vals e))]}) edges)))

(def graph-edges
  (apply (partial merge-with concat)
         (map #(edges->graph (get-edges-from-db (first %) (second %)))
              (partition 2 1 hyper-sels-by-gen))))
;                             (edges->graph (get-edges-from-db (first hyper-sels-by-gen) (second hyper-sels-by-gen)))
;                             (edges->graph (get-edges-from-db (second hyper-sels-by-gen) (nth hyper-sels-by-gen 2)))))

graph-edges

(def early-nodes (concat (first hyper-sels-by-gen) (second hyper-sels-by-gen)))

early-nodes

(def gen-nodes (for [g (range 87)] {:generation g, :num_selections 0}))
(def gen-edges
  (apply merge
         (for [p (partition 2 1 gen-nodes)]
           {(first p) [(second p)]})))

;  (let [pairs (partition 2 1 gen-nodes)]
;    (array-map pairs)))

gen-edges

(merge graph-edges gen-edges)

(rviz/view-graph (concat hyper-selections gen-nodes)
                 (merge graph-edges gen-edges)
                 ;(fn [n] (println (graph-edges (n "id(n)"))) (graph-edges (n "id(n)")))
                 ; (fn [n] (filter #(= (inc (n "n.generation")) (% "n.generation")) hyper-selections))
                 :node->descriptor (fn [n] {:label "",
                                            :shape "box",
                                            :width (/ (:num_selections n) 500.0),
                                            :height 0.1,
                                            :fixedwidth true
                                            :color (if (zero? (:num_selections n))
                                                     "white"
                                                     "black")})
                 :edge->descriptor (fn [s d] {:arrowsize 0.5
                                              :color (if (zero? (:num_selections s))
                                                       "white"
                                                       "black")
                                              :style (if (zero? (:num_selections s))
                                                       "invis"
                                                       "")})
                 :cluster->descriptor (fn [n] {:label n :labeljust "l"
                                               :color "white"})
                 :node->cluster :generation)

(with-open [out-file (io/writer "run6_lexicase_rswn_440.dot")]
  (.write out-file
          (rdot/graph->dot
           (concat hyper-selections gen-nodes)
           (merge graph-edges gen-edges)
           ;(fn [n] (println (graph-edges (n "id(n)"))) (graph-edges (n "id(n)")))
           ; (fn [n] (filter #(= (inc (n "n.generation")) (% "n.generation")) hyper-selections))
           :node->descriptor (fn [n] {:label "",
                                      :shape "box",
                                      :width (/ (:num_selections n) 500.0),
                                      :height 0.1,
                                      :fixedwidth true
                                      :color (if (zero? (:num_selections n))
                                               "white"
                                               "black")})
           :edge->descriptor (fn [s d] {:arrowsize 0.5
                                        :color (if (zero? (:num_selections s))
                                                 "white"
                                                 "black")
                                        :style (if (zero? (:num_selections s))
                                                 "invis"
                                                 "")})
           :cluster->descriptor (fn [n] {:label n :labeljust "l"
                                         :color "white"})
           :node->cluster :generation)))

(concat [5 8 9] (for [g (range 87)] {:generation g}))
