(ns monome-osc.anim
  (:require [clojure.core.async :refer [go <! timeout]])
  (:use [monome-osc.device]))

(defprotocol Connect-Animation
  (connect-animation [device]))

(extend-protocol Connect-Animation
  Monome
  (connect-animation [{:keys [size] :as monome}]
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
  Arc
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
                 (set-led arc n led 0))))

            (go
              (doseq [n (range 12)]
                (<! (timeout 100))
                (circle arc (mod n 4) 15)))])))
