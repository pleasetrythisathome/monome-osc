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

 (log (get-devices))
 (def monome (first (get-devices)))
 (log monome)

 (take! (get-info monome) log)

 (set-all monome 1)
 (set-all monome 0)

 (set-brightness-all monome 10)
 (set-brightness-all monome 15)

 (let [[w h] (:size monome)]
   (doseq [x (range w)
           y (range h)]
     (set-brightness monome x y x)))

 (set-led monome 0 0 1)
 (set-led monome 0 0 0)

 (def row-on (apply vector (repeat 10 1)))
 (def row-off (apply vector (repeat 10 0)))
 (set-row monome 0 0 row-on)
 (set-row monome 0 0 row-off)

 (set-column monome 0 0 row-on)
 (set-column monome 0 0 row-off)

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

 (def arc (first (get-devices)))
 (log arc)

 (send-to arc "/ring/all" 0 15)
 (send-to arc "/ring/all" 0 0)
 (send-to arc "/ring/set" 0 0 15)
 (send-to arc "/ring/set" 0 0 0)
 (apply send-to arc "/ring/map" 0 (map #(mod % 16) (range 64)))
 (send-to arc "/ring/range" 0 0 15 15)
 (send-to arc "/ring/range" 0 0 15 0)

 (defn abs [n] (max n (- n)))
 (defn map-range [x start end new-start new-end]
   (+ new-start (* (- new-end new-start) (if (zero? x)
                                           start
                                           (/ (- x start) (- end start))))))
 (defn circle [arc n speed]
   (go
    (doseq [led (range 64)]
      (let [l (- 15 (Math/floor (map-range (abs (- led 32)) 0 32 0 15)))]
        (send-to arc "/ring/set" n led l))
      (<! (timeout speed))
      (send-to arc "/ring/set" n led 0))))

 (circle arc 0 20)
 (go
  (doseq [n (range 12)]
    (<! (timeout 100))
    (circle arc (mod n 4) 15)))

 )
