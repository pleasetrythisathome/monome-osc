(ns monome-osc.core
  (:require [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async]
            [clojure.pprint :refer [pprint]])
  (:use [overtone.osc]
        [monome-osc.utils]
        [monome-osc.com]
        [monome-osc.conn]
        ;;[monome-osc.led]
        [monome-osc.anim]
        [monome-osc.arc]))

(comment
 ;; debug
 ;; (osc-debug true)
 ;; (osc-listen server log :debug)
 ;; (osc-rm-listener server :debug)

 (def watcher (watch-devices))
 ;; (close! watcher)
 (go-loop []
          (when-let [{:keys [action device]} (<! watcher)]
            (case action
              :connect (connect-animation device)
              :disconnect nil)
            (recur)))

 ;; tests

 (monitor-devices)

 (log (map class (get-devices)))
 (def monome (first (get-devices)))
 (log (class monome))

 (take! (get-info monome) log)

 (set-all monome 1)
 (set-all monome 0)

 (set-all-level monome 10)
 (set-all-level monome 15)

 (let [[w h] (:size monome)]
   (doseq [x (range w)
           y (range h)]
     (set-brightness monome x y x)))

 (set-led monome 0 0 1)
 (set-led monome 0 0 0)

 (def row-on (apply vector (repeat 10 1)))
 (def row-off (apply vector (repeat 10 0)))
 (set-row monome 0 0 [row-on])
 (set-row monome 0 0 [row-off])

 (set-column monome 0 0 [row-on])
 (set-column monome 0 0 [row-off])

 (connect-animation monome)

 (defn handle-event
   [[action args]]
   (case action
     :press (log :press args)
     :release (log :release args)
     :delta (log :delta args)
     :tilt nil))

 (def events (listen-to monome))
 (go-loop []
          (when-let [event (<! events)]
            (handle-event event)
            (recur)))
 ;; (close! events)


 ;; arc test

 (def arc (second (get-devices)))
 (log arc)

 (set-all arc 0 15)
 (set-all arc 0 0)
 (set-led arc 0 0 15)
 (set-led arc 0 0 0)
 (set-map arc 0 (map #(mod % 16) (range 64)))
 (set-range arc 0 0 15 15)
 (set-range arc 0 0 15 0) )
