(ns example.todo.ui
  (:require [com.reasonr.scriptjure :refer [js]]
            [liveview.core :as liveview]))

(defn layout [liveview body]
  [:html
   [:head
    [:title "LiveView TODO"]
    [:link {:rel "stylesheet"
            :href "/css/base.css"}]
    [:link {:rel "stylesheet"
            :href "/css/todo.css"}]
    (liveview/inject liveview)]
   [:body body]])

(defn display-item [bool]
  (if bool "list-item" "none"))

(defn display-elem [bool]
  (if bool "inline" "none"))

(defn todos-all [todos]
  (vals todos))

(defn todos-active [todos]
  (let [todos-all (todos-all todos)]
    (filter #(not (:completed %)) todos-all)))

(defn todos-completed [todos]
  (let [todos-all (todos-all todos)]
    (filter :completed todos-all)))

(defn items-left [todos]
  (let [active-count (count (todos-active todos))]
    (str (if (= 1 active-count) " item " " items ")
         "left")))

(defn todos-any? [todos]
  (pos? (count (todos-all todos))))

(defn selected-class [display-type todos-display-type]
  (if (= display-type
         todos-display-type)
    "selected" ""))

(defn todos-any-completed? [todos]
  (pos? (count (todos-completed todos))))

(defn todos-all-completed? [todos]
  (= (count (todos-all todos))
     (count (todos-completed todos))))

(defn todo-item-class [completed editing]
  (str (when completed "completed ")
       (when editing "editing")))

(defn todo-checkbox [id completed]
  [:input.toggle {:type "checkbox"
                  :checked completed
                  :onchange (js (LV.sendEvent "toggle-todo" {id (clj id)}) (return true))}])

(defn todo-display-filter [completed display-type]
  (case display-type
    :completed completed
    :active (not completed)
    true))

(defn todo [display-type {:keys [id title completed editing?]}]
  [:li {:class (todo-item-class completed editing?)
        :style {:display (display-item
                          (todo-display-filter completed display-type))}}
   [:div.view
    (todo-checkbox id completed)
    [:label {:ondblclick (js (LV.sendEvent "edit-todo" {id (clj id)}) (return true))}
     title]
    [:button.destroy {:onclick (js (LV.sendEvent "delete-todo" {id (clj id)}) (return true))}]]
   [:input.edit {:type "text"
                 :style {:display (display-elem editing?)}
                 :value title
                 :onblur (js (LV.sendEvent "edit-todo-finished"
                                         {id (clj id)
                                          title (aget event "target" "value")})
                             (return true))
                 :onkeydown (js (if (= 13 (aget event "which"))
                                  (LV.sendEvent "edit-todo-finished"
                                              {id (clj id)
                                               title (aget event "target" "value")})
                                  (if (= 27 (aget event "which"))
                                    (LV.sendEvent "edit-todo-revert"
                                                {id (clj id)})))
                                (return true))}]])

(defn app [{:keys [todos display-type]}]
  [:div
   [:section.todoapp
    [:header.header
     [:h1 "todos"]
     [:input.new-todo {:type "text"
                       :value ""
                       :placeholder "What needs to be done?"
                       :onkeydown (js (if (= 13 (aget event "which"))
                                        (LV.sendEvent "new-todo" {title (aget event "target" "value")})) (return true))}]]
    [:div {:style {:display (display-elem (todos-any? todos))}}
     [:section.main
      [:span
       (let [all-completed? (todos-all-completed? todos)]
         [:input#toggle-all.toggle-all {:type "checkbox"
                                        :checked all-completed?
                                        :onchange (js (LV.sendEvent "toggle-all" {bool (clj all-completed?)}) (return true))}])
       [:label {:for "toggle-all"} "Mark all as complete"]]
      [:ul.todo-list
       (for [v (todos-all todos)]
         (todo display-type v))]]
     [:footer.footer
      [:span.todo-count
       [:strong (count (todos-active todos))]
       (items-left todos)]
      [:ul.filters
       [:li [:a {:class (selected-class :all display-type)
                 :onclick (js (LV.sendEvent "set-display-type" {type "all"})
                              (return true))}
             "All"]]
       [:li [:a {:class (selected-class :active display-type)
                 :onclick (js (LV.sendEvent "set-display-type" {type "active"})
                              (return true))}
             "Active"]]
       [:li [:a {:class (selected-class :completed display-type)
                 :onclick (js (LV.sendEvent "set-display-type" {type "completed"})
                              (return true))}
             "Completed"]]]
      [:button.clear-completed {:onclick (js (LV.sendEvent "clear-completed" {})
                                             (return true))
                                :style {:display (display-elem (todos-any-completed? todos))}}
       "Clear completed"]]]]])
