(ns monome-osc.devices
  (:use [monome-osc.com]))

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

(defprotocol Device
  (listen-to [device]))

(defrecord Monome [info]
  Device
  (listen-to
    [device]
    (let [prefix (:prefix device)
          key (tag-chan (fn [[x y s]] (case s
                                       0 :release
                                       1 :press)) (listen-path (str prefix "/grid/key")))
          tilt (tag-chan (constantly :tilt) (listen-path (str prefix "/tilt")))]
      (async/merge [key tilt]))))

(defrecord Arc [info]
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

(defmulti create-device :size)
(defmethod create-device [0 0]
  [info]
  (Arc. info))
(defmethod create-device :default
  [info]
  (Monome. info))
