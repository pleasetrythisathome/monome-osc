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
        listener (listen-to device)
        m (mult listener)
        p (let [c (chan)]
            (tap m c)
            (pub c first))]
    (when-let [old (get-in @events [id :listener])]
      (close! old))
    (swap! events assoc id {:listener  listener
                            :mult m
                            :pub  p})))

(defn tap-events [device]
  (let [{:keys [id]} (:info device)
        out (chan)]
    (tap (get-in @events [id :mult]) out)
    out))

(defn sub-events
  ([device] (tap-events device))
  ([device key]
     (let [{:keys [id]} (:info device)
           out (chan)]
       (sub (get-in @events [id :pub]) key out)
       (map< second out))))
