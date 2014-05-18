(ns monome-osc.core
  (:require [monome-osc com conn device])
  (:use [overtone.helpers.ns]))

(immigrate
 'monome-osc.com
 'monome-osc.conn
 'monome-osc.device)

(monitor-devices)
