(ns monome-osc.anim
  (:require [clojure.core.async :refer [go <! timeout]])
  (:use [monome-osc.led]))

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
