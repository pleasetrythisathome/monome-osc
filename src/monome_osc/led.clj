(ns monome-osc.led
  (:refer-clojure :exclude [map reduce into partition partition-by take merge])
  (:require [clojure.core.async :refer :all :as async]
            [clojure.pprint :refer [pprint]])
  (:use [overtone.osc]
        [monome-osc.com]))

(defn row->bitmask
  [row]
  (Integer/parseInt (apply str row) 2))

(defn set-led
  [monome x y state]
  (send-to monome "/grid/led/set" x y state))

(defn set-all
  [monome state]
  (send-to monome "/grid/led/all" state))

(defn set-quad
  "state is a [] size 8 where [[row] ...]"
  [monome x-off y-off state]
  (send-to monome "/grid/led/map" x-off y-off (map row->bitmask state)))

(defn set-row
  "x-off must be a multiple of 8"
  [monome x-off y state]
  (send-to monome "/grid/led/row" x-off y (row->bitmask state)))

(defn set-column
  "x-off must be a multiple of 8"
  [monome x y-off state]
  (send-to monome "/grid/led/col" x y-off (row->bitmask state)))

(defn connect-animation
  [{:keys [size] :as monome}]
  (let [speed 25
        cols (first size)
        rows (second size)
        on (apply vector (repeat rows 1))
        off (apply vector (repeat rows 0))]
    (go
     (loop [col 0]
       (set-column monome col 0 on)
       (<! (timeout speed))
       (set-column monome col 0 off)
       (when (< col cols)
         (recur (inc col)))))))
