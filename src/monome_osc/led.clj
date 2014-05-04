(ns monome-osc.led
  (:use [monome-osc.com]))

(defn row->bitmask
  [row]
  (Integer/parseInt (apply str row) 2))

(defn set-led
  [monome x y state]
  (send-to monome "/grid/led/set" x y state))

(defn set-all
  [monome state]
  (send-to monome "/grid/led/all" state))

(defn set-quad
  "state is a [] size 8 where [[row] ...]"
  [monome x-off y-off state]
  (send-to monome "/grid/led/map" x-off y-off (map row->bitmask state)))

(defn set-row
  "x-off must be a multiple of 8"
  [monome x-off y state]
  (send-to monome "/grid/led/row" x-off y (row->bitmask state)))

(defn set-column
  "x-off must be a multiple of 8"
  [monome x y-off state]
  (send-to monome "/grid/led/col" x y-off (row->bitmask state)))

;; brightness

(defn set-brightness
  [monome x y l]
  (send-to monome "/grid/led/level/set" x y l))

(defn set-brightness-all
  [monome l]
  (send-to monome "/grid/led/level/all" l))

(defn set-brightness-map
  [monome x-off y-off l]
  (send-to monome "/grid/led/level/map" x-off y-off l))

(defn set-brightness-row
  [monome x-off y l]
  (send-to monome "/grid/led/level/row" x-off y l))

(defn set-brightness-col
  [monome x y-off l]
  (send-to monome "/grid/led/level/col" x y-off l))
