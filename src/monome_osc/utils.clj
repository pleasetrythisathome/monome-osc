(ns monome-osc.utils
  (:require [clojure.core.async :refer [<!! >!! thread chan]]
            [clojure.pprint :refer [pprint]]))

(def log-chan (chan))

(thread
 (loop []
   (when-let [v (<!! log-chan)]
     (pprint v)
     (recur)))
 (println "Log Closed"))

;; (close! log-chan)

(defn log-c [& msgs]
  (doseq [msg msgs]
    (>!! log-chan (or msg "**nil**"))))

(defn row->bitmask
  [row]
  (Integer/parseInt (apply str row) 2))
