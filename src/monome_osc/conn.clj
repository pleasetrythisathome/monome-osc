(ns monome-osc.conn
  (:require [clojure.core.async
             :refer :all
             :exclude [map reduce into partition partition-by take merge]
             :as async])
  (:use [overtone.osc]
        [monome-osc.com]))

(defonce connection (chan))
(defonce mult-connection (mult connection))

(defn connect
  [{:keys [id port prefix] :as device}]
  (let [client (osc-client host port)
        events (listen-to device)]
    (osc-send client "/sys/port" (:server PORTS))
    (set-prefix device prefix client)
    (let [info (<!! (get-info device client))]
      (swap! devices assoc id {:info (merge device info)
                               :client client
                               :events events})
      (put! connection {:action :connect
                        :device info}))))

(defn disconnect
  [{:keys [id] :as device}]
  (let [client (get-client device)]
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
    (go
     (loop []
       (when-let [[action [id type port]] (<! serialosc)]
         (let [device {:id id
                       :type type
                       :port port
                       :prefix (str "/" id)}]
           (case action
            :add (connect device)
            :remove (disconnect device)))
         (request-serialosc "/serialosc/notify")
         (recur))))))
