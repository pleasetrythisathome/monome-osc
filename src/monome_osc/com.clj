(ns monome-osc.com
  (:refer-clojure :exclude [map reduce into partition partition-by take merge])
  (:require [clojure.core.async :refer :all :as async]
            [clojure.pprint :refer [pprint]])
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

(defonce devices (atom {}))

(defn get-devices
  []
  (clojure.core/map :info (vals @devices)))

(defn get-client
  [{:keys [id] :as device}]
  (get-in @devices [id :client]))

(defn send-to [{:keys [prefix] :as device} path & args]
  (let [client (get-client device)]
    (apply (partial osc-send client (str prefix path)) args)))

(defn set-prefix
  ([device prefix] (set-prefix device prefix (get-client device)))
  ([device prefix client]
     (osc-send client "/sys/prefix" prefix)))

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

(defn listen-to
  [monome]
  (let [prefix (:prefix monome)
        button (tag-chan (fn [[x y s]] (case s
                                        0 :release
                                        1 :press)) (listen-path (str prefix "/grid/key")))
        tilt (tag-chan (constantly :tilt) (listen-path (str prefix "/tilt")))]
    (async/merge [button tilt])))
