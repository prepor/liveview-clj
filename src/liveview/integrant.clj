(ns liveview.integrant
  (:require [integrant.core :as ig]
            [liveview.core :as liveview]))

(defmethod ig/init-key ::liveview [_ opts]
  (liveview/start opts))

(defmethod ig/halt-key! ::liveview [_ liveview]
  (liveview/stop liveview))

(defmethod ig/init-key ::ws [_ {:keys [liveview adapter]}]
  (liveview/ws-handler liveview adapter))
