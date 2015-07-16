(ns memory-cljs.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as str]
            [cljs.core.async :refer [put! chan <!]]
            [clojure.browser.repl :as repl]
            [cemerick.url :refer [url-encode]]
            [memory-cljs.insta :as insta :refer [login-component]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:import [goog.net Jsonp]
           [goog Uri]))

;CLIENT ID	dea1d49dd5684444a44da24d64c88206
;CLIENT SECRET	cdb4a7da55394e66b1c12c45da42ff59
;WEBSITE URL	http://plance.nl/memory-cljs/
;REDIRECT URI	http://plance.nl/memory-cljs#login

(enable-console-print!)

(def img_base_url "/")

(def labels
    ["aap","nootjes","mies","wim","vuur","zus","jet","teun","https://igcdn-photos-b-a.akamaihd.net/hphotos-ak-xap1/t51.2885-15/10311171_351287585041737_1346783369_n.jpg"])

(defn log [s]
  (.log js/console s))


(defn add-card
   "create card element based on a label"
    [v label]
       (conj v {:id (count v) :label label :hidden true :found false  }))


(defn create-all-cards
   "create set of cards based on the set of labels"
    [labels]
    (reduce add-card []
         (shuffle
           (concat labels labels))))

(defn create-board
   "initialize app state"
    []
  {:cards (create-all-cards labels)
   :turns   0
   :session {}})

(def board
  (atom (create-board)))

(defn reset-board
   [labels]
   (swap! board
       #(assoc % :cards (create-all-cards labels)
                 :turns 0)))

(defn get-thumbnail
  "fetches thumbnail from pic map"
  [v pic]
  (let [{{{url :url} :thumbnail} :images} pic]
    (conj v url)))

(defn fetch-from-instafeed
  "haal plaatjes van instagram self feed"
  [token]
  (go
    (let [obj   (<! (insta/jsonp (str insta/insta-most-recent-self-url token)))
          picas (js->clj (-> obj .-data) :keywordize-keys true) ]
      (reset-board
        (reduce get-thumbnail [] picas)))))

(defn click []
(let [sound  (buzz.sound. "audio/click_low.mp3")]
     (.play sound)))

(defn harp []
(let [sound  (buzz.sound. "audio/harp.mp3")]
     (.play sound)))

(defn wrong []
(let [sound  (buzz.sound. "audio/wrong.mp3")]
     (.play sound)))


(defn log-card
   "log one card"
   [card]
   (doseq [[k v] card]
         (.log js/console (str k ": " v ))))

(defn turned-cards
   "return all turned cards"
   [cards]
      (filterv #(false? (:hidden %)) cards))

(defn found-cards
   "return all found cards"
    [cards]
       (filterv #(true? (:found %)) cards))

(defn amount-turned
   "return amount of turned cards"
   [cards]
      (count (turned-cards cards)))

(defn amount-found
   "return amount of found cards"
   [cards]
      (count (found-cards cards)))

(defn is-img
   "determines if a card is an image"
   [{label :label}]
     (=
       (.indexOf label "http")
       0))

(defn amount-remaining-pairs
   "return the amount of pairs that need to be found"
   [cards]
      (/
        (- (count cards) (amount-found cards))
          2))

(defn eliminate-equals
   "find two turned cards with equal labels and mark as found"
   [cards]
   (if (= (amount-turned cards) 2)
      (let [turned-cards (turned-cards cards)
            label1       ((first turned-cards) :label)
            label2       ((second turned-cards) :label)]
             (if (= label1 label2)
                 (do
                     (harp)
                     (mapv
                     #(if (= label1 (:label %)) (assoc % :found true :hidden true) %)
                     cards)
                  )
              (do
                (wrong)
                cards
               )))
       (do
         (click)
         cards)
        ))

(defn handle-doubles
   "find two turned cards and hide them"
   [cards]
   (let [amount (amount-turned cards)]
      (if (= amount 2)
          (map #(assoc % :hidden true) cards)
           cards)))

(defn toggle
   "handle toggle of a card"
    [cards card]
         (mapv
            #(if (= % card) (assoc % :hidden false) % )
               cards))


(defn toggle-card [app card]
  "persist the toggle"
  (when  (:hidden card)
       (om/transact! app :cards
                  #(-> %
                       handle-doubles
                      (toggle card)
                       eliminate-equals))
       (om/transact! app :turns inc)))
  (.log js/console (:turns @board))


(defn hidden-card-view [card _]
  (reify
     om/IRenderState
       (render-state [this {:keys [turnaround]}]
          (let [put-card #(put! turnaround @card)]
             (dom/div #js {:className "hidden-card"
                           :onClick put-card
                           :onTouchEnd put-card}
                     (dom/span nil ""))))))

(defn shown-card-view [card _]
  (reify
     om/IRenderState
       (render-state [this {:keys [turnaround]}]
          (let [{label :label} card]
                (dom/div #js {:className "shown-card"}
                         (if (is-img card)
                           (dom/img #js {:className "shown-img"
                                         :src       label})
                           (dom/span nil label)))))))


(defn found-card-view [card _]
  (reify
     om/IRenderState
       (render-state [this {:keys [turnaround]}]
          (let [{label :label} card]
             (dom/div #js {:className "found-card"}
                      (if (is-img card)
                         (dom/img #js {:className "shown-img"
                                       :src       label})
                         (dom/span nil label)))))))

(defmulti card-view
   (fn [{found :found hidden :hidden} card]
        [found hidden]))


(defmethod card-view [false true]
  [card owner] (hidden-card-view card owner))

(defmethod card-view [false false]
  [card owner] (shown-card-view card owner))

(defmethod card-view [true true]
  [card owner] (found-card-view card owner))

(defn memory-board
  "om component for board"
  [app owner]
   (reify
     om/IInitState
     (init-state[_]
       {:turnaround (chan)})
     om/IWillMount
     (will-mount [_]
       (let [turnaround (om/get-state owner :turnaround)]
         (go (loop []
                  (toggle-card app (<! turnaround))
                  (recur))
         )))
     om/IRenderState
     (render-state [this {:keys [turnaround]}]
       (dom/div nil
            (om/build login-component app)
                (dom/div #js {:className "header pure-g"}
                         (dom/div #js {:className "pure-u-1-4"} (str "aantal beurten: " (quot (:turns app) 2)))
                         (dom/div #js {:className "pure-u-1-4"} (str "resterend: " (amount-remaining-pairs (:cards app))))
                         (dom/div #js {:className "pure-u-1-4"}
                                  (dom/button #js {:onClick #(reset-board labels)} "opnieuw"))
                         (if (:id app)
                           (dom/div #js {:className "pure-u-1-4"}
                                    (dom/button #js {:onClick #(fetch-from-instafeed (:token app))} "gebruik instagram plaatjes"))))
                (apply dom/div #js {:className "board"}
                 (om/build-all card-view (:cards app)
                               {:init-state {:turnaround turnaround}}))))))

(om/root memory-board board
  {:target (. js/document (getElementById "memory"))})