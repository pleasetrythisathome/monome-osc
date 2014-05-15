(ns monome-osc.arc
  (:use [monome-osc.com]))

(defn set-led
  [arc n x l]
  (send-to arc "/ring/set" n x l))

(defn set-all
  [arc n l]
  (send-to arc "/ring/all" n l))

(defn set-map
  "l is a list of 64 integers (< 0 x 16)"
  [arc n l]
  (apply send-to arc "/ring/map" n l))

(defn set-range
  [arc n x1 x2 l]
  (send-to arc "/ring/range" n x1 x2 l))
