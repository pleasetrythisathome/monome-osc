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
        listener (listen-to device)]
    (when-let [old (get @events id)]
      (close! old))
    (swap! events assoc id {:listener  listener
                            :mult (mult listener)
                            :pub  (pub listener first)})))

(defn sub-events [device key]
  (let [{:keys [id]} (:info device)
        out (chan (sliding-buffer 1))]
    (sub (get-in @events [id :pub]) key out)
    (map< second out)))

(defn tap-events [device]
  (let [{:keys [id]} (:info device)
        out (chan (sliding-buffer 1))]
    (tap (get-in @events [id :mult]) out)
    out))
