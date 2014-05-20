(ns monome-osc.utils-test
  (:require [clojure.test :refer :all]
            [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async]
            [monome-osc.core :refer :all]
            [clojure.pprint :refer [pprint]]))

(def log-chan (chan))

(thread
 (loop []
   (when-let [v (<!! log-chan)]
     (pprint v)
     (recur)))
 (println "Log Closed"))

;; (close! log-chan)

(defn log [& msgs]
  (doseq [msg msgs]
    (>!! log-chan (or msg "**nil**"))))

(defn log-loop [in]
  (go-loop []
           (when-let [e (<! in)]
             (log e)
             (recur)))
  in)
