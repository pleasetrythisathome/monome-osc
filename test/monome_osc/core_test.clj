(ns monome-osc.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go go-loop <! chan timeout close! thread alts! >!! <!!]]
            [monome-osc.core :refer :all]
            [clojure.pprint :refer [pprint]])
  (:use [overtone.osc]))

;; debug
;; (osc-debug true)
;; (osc-debug false)
;; (osc-listen server (fn [& args] (doseq [a args] (pprint a)) :debug))
;; (osc-rm-listener server :debug)

(def log-chan (chan))

(thread
 (loop []
   (when-let [v (<!! log-chan)]
     (pprint v)
     (recur)))
 (println "Log Closed"))

;; (close! log-chan)

(defn log [& msgs]
  (doseq [msg msgs]
    (>!! log-chan (or msg "**nil**"))))

(def watcher (watch-devices))
;; (close! watcher)
(go-loop []
         (when-let [{:keys [action device]} (<! watcher)]
           (pprint action device)
           (recur)))

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

(defn handle-event
  [[action args]]
  (pprint action args)
  (case action
    :press (pprint :press args)
    :release (pprint :release args)
    :delta (pprint :delta args)
    :tilt nil))

(def events (listen-to monome))
(go-loop []
         (when-let [event (<! events)]
           (handle-event event)
           (recur)))
;; (close! events)

(defn log-loop [in]
  (let [control (chan)]
    (go-loop []
             (when-let [e (first (alts! [in control]))]
               (log e)
               (recur)))
    control))

(def logger (log-loop (sub-events monome :press)))
(def logger (log-loop (listen-to monome)))
;; (close! logger)


;; arc test

(def arc (get-device :arc))
(pprint (:info arc))

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
