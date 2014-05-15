(ns monome-osc.com
  (:refer-clojure :exclude [map reduce into partition partition-by take merge])
  (:require [clojure.core.async :refer :all :as async])
  (:use [overtone.osc]))

(defonce PORTS {:serialosc 12002
                :server 12001})
(defonce host "localhost")

(defonce server (osc-server (:server PORTS)))
(defonce to-serialosc (osc-client host (:serialosc PORTS)))

(defonce responses (chan (sliding-buffer 1)))
(defonce pub-responses (pub responses :path))
(osc-listen server #(put! responses %) :listen)

(defn listen-path
  [path]
  (let [out (chan (sliding-buffer 1))]
    (sub pub-responses path out)
    (async/map< :args out)))

(defn tag-chan
  [tagfn in]
  (async/map< (juxt tagfn identity) in))

(defn listen-all
  "listens and merges a collections of vectors [tag path]"
  [paths]
  (async/merge (clojure.core/map (fn [[tag path]]
                                   (tag-chan (constantly tag) (listen-path path)))
                                 paths)))

(defn get-info
  ([device] (get-info device (get-client device)))
  ([device client]
     (let [out (chan)
           paths [[:port "/sys/port"]
                  [:host "/sys/host"]
                  [:id "/sys/id"]
                  [:prefix "/sys/prefix"]
                  [:rotation "/sys/rotation"]
                  [:size "/sys/size"]]
           listeners (clojure.core/map (fn [[tag path]]
                                         (tag-chan (constantly tag) (listen-path path)))
                                       paths)
           fail (timeout 1000)]
       (go
        (osc-send client "/sys/info")
        (loop [info {} failed false]
          (if (or failed (every? #(contains? info %) (clojure.core/map first paths)))
            (do
              (clojure.core/map close! listeners)
              (put! out info))
            (if-let [[[tag v] c] (alts! (conj listeners fail))]
              (let [parsed (if (= 1 (count v))
                             (first v)
                             (into-array v))]
                (recur (assoc info tag parsed) false))
              (recur info true)))))
       out)))
