(ns monome-osc.events
  (:require [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async])
  (:use [overtone.osc]
        [monome-osc.device]))

(defonce events (atom {}))

(defn reset-events! [device]
  (let [id (get-in device [:info :id])
        listener (pub (listen-to device) first)]
    (when-let [old (get @events id)]
      (close! old))
    (swap! events assoc id listener)))

(defn sub-device [device key]
  (let [{:keys [id]} (:info device)
        out (chan (sliding-buffer 1))]
    (sub (get @events id) key out)
    (async/map< second out)))
