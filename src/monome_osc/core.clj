(ns monome-osc.core
  (:require [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async]
            [clojure.pprint :refer [pprint]]
            [overtone.live :as o])
  (:use [monome-osc.utils]
        [monome-osc.com]
        [monome-osc.conn]
        [monome-osc.device]))

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
(def monome (second (get-devices)))
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

(def row-on (apply vector (repeat 8 1)))
(def row-off (apply vector (repeat 8 0)))
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

(def arc (first (get-devices)))
(log (:info arc))

(connect-animation arc)

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

(def encoders (atom []))
(defn reset-encoders! []
  (reset! encoders (into [] (repeat 4 {:step 1
                                       :scale 64
                                       :value 0}))))

;; (reset-encoders!)
(defn update-encoder [n d]
  (swap! encoders (fn [all]
                    (update-in all [n] (fn [{:keys [step value] :as enc}]
                                         (assoc enc :value (+ value (* d step))))))))
(defn mirror [k r os ns]
  (doseq [n (range 4)]
    (let [old (get os n)
          {:keys [scale value] :as new} (get ns n)
          led (-> value
                  (/ scale)
                  (* 64)
                  (mod 64)
                  (int))]
      (when-not (= old new)
        (set-all arc n 0)
        (set-led arc n led 15)))))
(add-watch encoders :mirror mirror)
;; (remove-watch encoders :mirror)

(def delta (map< second (filter< #(= :delta (first %)) (listen-to arc))))
;; (close! delta)
(go-loop []
         (when-let [[n d] (<! delta)]
           (update-encoder n d)
           (recur)))

(log @encoders)

;; overtone

(o/definst quux [freq 440]
  (* 0.3 (o/saw freq)))
(quux 600)
(o/stop)

(defn get-value [n k r os ns]
  (get-in ns [n :value]))
(defn control [synth key v]
  (o/ctl synth key v))
(add-watch encoders :control (comp (partial control quux :freq) (partial get-value 0)))
(remove-watch encoders :control)
