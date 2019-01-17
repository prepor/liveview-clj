(ns example.core
  (:require [aleph.http :as http]
            [ataraxy.core :as ataraxy]
            [hiccup.core :as hiccup]
            [integrant.core :as ig]
            [liveview.core :as liveview]
            [liveview.ws.aleph :as liveview-ws]
            [ring.middleware.resource :refer [wrap-resource]]))

(defn layout [liveview body]
  [:html
   [:head
    [:title "Hello world"]
    [:link {:rel "stylesheet"
            :href "/css/base.css"}]
    [:link {:rel "stylesheet"
            :href "/css/todo.css"}]
    (liveview/inject liveview)]
   [:body body]])

(defmethod ig/init-key ::handler [_ {:keys [liveview]}]
  (liveview/handler
   liveview
   {:init (fn [req]
            (atom 0))
    :render (fn [state]
              (hiccup/html (layout liveview
                                   [:div
                                    [:span state]
                                    [:button {:onclick (liveview/msg-clb "inc")} "Increment!"]])))
    :on-event (fn [state event]
                (swap! state inc))
    :on-mount (fn [instance])}))

(defmethod ig/init-key ::router [_ opts]
  (ataraxy/handler opts))

(defmethod ig/init-key ::server [_ {:keys [handler options]}]
  (http/start-server (-> handler
                         (wrap-resource "/example/public"))
                     options))

(defmethod ig/halt-key! ::server [_ server]
  (.close server))

(defmethod ig/init-key ::liveview [_ opts]
  (liveview/start opts))

(defmethod ig/init-key ::liveview-ws [_ {:keys [liveview]}]
  (liveview-ws/handler liveview))

(defmethod ig/halt-key! ::liveview [_ liveview]
  (liveview/stop liveview))

(def config
  {::handler {:liveview (ig/ref ::liveview)}
   ::router {:routes {"/" [:index]
                      "/ws" [:liveview]}
             :handlers {:index (ig/ref ::handler)
                        :liveview (ig/ref ::liveview-ws)}}
   ::server {:handler (ig/ref ::router)
             :options {:port 8000}}
   ::liveview {:ws-url "ws://localhost:8000/ws"}
   ::liveview-ws {:liveview (ig/ref ::liveview)}})

(comment
  (require 'integrant.repl)
  (integrant.repl/set-prep! (constantly config))
  )
