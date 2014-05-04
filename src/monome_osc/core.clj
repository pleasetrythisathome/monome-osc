(ns monome-osc.core
  (:refer-clojure :exclude [map reduce into partition partition-by take merge])
  (:require [clojure.core.async :refer :all :as async]
            [clojure.pprint :refer [pprint]])
  (:use [overtone.osc]))

;; utils

(def log-chan (chan))

(thread
 (loop []
   (when-let [v (<!! log-chan)]
     (pprint v)
     (recur)))
 (println "Log Closed"))

;; (close! log-chan)

(defn log [& msgs]
  (doseq [msg msgs]
    (>!! log-chan (or msg "**nil**"))))

;; communication

(defonce PORTS {:serialosc 12002
                :server 12001})
(defonce host "localhost")

(defonce server (osc-server (:server PORTS)))
(defonce to-serialosc (osc-client host (:serialosc PORTS)))

(defonce responses (chan (sliding-buffer 1)))
(osc-listen server #(put! responses %) :listen)

(defonce broadcaster (pub responses :path))

(defn listen-path
  [path]
  (let [out (chan)]
    (sub broadcaster path out)
    (async/map< :args out)))

(defn tag-chan
  [tag in]
  (async/map< (partial vector tag) in))


;; devices

(defonce devices (atom {}))

(defn get-devices
  []
  (clojure.core/map :device @devices))

(defn get-client
  [{:keys [id] :as device}]
  (get-in @devices [id :client]))

(defn send-to [{:keys [prefix] :as device} path & args]
  (let [client (get-client device)]
    (apply (partial osc-send client (str prefix path)) args)))

(defn set-prefix
  [device prefix]
  (let [client (get-client device)]
    (osc-send client "/sys/prefix" prefix)))

;; connection

(defn listen-to
  [monome]
  (let [prefix (:prefix monome)
        button (tag-chan :button (listen-path (str prefix "/grid/key")))
        tilt (tag-chan :tilt (listen-path (str prefix "/tilt")))]
    (async/merge [button tilt])))


(defn connect
  [{:keys [id port prefix] :as device}]
  (let [client (osc-client host port)
        events (listen-to device)]
    (log :connect device)
    (swap! devices #(assoc % id {:device device
                                 :client client
                                 :events events}))
    (osc-send client "/sys/port" (:server PORTS))
    (set-prefix device prefix)))

(defn disconnect
  [{:keys [id] :as device}]
  (let [client (get-client device)]
    (osc-close client)
    (swap! devices #(dissoc % id))))


(defn request-info [path]
  (osc-send to-serialosc path host (:server PORTS)))

(defn monitor-devices
  []
  (let [handlers [[:add "/serialosc/device"]
                  [:add "/serialosc/add"]
                  [:remove "/serialosc/remove"]]
        connection (async/merge (clojure.core/map (fn [[tag path]]
                                                    (tag-chan tag (listen-path path))) handlers))]
    (reset! devices {})
    (request-info "/serialosc/list")
    (go
     (loop []
       (when-let [[action [id type port]] (<! connection)]
         (let [device {:id (keyword id)
                       :type type
                       :port port
                       :prefix (str "/" id)}]
           (case action
            :add (connect device)
            :remove (disconnect device)))
         (request-info "/serialosc/notify")
         (recur))))))

(defn watch-devices []
  (let [out (chan)]
    (add-watch devices :change (fn [key ref old new] (when-not (= old new)
                                                      (put! out new))))
    out))

;; led

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

(defn connect-animation
  [monome]
  (let [row-on (apply vector (repeat 10 1))
        row-off (apply vector (repeat 10 0))]
    (go
     (loop [col 0]
       (set-column monome col 0 row-on)
       (<! (timeout 25))
       (set-column monome col 0 row-off)
       (when (< col 18)
         (recur (inc col)))))))

(defn make-cell [monome x y]
  (let [stagger 200
        length 2]
    (go
     (<! (timeout (rand-int stagger)))
     (loop []
       (set-led monome x y 1)
       (<! (timeout (max 150 (rand-int stagger))))
       (set-led monome x y 0)
       (when-not (zero? (rand-int length))
         (recur))))))

(defn make-scene [monome rows cols]
  (dotimes [x cols]
    (dotimes [y rows]
      (make-cell monome x y))))

;; (osc-debug true)

;; tests

;; (osc-listen server log :debug)
;; (osc-rm-listener server :debug)

;; (take! responses log)

;; (monitor-devices)
;; (def monome (first (get-devices)))
;; (log monome)
;; (set-all monome 1)
;; (set-all monome 0)


;; (set-led monome 0 0 1)
;; (set-led monome 0 0 0)

;; (def row-on (apply vector (repeat 10 1)))
;; (def row-off (apply vector (repeat 10 0)))
;; (set-row monome 0 0 row-on)
;; (set-row monome 0 0 row-off)

;; (set-column monome 0 0 row-on)
;; (set-column monome 0 0 row-off)

;; (connect-animation monome)

#_(defn handle-event
    [[action args]]
    (print action args)
  (case action
    :button (pprint args)
    :tilt nil))

#_(let [events (monome-listen monome)]
    (go
     (while true
       (let [event (<! events)]
         (handle-event event)))))

;; monitor devices

#_(go
   (while true
     (let [devices (<! connected-devices)]
       (if-let [monome (first (vals devices))]
         (print :disconnected)))))
