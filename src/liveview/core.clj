(ns liveview.core
  (:require [cheshire.core :as json]
            [clojure.core.async :as a]
            [clojure.java.io :as io]
            [clojure.tools.logging :as logger]
            [hiccup2.core :as hiccup])
  (:import [java.util Timer TimerTask]))

(defn start [opts]
  {:timer (Timer.)
   :opts opts
   :instances (atom {})})

(defn stop [{:keys [timer]}]
  (.cancel timer))

(defn new-id [] (str (java.util.UUID/randomUUID)))

(def ^:dynamic *id*)

(defn js-runtime []
  (slurp (io/resource "liveview/runtime.js")))

(defn js-init [id ws-url]
  (str "var LV = LiveView(\"" ws-url "?" id "\")"))

(defn inject [liveview]
  (list [:script {:src "https://cdn.jsdelivr.net/npm/morphdom@2.3.3/dist/morphdom-umd.min.js"}]
        [:script (hiccup/raw (js-runtime))]
        [:script (hiccup/raw (js-init *id*
                                      (get-in liveview [:opts :ws-url])))]))

(defn make-instance [state {:keys [render on-event] :as opts
                            :or {on-event (fn [_ _]
                                            (logger/warn "Undefined event handler"))}}]
  (let [out (a/chan 100)
        in (a/chan 100)
        id (new-id)
        instance {:id id
                  :state state
                  :in in
                  :out out
                  :opts opts
                  :mounted? (atom false)}]
    (add-watch state [::watcher id]
               (fn [_ _ _ state']
                 (a/>!! out (json/generate-string
                             {:type "rerender"
                              :value (binding [*id* id]
                                       (render state'))}))))
    (a/go-loop []
      (if-let [v (a/<! in)]
        (do
          (logger/debug "Event" {:instance id
                                 :event v})
          (case (:type v)
            "event" (on-event state (:event v) (:payload v))
            (logger/warn "Unknown instance event" v))
          (recur))
        (remove-watch state [::watcher id])))
    instance))

(defn stop-instance [{:keys [in out]}]
  (a/close! out)
  (a/close! in))

(defn expire-task [instances id]
  (proxy [TimerTask] []
    (run []
      (swap! instances (fn [instances']
                         (if-let [{:keys [mounted?] :as instance}
                                  (get instances' id)]
                           (if @mounted?
                             instances'
                             (do (stop-instance instance)
                                 (dissoc instances' id)))
                           instances'))))))

(defn register-instance [{:keys [instances timer opts]}
                         {:keys [id] :as instance}]
  (swap! instances assoc id instance)
  (.schedule timer (expire-task instances id) (get opts :mount-wait 5000)))

(defn deregister-instance [{:keys [instances]} id]
  (swap! instances dissoc id))

(defn handler [liveview {:keys [init render on-mount]
                         :or {init (fn [] (atom nil))}
                         :as opts}]
  (fn [req]
    (let [instance (make-instance (init req) opts)]
      (register-instance liveview instance)
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (binding [*id* (:id instance)]
               (render @(:state instance)))})))
