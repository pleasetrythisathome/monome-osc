(ns monome-osc.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async]
            [monome-osc.core :refer :all])
  (:use [monome-osc.utils-test]))

;; debug
;; (osc-debug true)
;; (osc-debug false)
;; (osc-listen server (fn [& args] (doseq [a args] (pprint a)) :debug))
;; (osc-rm-listener server :debug)

(def watcher (log-loop (watch-devices)))
;; (close! watcher)

;; tests

(monitor-devices)

(def monome (get-device :monome))
(pprint (:info monome))
(set-rotation monome 180)

(set-all monome 1)
(reset-device monome)

(set-all-level monome 10)
(set-led monome 0 0 1)

(let [[w h] (get-in monome [:info :size])]
  (doseq [x (range w)
          y (range h)]
    (set-led-level monome x y x)))

(set-row monome 0 0 (repeat 8 1))
(set-column monome 0 0 (repeat 8 1))

(set-row-level monome 0 0 (repeat 8 10))
(set-column-level monome 0 0 (repeat 8 10))

(set-map monome 0 0 (for [y (range 8)]
                      (into [] (repeat 8 1))))
(set-map-level monome 0 0 (mapcat identity (for [y (range 8)]
                                             (into [] (map #(+ % y) (range 8))))))

(connect-animation monome)


(def all-events (listen-to monome))
(def mult-events (mult all-events))
(def test (chan))
(tap mult-events test)


(def logger (log-loop (filter< #(= [0 1] %) (sub-events monome :release))))
;; (close! logger)
(reset-events! monome)



;; arc test

(def arc (get-device :arc))
(pprint (:info arc))

(connect-animation arc)

(reset-device arc)

(set-all arc 0 15)
(set-all arc 0 0)
(set-led arc 0 0 15)
(set-led arc 0 0 0)
(set-map arc 0 (map #(mod % 16) (range 64)))
(set-range arc 0 0 15 15)
(set-range arc 0 0 15 0)

(def arc-events (log-loop (listen-to arc)))
;; (close! arc-events)
