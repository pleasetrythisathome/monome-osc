(ns monome-osc.core
  (:refer-clojure :exclude [map reduce into partition partition-by take merge])
  (:require [clojure.core.async :refer :all :as async]
            [clojure.pprint :refer [pprint]])
  (:use [overtone.osc]
        [monome-osc.utils]
        [monome-osc.com]
        [monome-osc.conn]
        [monome-osc.led]))

(def watcher (watch-devices))
(go-loop []
         (when-let [{:keys [action device]} (<! watcher)]
           (case action
             :connect (connect-animation device)
             :disconnect nil)
           (recur)))

;; (close! watcher)

;; (osc-debug true)

;; tests

;; (osc-listen server log :debug)
;; (osc-rm-listener server :debug)

;; (monitor-devices)

;; (def monome (first (get-devices)))
;; (log monome)


;; (take! (get-info monome) log)

;; (set-all monome 1)
;; (set-all monome 0)


;; (set-led monome 0 0 1)
;; (set-led monome 0 0 0)

;; (def row-on (apply vector (repeat 10 1)))
;; (def row-off (apply vector (repeat 10 0)))
;; (set-row monome 0 0 row-on)
;; (set-row monome 0 0 row-off)

;; (set-column monome 0 0 row-on)
;; (set-column monome 0 0 row-off)

;; (connect-animation monome)

#_(defn handle-event
    [[action args]]
    (print action args)
  (case action
    :press (log :press args)
    :release (log :release args)
    :tilt nil))

;; (def events (listen-to monome))
#_(go-loop []
           (when-let [event (<! events)]
             (handle-event event)
             (recur)))
;; (close! events)
