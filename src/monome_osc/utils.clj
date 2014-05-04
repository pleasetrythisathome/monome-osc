(ns monome-osc.utils
  (:refer-clojure :exclude [map reduce into partition partition-by take merge])
  (:require [clojure.core.async :refer :all :as async]
            [clojure.pprint :refer [pprint]])
  (:use [overtone.osc]))

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
