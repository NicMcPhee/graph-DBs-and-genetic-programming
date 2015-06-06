(ns fecundity-counts.core
  (:require [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.relationships :as nrl]
            [clojurewerkz.neocons.rest.cypher :as cy]
            [clojurewerkz.neocons.rest.paths :as np]
            [clojurewerkz.neocons.rest.index :as ni]
            [clojure.core.reducers :as r]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(def conn (nr/connect "http://localhost:7474/db/data/"))

;;   ON :Individual(generation)     ONLINE
;;   ON :Individual(run_uuid)       ONLINE
;;   ON :Individual(total_error)    ONLINE

; RUN THIS ON A NEW DB
; You have to wait until this finishes populating (use the "schema" command in the Neo4j shell)
; before you can continue. It takes 5-10 minutes.
(defn add-initial-schema []
  (ni/create conn "Individual" "generation")
  (ni/create conn "Individual" "run_uuid")
  (ni/create conn "Individual" "total_error"))

; (add-initial-schema)

; (ni/get-all conn "Individual")

(defn count-runs []
  (cy/tquery conn "MATCH (run:Run) RETURN COUNT(DISTINCT run)"))

; (time (count-runs))

(defn get-runs []
  (map #(get % "run") (cy/tquery conn "MATCH (run:Run) RETURN run")))

(def all-runs (get-runs))

; (time (first all-runs))

(defn get-property [node prop]
  (get-in node [:data prop]))

(defn get-run-uuid [run]
  (get-property run :run_uuid))

(defn get-run-max-generation [run]
  (get-property run :max-generation))

(defn get-run-id ^long [run]
  (get-in run [:metadata :id]))

; 26 gen id: "4c1a3b62-f859-42c5-9cf4-40863cb57624"
; 300 gen id: "921ffe71-fb23-420d-8a8b-047f74493f87"
; 300 gen id: 298ea2d4-8827-485e-9298-7b2b768b17f3

; (get-run-id (first all-runs))

(defn find-max-gen [run]
  (let [query (str "MATCH (n:Individual {run_uuid: \"" (get-run-uuid run) "\"}) USING INDEX n:Individual(run_uuid) RETURN MAX(n.generation)")
        run-query #(cy/tquery conn %)]
    (-> query
        run-query
        first
        vals
        first)))

; (time (find-max-gen (first all-runs)))

;; (defn max-gens [run-ids]
;;   (zipmap run-ids (pmap find-max-gen run-ids)))

;  (reduce (fn [m id] (assoc m id (find-max-gen id)))
;          {}
;          run-ids))

; (time (doall (max-gens (take 10 all-run-ids))))

(defn add-max-gen-property [runs]
  (pmap (fn [run]
          (dorun
           (let [max-gen (find-max-gen run)]
             (println (str (get-run-id run) "  " max-gen))
             ; I put this in brackets to make it a collection in the hopes that
             ; would make dorun happier?
             [(nn/set-property conn (get-run-id run) :max_generation max-gen)])))
        runs))

;;   (doseq [run runs
;;           :let [max-gen (find-max-gen run)]]
;;     (println (get-run-id run))
;;     (nn/set-property conn (get-run-id run) :max_generation max-gen)))

; RUN THIS ON A NEW DB
; This took about 30 minutes on the RSWN Tourney DB, and didn't seem to do any
; meaningful parallelism :-(
; (time (doall (add-max-gen-property all-runs)))

; match (n:Individual {run_uuid: "4b3686c3-8d3d-41b0-81b2-280b49d279ab"})  with n optional match (n)-[r:PARENT_OF]->(c) with n, count(r) as numSels, count(distinct c) as numKids set n += {num_selections: numSels, num_children: numKids};
; Took 6793ms, 1806001 db accesses
; Now trying it without the run_uuid restriction and we'll see how long *that* takes.
; It *totally* died, running over an hour and eventually dying with an java.io.EOFException.
; No idea what *that's* about.

; So I wrote this little bit of code that handled each run separately, and that took about 45 mins to complete.

(defn add-selection-offspring-count-properties-to-run [run]
  (let [match-run (str "MATCH (n:Individual {run_uuid: \"" (get-run-uuid run) "\"})")
        using-index "USING INDEX n:Individual(run_uuid) WITH n"
        find-children "OPTIONAL MATCH (n)-[r:PARENT_OF]->(c) WITH n, COUNT(r) AS numSels, COUNT(DISTINCT c) AS numKids"
        set-properties "SET n += {num_selections: numSels, num_children: numKids}"
        query (clojure.string/join " " [match-run using-index find-children set-properties])]
    (cy/tquery conn query)))

(defn add-selection-offspring-count-properties-to-runs [runs]
  (pmap (fn [run] (dorun (add-selection-offspring-count-properties-to-run run))) runs))

; This one doesn't parallelize very well because all the work is in the *big*
; query in add-selection-offspring-count-properties-to-run and none of the work
; is in Clojure land. Not sure how we'd fix that, though. Maybe we split it up
; by generation so we have a *lot* more smaller queries? It might make the
; transaction costs a lot smaller.
; Worse, this actually died part way through due to lack of memory. I explicitly set the
; number of web threads in the Neo4j server properties file to 4 to see what effect that
; might have. The memory problem, though, does make me wonder if the size of the transactions
; is the problem and we need to keep the size of the queries down.
; Setting the number of web threads to 4 may have solved the memory problem, but it definitely
; didn't speed anything up. This has been running for 2-3 hours and still isn't done. Sigh.
; Actually, it still ran out of memory and died, it just took longer to do it. So we're going
; to break this up by generation so the transactions aren't so big.
(time (doall (add-selection-offspring-count-properties-to-runs all-runs)))

;;   ON :Individual(num_selections) ONLINE
;;   ON :Individual(num_children)   ONLINE

(defn add-selection-count-schema []
  (ni/create conn "Individual" "num_selections")
  (ni/create conn "Individual" "num_children"))

; (add-selection-count-schema)

(defn total-selections-for-run-gen [run gen]
  (let [match-run-gen (str "MATCH (n:Individual {run_uuid: \"" (get-run-uuid run) "\", generation: " gen "})")
        sum-selections "RETURN SUM(n.num_selections)"
        query (clojure.string/join " " [match-run-gen sum-selections])]
    (->> query
         (cy/tquery conn)
         first
         vals
         first)))

(defn add-generation-nodes-for-run-gen [run gen]
  (let [total-selections (total-selections-for-run-gen run gen)
        gen-node (nn/create conn {:total_selections total-selections, :generation gen, :run_uuid (get-run-uuid run)})]
    (nl/add conn gen-node "Generation")))

(defn add-generation-nodes [runs]
  (pmap (fn [run]
          (dorun
           (doseq [gen (range inc (get-run-max-generation runs))]
             (let [total-selections (total-selections-for-run-gen run gen)
                   gen-node (nn/create conn {:total_selections total-selections, :generation gen, :run_uuid (get-run-uuid run)})]
               (nl/add conn gen-node "Generation"))))
          runs)))

;;   (doseq [run runs
;;           gen (range (inc (get-run-max-generation run)))]
;;     (add-generation-nodes-for-run-gen run gen)))

; This took about 18 hours, which suggests that ad-generation-nodes-for-run-gen is pretty messed up
; and needs to be tuned. I think the problem is the query in total-selections-for-run-gen. The problem
; might be that we're using the wrong index in that query?
; (time (add-generation-nodes all-runs))

; (time (total-selections-for-run-gen (nth all-runs 0) 200))
; (time (map (partial total-selections-for-run-gen (first all-runs)) (range 300)))

(defn get-gen-total-selections [run gen]
  (let [match-run-gen (str "MATCH (g:Generation {run_uuid: \"" (get-run-uuid run) "\", generation: " gen "})")
        total-selections "RETURN g.total_selections"
        query (clojure.string/join " " [match-run-gen total-selections])]
    (->> query
         (cy/tquery conn)
         first
         vals
         first)))

; (get-gen-total-selections (second all-runs) 10)

(defn get-individual-selections [run gen]
  (let [match-run-gen (str "MATCH (n:Individual {run_uuid: \"" (get-run-uuid run) "\", generation: " gen "})")
        using-index "USING INDEX n:Individual(run_uuid)"
        return-selections "RETURN n.num_selections"
        query (clojure.string/join " " [match-run-gen using-index return-selections])]
    (->> query
         (cy/tquery conn)
         (map #(get % "n.num_selections")))))

; (get-individual-selections (second all-runs) 10)

(defn compute-hyper-selections-for-run-gen [run gen proportions]
  (let [gen-total-selections (get-gen-total-selections run gen)
        individual-selections (get-individual-selections run gen)]
    (if (zero? gen-total-selections)
      '(0 0 0)
      (map (fn [target] (count (filter (fn [individual-selection]
                                         (>= individual-selection target))
                                       individual-selections)))
           (map (partial * gen-total-selections) proportions)))))

; (time (compute-hyper-selections-for-run-gen (first all-runs) 10 [0.01, 0.05, 0.1]))

(defn compute-hyper-selections-for-run [run proportions]
  (doall (for [gen (range (inc (get-run-max-generation run)))]
    (concat [(get-run-uuid run) gen]
            (compute-hyper-selections-for-run-gen run gen proportions)))))

; (time (doall (compute-hyper-selections-for-run (second all-runs) [0.01, 0.05, 0.1])))

(defn compute-hyper-selections [runs proportions]
  (apply concat (pmap #(compute-hyper-selections-for-run % proportions) runs)))
;; (defn compute-hyper-selections [runs proportions]
;;   (let [res (map #(future (compute-hyper-selections-for-run % proportions)) runs)]
;;     (reduce concat [] (map deref res))))
;; (defn compute-hyper-selections [runs proportions]
;;   (into [] (r/fold 1 r/cat r/cat (r/map #(compute-hyper-selections-for-run % proportions) runs))))

; (def sels (time (doall (compute-hyper-selections (take 10 all-runs) [0.01, 0.05, 0.1]))))

; sels

; (map (fn [i] (/ (reduce + (map #(nth % i) sels)) (* 1.0 (count sels)))) [2 3 4])

(defn generate-hyper-selection-csv [filename runs proportions]
  (let [selections (compute-hyper-selections runs proportions)]
    (with-open [out-file (io/writer filename)]
      (csv/write-csv out-file [["Run_UUID", "Generation", "1%", "5%", "10%"]])
      (csv/write-csv out-file selections))))

; (time (doall (generate-hyper-selection-csv "rswn_tournament_hyperselections.csv" (take 100 all-runs) [0.01, 0.05, 0.1])))

;; (time (count (filter #(> % 0)
;;                      (pmap #(:num_selections (nn/get-properties conn %)) (range 100000)))))

;; (time
;;  (reduce +
;;          0
;;          (pmap (fn [run]
;;                 (let [num-gens (first (vals (first (cy/tquery conn (str "MATCH (r:Run {run_uuid: '" (get-run-uuid run) "'}) RETURN r.max_generation")))))
;;                       sels (for [gen (range num-gens)]
;;                              (first (vals (first (cy/tquery conn (str "MATCH (g:Generation {run_uuid: '" (get-run-uuid run) "', generation: " gen "}) RETURN g.total_selections"))))))]
;;                   (float (/ (apply + sels) num-gens))))
;;               (take 10 all-runs))))

; match (n:Individual {run_uuid: "298ea2d4-8827-485e-9298-7b2b768b17f3", generation: 10}) with sum(n.num_selections) as total_selections match (n:Individual {run_uuid: "298ea2d4-8827-485e-9298-7b2b768b17f3", generation: 10}) where n.num_selections > 0.01 * total_selections return count(n);

; match (n:Individual {generation: 20, total_error: 0, run_uuid: "c39ae5a4-b2d6-40a8-bed3-b5750eccdc52"}) with n match (a:Individual {run_uuid: "c39ae5a4-b2d6-40a8-bed3-b5750eccdc52"}) where n.generation >= 13 with n, a match (a)-[:PARENT_OF*1..7]->(n) return distinct id(a), a.generation, a.num_selections, a.num_children order by a.generation;

; match (r:Run) with r.run_uuid as rid limit 1
; match (g:Generation {run_uuid: rid})
; with g.generation as gen, g.total_selections as tot_Sels, rid
; match (n:Individual {run_uuid: rid, generation: gen})
; using index n:Individual(run_uuid)
; where n.num_selections > 0.05*tot_Sels
; return gen, tot_Sels, count(n) order by gen;
