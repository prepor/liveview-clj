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
  (doseq [i (vals @instances)]
    ((:stop i)))
  (.cancel timer))

(defn new-id [] (str (java.util.UUID/randomUUID)))

(def ^:dynamic *id*)

(defn js-runtime []
  (slurp (io/resource "liveview/runtime.js")))

(defn morphdom-runtime []
  (slurp (io/resource "liveview/morphdom.js")))

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

(defn expire-task [clb]
  (proxy [TimerTask] []
    (run [] (clb))))

(defn register-instance [{:keys [instances timer]}
                         {:keys [id] :as instance}]
  (swap! instances assoc id instance))

(defn deregister-instance [{:keys [instances]} id]
  (swap! instances dissoc id))

(defn set-expire-task [{:keys [timer]} clb timeout]
  (let [task (expire-task clb)]
    (.schedule timer task timeout)
    task))

(defn start-instance [liveview
                      {:keys [render on-event on-mount on-disconnect
                              mount-timeout]
                       external-state :state
                       :as opts
                       :or {on-event (fn [_ _]
                                       (logger/warn "Undefined event handler"))
                            external-state (atom nil)
                            mount-timeout 5000}}]
  (let [id (new-id)
        prev-external-state (atom @external-state)
        rerender (fn [sink state']
                   (when (not= @prev-external-state state')
                     (reset! prev-external-state state')
                     (a/>!! sink (json/generate-string
                                  {:type "rerender"
                                   :value (binding [*id* id]
                                            (render state'))}))))
        state (atom nil)
        instance {:id id
                  :on-mount (fn [socket] ((:on-mount @state) socket))
                  :stop (fn [] ((:stop @state)))}]
    (register-instance liveview instance)
    (letfn [(initialized []
              (let [expire-task (set-expire-task liveview disconnected mount-timeout)]
                {:name :initialized
                 :on-mount (fn [socket]
                             (.cancel expire-task)
                             (when on-mount (on-mount socket))
                             (reset! state (mounted socket)))
                 :stop (fn [] (.cancel expire-task))}))
            (mounted [{:keys [sink source]}]
              (logger/debug "Mounted" {:instance id})
              (rerender sink @external-state)
              (add-watch external-state [::watcher id]
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
                                "event" (on-event external-state
                                                  (:event v') (:payload v'))
                                (logger/warn "Unknown event type" {:instance id
                                                                   :event v'})))
                            (catch Exception e
                              (logger/error e "Error during event handling" {:instance id
                                                                             :event v})))
                          (recur))
                        (do
                          (remove-watch external-state [::watcher id])
                          (reset! state (dismounted)))))]
                {:name :mounted
                 :on-mount (fn [_] (throw (Exception. "Double mount. (Race condition?)")))
                 :stop (fn []
                         (remove-watch external-state [::watcher id])
                         (a/close! sink)
                         (a/close! source)
                         (a/<!! worker))}))
            (dismounted []
              (logger/debug "Dismounted" {:instance id})
              (let [expire-task (set-expire-task liveview disconnected mount-timeout)]
                {:name :dismounted
                 :on-mount (fn [socket]
                             (.cancel expire-task)
                             (reset! state (mounted socket)))
                 :stop (fn [] (.cancel expire-task))}))
            (disconnected []
              (logger/debug "Disconnected" {:instance id})
              (when on-disconnect (on-disconnect))
              (deregister-instance liveview id))]
      (reset! state (initialized)))
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
