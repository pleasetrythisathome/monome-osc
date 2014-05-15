(ns monome-osc.device
  (:require [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async])
  (:use [monome-osc.com]))

(defprotocol Device
  (listen-to [device]))

(defrecord Monome [info client]
  Device
  (listen-to
    [device]
    (let [prefix (:prefix device)
          key (tag-chan (fn [[x y s]] (case s
                                       0 :release
                                       1 :press)) (listen-path (str prefix "/grid/key")))
          tilt (tag-chan (constantly :tilt) (listen-path (str prefix "/tilt")))]
      (async/merge [key tilt]))))

(defrecord Arc [info client]
  Device
  (listen-to
    [device]
    (let [prefix (:prefix device)
          key (tag-chan (fn [[n s]] (case s
                                         0 :release
                                         1 :press)) (listen-path (str prefix "/enc/key")))
          enc (tag-chan (constantly :delta) (listen-path (str prefix "/enc/delta")))
          tilt (tag-chan (constantly :tilt) (listen-path (str prefix "/tilt")))]
      (async/merge [key enc tilt]))))

(defmulti create-device (fn [info client]
                          (every? zero? (:size info))))
(defmethod create-device true [info client]
  (Arc. info client))
(defmethod create-device false [info client]
  (Monome. info client))
