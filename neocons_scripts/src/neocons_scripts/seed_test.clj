(ns neocons-scripts.seed-test
  (:require [clojure.string]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.relationships :as nrl]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.index :as ni]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [selmer.parser :as selmer]
            [neocons-scripts.core :as core])
  (:gen-class))

(defn empty-database []
  (cy/tquery core/*neo4j-connection* "match ()-[e]-() delete e")
  (cy/tquery core/*neo4j-connection* "match (n) delete n"))

(defn create-parent-of-edges
  [parent children]
  (doseq [c children]
    (cy/tquery core/*neo4j-connection*
      (selmer/render "match (p:Individual {location: \"{{parent}}\"}) match (c:Individual {location: \"{{child}}\"}) create (p)-[:ParentOf]->(c)"
        {:parent parent, :child c}))))

;; The number of input pairs should be:
;;   [0, 2]: 3 (E,F; D,G; D,E); outputs 2 for 1, 1, 2
;;   [0, 0]: 3 (A,C; C,C; D,D); outputs 2 for 1, 1 for 0
;;   [0, 1]: 3 (B,C; A,B; A,B); outputs 1 for 0, 2 for 2

(defn seed-test-db []
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 0, location: \"A\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 0, location: \"B\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 0, location: \"C\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 1, location: \"D\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 1, location: \"E\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 1, location: \"F\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 1, location: \"G\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 1, location: \"H\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 2, location: \"I\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 2, location: \"J\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 2, location: \"K\"})")
  (cy/tquery core/*neo4j-connection* "create (:Individual {generation: 2, location: \"L\"})")
  (cy/tquery core/*neo4j-connection* "create (:TotalError {TotalError: 0})")
  (cy/tquery core/*neo4j-connection* "create (:TotalError {TotalError: 1})")
  (cy/tquery core/*neo4j-connection* "create (:TotalError {TotalError: 2})")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"A\"}) match (e:TotalError {TotalError: 0}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"B\"}) match (e:TotalError {TotalError: 1}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"C\"}) match (e:TotalError {TotalError: 0}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"D\"}) match (e:TotalError {TotalError: 0}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"E\"}) match (e:TotalError {TotalError: 2}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"F\"}) match (e:TotalError {TotalError: 0}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"G\"}) match (e:TotalError {TotalError: 2}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"H\"}) match (e:TotalError {TotalError: 1}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"I\"}) match (e:TotalError {TotalError: 2}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"J\"}) match (e:TotalError {TotalError: 1}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"K\"}) match (e:TotalError {TotalError: 1}) create (i)-[:HasTotalError]->(e)")
  (cy/tquery core/*neo4j-connection* "match (i:Individual {location: \"L\"}) match (e:TotalError {TotalError: 1}) create (i)-[:HasTotalError]->(e)")
  (create-parent-of-edges "C" ["E", "H", "H", "F"])
  (create-parent-of-edges "B" ["G", "D", "E"])
  (create-parent-of-edges "A" ["G", "D", "F"])
  (create-parent-of-edges "D" ["I", "J", "J", "K"])
  (create-parent-of-edges "E" ["K", "L"])
  (create-parent-of-edges "F" ["L"])
  (create-parent-of-edges "G" ["I"]))
