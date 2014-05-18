(ns monome-osc.core
  (:require [monome-osc com conn device])
  (:use [overtone.helpers.ns]))

(immigrate
 'monome-osc.com
 'monome-osc.conn
 'monome-osc.device)

(monitor-devices)

;; debug
;; (o/osc-debug true)
;; (o/osc-debug false)
;; (o/osc-listen server log-c :debug)
;; (o/osc-rm-listener server :debug)

(def watcher (watch-devices))
;; (close! watcher)
(go-loop []
         (when-let [{:keys [action device]} (<! watcher)]
           (log-c action device)
           (recur)))

;; tests

(monitor-devices)

(def monome (get-device :monome))
(log-c (:info monome))

(set-all monome 1)
(set-all monome 0)

(set-all-level monome 10)
(set-all-level monome 15)

(let [[w h] (get-in monome [:info :size])]
  (doseq [x (range w)
          y (range h)]
    (set-led-level monome x y x)))

(set-led monome 0 0 1)
(set-led monome 0 0 0)

(def row-on (into [] (repeat 8 1)))
(def row-off (into [] (repeat 8 0)))
(set-row monome 0 0 [row-on])
(set-row monome 0 0 [row-off])

(set-column monome 0 0 [row-on])
(set-column monome 0 0 [row-off])

(set-map monome 0 0 (for [y (range 8)]
                      (into [] (repeat 8 1))))
(set-map-level monome 0 0 (for [y (range 8)]
                            (into [] (map #(+ % y) (range 8)))))

(connect-animation monome)

(defn handle-event
  [[action args]]
  (case action
    :press (log-c :press args)
    :release (log-c :release args)
    :delta (log-c :delta args)
    :tilt nil))

(def events (listen-to monome))
(go-loop []
         (when-let [event (<! events)]
           (handle-event event)
           (recur)))
;; (close! events)


;; arc test

(def arc (get-device :arc))
(log-c (:info arc))

(connect-animation arc)

(doseq [n (range 4)]
  (set-all arc n 0))

(set-all arc 0 15)
(set-all arc 0 0)
(set-led arc 0 0 15)
(set-led arc 0 0 0)
(set-map arc 0 (map #(mod % 16) (range 64)))
(set-range arc 0 0 15 15)
(set-range arc 0 0 15 0)

(def events (listen-to arc))
;; (close! events)
(go-loop []
         (when-let [event (<! events)]
           (handle-event event)
           (recur)))
