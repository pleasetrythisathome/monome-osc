(ns monome-osc.com
  (:require [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async])
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
  (async/merge (map (fn [[tag path]]
                      (tag-chan (constantly tag) (listen-path path)))
                    paths)))

(defn send-to [{client :client {prefix :prefix} :info} path & args]
  (apply (partial osc-send client (str prefix path)) args))

(defn set-prefix
  ([{:keys [client] :as device} prefix]
     (osc-send client "/sys/prefix" prefix)))

(defn set-rotation
  ([{:keys [client] :as device} degrees]
     (osc-send client "/sys/rotation" degrees)))

(defn get-info
  [client]
  (let [out (chan)
        paths [[:port "/sys/port"]
               [:host "/sys/host"]
               [:id "/sys/id"]
               [:prefix "/sys/prefix"]
               [:rotation "/sys/rotation"]
               [:size "/sys/size"]]
        listeners (map (fn [[tag path]]
                         (tag-chan (constantly tag) (listen-path path)))
                       paths)
        fail (timeout 1000)]
    (go
     (osc-send client "/sys/info")
     (loop [info {} failed false]
       (if (or failed (every? #(contains? info %) (map first paths)))
         (do
           (map close! listeners)
           (put! out info))
         (if-let [[[tag v] c] (alts! (conj listeners fail))]
           (let [parsed (if (= 1 (count v))
                          (first v)
                          (into-array v))]
             (recur (assoc info tag parsed) false))
           (recur info true)))))
    out))
