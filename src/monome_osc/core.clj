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

;; devices

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

;; events

(defn listen-to
  [monome]
  (let [prefix (:prefix monome)
        button (tag-chan (fn [[x y s]] (case s
                                        0 :release
                                        1 :press)) (listen-path (str prefix "/grid/key")))
        tilt (tag-chan (constantly :tilt) (listen-path (str prefix "/tilt")))]
    (async/merge [button tilt])))

;; connection

(defonce connection (chan))
(defonce mult-connection (mult connection))

(defn connect
  [{:keys [id port prefix] :as device}]
  (let [client (osc-client host port)
        events (listen-to device)]
    (osc-send client "/sys/port" (:server PORTS))
    (set-prefix device prefix client)
    (let [info (<!! (get-info device client))]
      (swap! devices assoc id {:info info
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
        serialosc (async/merge (clojure.core/map (fn [[tag path]]
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

;; (monitor-devices)

;; (def c (watch-devices))
#_(go-loop []
         (when-let [v (<! c)]
           (log v)
           (recur)))
;; (close! c)

;; (def monome (first (get-devices)))
;; (log monome)

;; (take! (get-info monome) log)

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
    :press (log :press args)
    :release (log :release args)
    :tilt nil))

;; (def events (listen-to monome))
#_(go-loop []
           (when-let [event (<! events)]
             (handle-event event)
             (recur)))
;; (close! events)
