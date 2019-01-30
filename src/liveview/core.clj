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

(defn stop [{:keys [timer instances]}]
  (.cancel timer)
  (doseq [i (vals @instances)]
    ((:stop i))))

(defn new-id [] (str (java.util.UUID/randomUUID)))

(def ^:dynamic *id*)

(defn js-runtime []
  (slurp (io/resource "liveview/runtime.js")))

(defn js-init [id {:keys [ws-url ws-path]}]
  (if ws-url
    (str "var LV = LiveView(\"" ws-url "?" id "\")")
    ;; FIXME schema
    (str "var LV = LiveView(\"ws://\" + location.host + \"" ws-path "?" id "\")")))

(defn inject [liveview]
  (list [:script {:src "https://cdn.jsdelivr.net/npm/morphdom@2.3.3/dist/morphdom-umd.min.js"}]
        [:script (hiccup/raw (js-runtime))]
        [:script (hiccup/raw (js-init *id*
                                      (:opts liveview)))]))

(defn expire-task [instances id]
  (proxy [TimerTask] []
    (run []
      (swap! instances (fn [instances']
                         (if-let [{:keys [mounted?] :as instance}
                                  (get instances' id)]
                           (if @mounted?
                             instances'
                             (dissoc instances' id))
                           instances'))))))

(defn register-instance [{:keys [instances timer opts]}
                         {:keys [id] :as instance}]
  (swap! instances assoc id instance)
  (.schedule timer (expire-task instances id) (get opts :mount-wait 5000)))

(defn deregister-instance [{:keys [instances]} id]
  (swap! instances dissoc id))

(defn start-instance [liveview
                      {:keys [state render on-event on-mount on-disconnect]
                       :as opts
                       :or {on-event (fn [_ _]
                                       (logger/warn "Undefined event handler"))
                            state (atom nil)}}]
  (let [id (new-id)
        prev-state (atom @state)
        rerender (fn [sink state']
                   (when (not= @prev-state state')
                     (reset! prev-state state')
                     (a/>!! sink (json/generate-string
                                  {:type "rerender"
                                   :value (binding [*id* id]
                                            (render state'))}))))
        mounted? (atom false)
        stop (atom (fn []))
        on-mount'
        (fn [{:keys [sink source]}]
          (logger/debug "Mount" {:instance id})
          (reset! mounted? true)
          (when on-mount (on-mount source sink))
          (rerender sink @state)
          (add-watch state [::watcher id]
                     (fn [_ _ _ state']
                       (rerender sink state')))
          (let [worker
                (a/go-loop []
                  (if-let [v (a/<! source)]
                    (do
                      (try
                        (let [v' (json/parse-string v true)]
                          (logger/debug "Event" {:instance id
                                                 :event v'})
                          (case (:type v')
                            "event" (on-event state (:event v') (:payload v'))
                            (logger/warn "Unknown event type" {:instance id
                                                               :event v'})))
                        (catch Exception e
                          (logger/error e "Error during event handling" {:instance id
                                                                         :event v})))
                      (recur))
                    (do
                      (logger/debug "Disconnect" {:instance id})
                      (reset! mounted? false)
                      (remove-watch state [::watcher id])
                      (when on-disconnect (on-disconnect))
                      (deregister-instance liveview id))))]
            (reset! stop (fn []
                           (a/close! sink)
                           (a/close! source)
                           (a/<!! worker)))))
        instance {:id id
                  :state state
                  :opts opts
                  :on-mount on-mount'
                  :mounted? mounted?
                  :stop (fn [] (@stop))}]
    (register-instance liveview instance)
    (binding [*id* id]
      (render @state))))

(defn render [dom]
  (str (hiccup/html dom)))

(defn page [liveview req opts]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (start-instance liveview opts)})

(defn ws-handler [liveview adapter]
  (fn [req]
    (let [socket (adapter req {})
          id (:query-string req)]
      (if-let [instance (get @(:instances liveview) id)]
        ((:on-mount instance) socket)
        (do (logger/warn "Unknown instance" {:id id})
            (a/close! (:sink socket))) )
      nil)))
