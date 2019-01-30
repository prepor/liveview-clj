(ns example.todo.handler
  (:require [example.todo.ui :as ui]
            [integrant.core :as ig]
            [liveview.core :as liveview]))

(defmulti event (fn [_ type _] type))

(defmethod event "new-todo" [state _ {:keys [title]}]
  (let [id (str (java.util.UUID/randomUUID))]
    (swap! state update :todos assoc id {:id id
                                         :title title
                                         :completed false})))

(defmethod event "toggle-todo" [state _ {:keys [id]}]
  (swap! state update-in [:todos id :completed] not))

(defmethod event "delete-todo" [state _ {:keys [id]}]
  (swap! state update :todos dissoc id))

(defmethod event "edit-todo" [state _ {:keys [id]}]
  (swap! state update-in [:todos id] assoc
         :editing? true
         :original (get-in @state [:todos id :title])))

(defmethod event "edit-todo-revert" [state _ {:keys [id]}]
  (swap! state update-in [:todos id] assoc
         :editing? false
         :title (get-in @state [:todos id :original])))

(defmethod event "edit-todo-finished" [state _ {:keys [id title]}]
  (swap! state update-in [:todos id] assoc
         :editing? false
         :title title))

(defmethod event "set-display-type" [state _ {:keys [type]}]
  (swap! state assoc :display-type (keyword type)))

(defmethod event "clear-completed" [state _ _]
  (swap! state assoc :todos (->> (:todos @state)
                                 (filter (fn [[k v]] (not (:completed v))))
                                 (into {}))))

(defmethod event "toggle-all" [state _ {:keys [bool]}]
  (let [todos' (->> (:todos @state)
                    (map (fn [[id todo]] [id (assoc todo :completed (not bool))]))
                    (into {}))]
    (swap! state assoc :todos todos')))

(defmethod ig/init-key :example.todo/handler [_ {:keys [liveview]}]
  (fn [req]
    (->>
     {:state (atom {:todos {}})
      :render (fn [data]
                (liveview/render (ui/layout liveview
                                            (ui/app data))))
      :on-event (fn [state type payload]
                  (event state type payload))}
     (liveview/page liveview req))))
