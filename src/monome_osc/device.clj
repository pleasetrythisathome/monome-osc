(ns monome-osc.device
  (:require [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async])
  (:use [monome-osc.com]))

(defprotocol Device
  (listen-to [device]))

(defprotocol Grid
  (set-led [grid x y s])
  (set-all [grid s])
  (set-map [grid x-off y-off s])
  (set-row [grid x-off y s])
  (set-column [grid x y-off s])

  (set-led-level [grid x y s])
  (set-all-level [grid s])
  (set-map-level [grid x-off y-off s])
  (set-row-level [grid x-off y s])
  (set-column-level [grid x y-off s]))

(defn row->bitmask
  [row]
  (Integer/parseInt (apply str row) 2))

(defrecord Monome [info client]
  Device
  (listen-to
    [device]
    (let [prefix (:prefix device)
          key (tag-chan (fn [[x y s]] (case s
                                       0 :release
                                       1 :press)) (listen-path (str prefix "/grid/key")))
          tilt (tag-chan (constantly :tilt) (listen-path (str prefix "/tilt")))]
      (async/merge [key tilt])))
  Grid
  (set-led [grid x y s]
    (send-to monome "/grid/led/set" x y s))
  (set-all [grid s]
    (send-to monome "/grid/led/all" s))
  (set-map [grid x-off y-off s]
    ;; state is [8][8]
    (apply send-to monome "/grid/led/map" x-off y-off (map row->bitmask s)))
  (set-row [grid x-off y s]
    ;; x-off must be a multiple of 8, state is [n][8] rows to be updated
    (apply send-to monome "/grid/led/row" x-off y (map row->bitmask s)))
  (set-column [grid x y-off s]
    ;; y-off must be a multiple of 8, state is [n][8] columns to be updated
    (apply send-to monome "/grid/led/col" x y-off (map row->bitmask s)))

  (set-led-level [grid x y l]
    (send-to monome "/grid/led/level/set" x y l))
  (set-all-level [grid l]
    (send-to monome "/grid/led/level/all" l))
  (set-map-level [grid x-off y-off l]
    (apply send-to monome "/grid/led/level/map" x-off y-off (map row->bitmask l)))
  (set-row-level [grid x-off y l]
    (apply send-to monome "/grid/led/level/row" x-off y (map row->bitmask l)))
  (set-column-level [grid x y-off l]
    (apply send-to monome "/grid/led/level/col" x y-off (map row->bitmask l))))

(defrecord Arc [info client]
  Device
  (listen-to
    [device]
    (let [prefix (:prefix device)
          key (tag-chan (fn [[n s]] (case s
                                         0 :release
                                         1 :press)) (listen-path (str prefix "/enc/key")))
          enc (tag-chan (constantly :delta) (listen-path (str prefix "/enc/delta")))
          tilt (tag-chan (constantly :tilt) (listen-path (str prefix "/tilt")))]
      (async/merge [key enc tilt]))))

(defmulti create-device (fn [info client]
                          (every? zero? (:size info))))
(defmethod create-device true [info client]
  (Arc. info client))
(defmethod create-device false [info client]
  (Monome. info client))
