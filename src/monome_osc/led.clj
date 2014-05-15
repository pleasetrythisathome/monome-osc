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

(defn set-map
  "state is [8][8]"
  [monome x-off y-off state]
  (apply send-to monome "/grid/led/map" x-off y-off (map row->bitmask state)))

(defn set-row
  "x-off must be a multiple of 8, state is [n][8] rows to be updated"
  [monome x-off y state]
  (apply send-to monome "/grid/led/row" x-off y (map row->bitmask state)))

(defn set-column
  "y-off must be a multiple of 8, state is [n][8] columns to be updated"
  [monome x y-off state]
  (apply send-to monome "/grid/led/col" x y-off (map row->bitmask state)))

;; brightness

(defn set-brightness-led
  [monome x y l]
  (send-to monome "/grid/led/level/set" x y l))

(defn set-brightness-all
  [monome l]
  (send-to monome "/grid/led/level/all" l))

(defn set-brightness-map
  "state is [8][8]"
  [monome x-off y-off state]
  (apply send-to monome "/grid/led/level/map" x-off y-off (map row->bitmask state)))

(defn set-brightness-row
  "x-off must be a multiple of 8, state is [n][8] rows to be updated"
  [monome x-off y state]
  (apply send-to monome "/grid/led/level/row" x-off y (map row->bitmask state)))

(defn set-brightness-col
  "y-off must be a multiple of 8, state is [n][8] columns to be updated"
  [monome x y-off state]
  (apply send-to monome "/grid/led/level/col" x y-off (map row->bitmask state)))
