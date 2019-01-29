(ns liveview.ws.aleph
  (:require [manifold.stream :as s]
            [aleph.http :as http]
            [clojure.core.async :as a]
            [cheshire.core :as json]
            [liveview.core :as liveview]))

(defn adapter [req {:keys [sink-buf-or-n source-buf-or-n]}]
  (let [socket @(http/websocket-connection req)
        sink (a/chan sink-buf-or-n)
        source (a/chan source-buf-or-n)]
    (s/connect socket source)
    (s/connect sink socket)
    {:sink sink
     :source source}))
