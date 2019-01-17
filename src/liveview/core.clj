(ns liveview.core
  (:require [cheshire.core :as json]
            [clojure.core.async :as a]
            [clojure.tools.logging :as logger])
  (:import [java.util Timer TimerTask]))

(comment
  render data => dom
  => send dom
  render data' => dom'
  => send dom dom'
  ;; questions:
  ;; - disconnection logic?
  ;; - reconnection logic?
  ;; - form validation. replace users data?
  ;; - graceful shutdown? send state to a clients before shutdown? versioning? spec integrations? migrations?
  ;; - server crash recovery?


  ;; additional library actions + helpers on socket:
  ;; - put-flash
  ;; - redirect

  ;; (reconnect? socket)

  (liveview (fn [req socket]
              {:state (atom {})
               :handler (fn [event])
               :render (fn [data])
               :on-disconnect (fn [])})))

(defn start [opts]
  (prn "---START" opts)
  {:timer (Timer.)
   :opts opts
   :instances (atom {})})

(defn stop [{:keys [timer]}]
  (.cancel timer))

(defn new-id [] (str (java.util.UUID/randomUUID)))

(def ^:dynamic *id*)

(defn js [{:keys [id ws-url]}]
  (str "
var LiveView = {
  id: \"" id "\",
  socket: new WebSocket(\"" ws-url "?" id"\"),
  sendEvent: function(type, event) {
 if (this.socket.readyState != 1) {
      console.log(\"Can't send an event, socket is not connected\")
    } else {
      this.socket.send(JSON.stringify({type: \"event\", event: type, payload: event}));
    }
  }
};
LiveView.socket.onmessage = function(e) {
  var data = JSON.parse(e.data);
  if (data.type == \"rerender\"){
    morphdom(document.documentElement, data.value);
  }
};"))

(defn inject [liveview]
  (list [:script {:src "https://cdn.jsdelivr.net/npm/morphdom@2.3.3/dist/morphdom-umd.min.js"}]
        [:script (js {:id *id*
                      :ws-url (get-in liveview [:opts :ws-url])})]))

(defn msg-clb [type]
  (str "LiveView.sendEvent(\"" type "\", event); return true"))


(defn make-instance [state {:keys [render on-event] :as opts
                            :or {on-event (fn [_ _]
                                            (logger/warn "Undefined event handler"))}}]
  (let [out (a/chan 100)
        in (a/chan 100)
        instance {:id (new-id)
                  :state state
                  :in in
                  :out out
                  :opts opts
                  :mounted? (atom false)}]
    (add-watch state ::watcher
               (fn [_ _ _ state']
                 (a/>!! out (json/generate-string
                             {:type "rerender"
                              :value (render state')}))))
    (a/go-loop []
      (when-let [v (a/<! in)]
        (case (:type v)
          "event" (on-event state (:payload v))
          (logger/warn "Unknown instance event" v))
        (recur)))
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
       :headers {:content-type "text/html"}
       :body (binding [*id* (:id instance)]
               (render @(:state instance)))})))
