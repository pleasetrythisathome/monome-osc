(ns monome-osc.device
  (:require [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async])
  (:use [monome-osc.com]))

(defprotocol Device
  (listen-to [device])
  (set-led
    [device x y s])
  (set-all
    [grid s]
    [arc n l])
  (set-map
    [grid x-off y-off s]
    [arc n l])
  (clear [device]))

(defprotocol Grid
  (set-row [grid x-off y s])
  (set-column [grid x y-off s])

  (let-led-level [grid x y l])
  (let-all-level [grid l])
  (let-map-level [grid x-off y-off l])
  (let-row-level [grid x-off y l])
  (let-column-level [grid x y-off l]))

(defprotocol Ring
  (set-range [arc n x1 x2 l]))

(defprotocol Animation
  (connect-animation [device]))

(defrecord Monome [info client]
  Device
  (listen-to
    [device]
    (let [prefix (get-in device [:info :prefix])
          key (tag-chan (fn [[x y s]] (case s
                                       0 :release
                                       1 :press)) (listen-path (str prefix "/grid/key")))
          tilt (tag-chan (constantly :tilt) (listen-path (str prefix "/tilt")))]
      (async/merge [key tilt])))

  (set-led [grid x y s]
    (send-to grid "/grid/led/set" x y s))
  (set-all [grid s]
    (send-to grid "/grid/led/all" s))
  (set-map [grid x-off y-off s]
    ;; state is [8][8]
    (apply send-to grid "/grid/led/map" x-off y-off (map row->bitmask s)))
  [clear [grid]
   (set-all grid 0)]
  Grid
  (set-row [grid x-off y s]
    ;; x-off must be a multiple of 8, state is [n][8] rows to be updated
    (apply send-to grid "/grid/led/row" x-off y (map row->bitmask s)))
  (set-column [grid x y-off s]
    ;; y-off must be a multiple of 8, state is [n][8] columns to be updated
    (apply send-to grid "/grid/led/col" x y-off (map row->bitmask s)))

  (set-led-level [grid x y l]
    (send-to grid "/grid/led/level/set" x y l))
  (set-all-level [grid l]
    (send-to grid "/grid/led/level/all" l))
  (set-map-level [grid x-off y-off l]
    (apply send-to grid "/grid/led/level/map" x-off y-off (map row->bitmask l)))
  (set-row-level [grid x-off y l]
    (apply send-to grid "/grid/led/level/row" x-off y (map row->bitmask l)))
  (set-column-level [grid x y-off l]
    (apply send-to grid "/grid/led/level/col" x y-off (map row->bitmask l)))
  Animation
  (connect-animation [monome]
    (let [size (get-in monome [:info :size])
          speed 25
          cols (first size)
          rows (second size)
          on (apply vector (repeat rows 1))
          off (apply vector (repeat rows 0))]
      (go-loop [col 0]
               (set-column monome col 0 [on])
               (<! (timeout speed))
               (set-column monome col 0 [off])
               (when (< col cols)
                 (recur (inc col)))))))

(defrecord Arc [info client]
  Device
  (listen-to
    [device]
    (let [prefix (get-in device [:info :prefix])
          key (tag-chan (fn [[n s]] (case s
                                     0 :release
                                     1 :press)) (listen-path (str prefix "/enc/key")))
          enc (tag-chan (constantly :delta) (listen-path (str prefix "/enc/delta")))
          tilt (tag-chan (constantly :tilt) (listen-path (str prefix "/tilt")))]
      (async/merge [key enc tilt])))

  (set-led [arc n x l]
    (send-to arc "/ring/set" n x l))
  (set-all [arc n l]
    (send-to arc "/ring/all" n l))
  (set-map [arc n l]
    ;; l is a list of 64 integers (< 0 x 16)
    (apply send-to arc "/ring/map" n l))
  (clear [arc]
    (doseq [n (range 4)]
      (set-all arc n 0)))
  Ring
  (set-range [arc n x1 x2 l]
    (send-to arc "/ring/range" n x1 x2 l))
  Animation
  (connect-animation [arc]
    (letfn [(abs [n] (max n (- n)))
            (map-range [x start end new-start new-end]
              (+ new-start (* (- new-end new-start) (if (zero? x)
                                                      start
                                                      (/ (- x start) (- end start))))))
            (circle [arc n speed]
              (go
               (doseq [led (range 64)]
                 (let [l (- 15 (Math/floor (map-range (abs (- led 32)) 0 32 0 15)))]
                   (set-led arc n led l))
                 (<! (timeout speed))
                 (set-led arc n led 0))))]
      (go
       (doseq [n (range 12)]
         (<! (timeout 100))
         (circle arc (mod n 4) 15))))))

(defmulti create-device (fn [info client]
                          (every? zero? (:size info))))
(defmethod create-device true [info client]
  (Arc. info client))
(defmethod create-device false [info client]
  (Monome. info client))
