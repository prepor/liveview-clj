(ns example.increment
  (:require [liveview.core :as liveview]
            [integrant.core :as ig]
            [com.reasonr.scriptjure :refer [js]]))

(defn render [liveview data]
  [:html
   [:head
    [:title "Increment example"]
    (liveview/inject liveview)]
   [:body
    [:div "Number: " data]
    [:div [:button {:onclick (js (LV.sendEvent "increment" {})
                                 (return true))}
           "Increment!"]]]])

(defmethod ig/init-key ::handler [_ {:keys [liveview]}]
  (fn [req]
    (->>
     {:state (atom 0)
      :render (fn [data]
                (liveview/render (render liveview data)))
      :on-event (fn [state type payload]
                  (case type
                    "increment" (swap! state inc)))}
     (liveview/page liveview req))))
