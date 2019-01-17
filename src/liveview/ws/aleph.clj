(ns liveview.ws.aleph
  (:require [manifold.stream :as s]
            [aleph.http :as http]
            [clojure.core.async :as a]
            [cheshire.core :as json]
            [liveview.core :as liveview]))

(defn handler [liveview]
  (fn [req]
    (let [s @(http/websocket-connection req)
          id (:query-string req)]
      (if-let [instance (get @(:instances liveview) id)]
        (do
          (update instance :mounted? reset! true)
          (a/go-loop []
            (when-let [msg (a/<! (:out instance))]
              (s/put! s msg)
              (recur)))
          (s/consume (fn [data]
                       (a/>!! (:in instance) (json/parse-string data true))) s)
          (s/on-closed s (fn []
                           (liveview/deregister-instance liveview id)
                           (liveview/stop-instance instance))))
        (s/close! s))
      nil)))
