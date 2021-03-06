(ns monome-osc.conn
  (:require [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async])
  (:use [overtone.osc]
        [monome-osc.com]
        [monome-osc.device]
        [monome-osc.events]))

(defonce connection (chan))
(defonce mult-connection (mult connection))

(defonce devices (atom {}))

(defn get-devices
  []
  (vals @devices))

(defn get-device
  ([type] (get-device type 0))
  ([type n]
     (let [device-class (case type
                          :monome monome_osc.device.Monome
                          :arc monome_osc.device.Arc)
           of-class (filter #(= (class %) device-class)(get-devices))]
       (when (seq of-class)
         (nth of-class n nil)))))

(defn connect
  [{:keys [id port prefix] :as raw}]
  (let [client (osc-client host port)]
    (osc-send client "/sys/port" (:server PORTS))

    (let [info (<!! (get-info client))
          device (create-device (merge raw info) client)]
      (set-prefix device prefix)
      (connect-animation device)
      (swap! devices assoc id device)
      (reset-events! device)
      (put! connection {:action :connect
                        :device device}))))

(defn disconnect
  [id]
  (let [{:keys [client info] :as device} (get @devices id)]
    (osc-close client)
    (swap! devices #(dissoc % id))
    (put! connection {:action :disconnect
                      :device device})))

(defn watch-devices []
  (let [out (chan)]
    (tap mult-connection out)
    out))

(defn request-serialosc [path]
  (osc-send to-serialosc path host (:server PORTS)))

(defn monitor-devices
  []
  (let [paths [[:add "/serialosc/device"]
               [:add "/serialosc/add"]
               [:remove "/serialosc/remove"]]
        serialosc (async/merge (map (fn [[tag path]]
                                      (tag-chan (constantly tag) (listen-path path)))
                                    paths))]
    (reset! devices {})
    (request-serialosc "/serialosc/list")
    (go-loop []
             (when-let [[action [id type port]] (<! serialosc)]
               (let [device {:id id
                             :type type
                             :port port
                             :prefix (str "/" id)}]
                 (case action
                   :add (connect device)
                   :remove (disconnect id)))
               (request-serialosc "/serialosc/notify")
               (recur)))))
