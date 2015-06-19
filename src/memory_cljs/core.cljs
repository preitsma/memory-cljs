(ns memory-cljs.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as str]
            [cljs.core.async :refer [put! chan <!]]
            [clojure.browser.repl :as repl]
            [cemerick.url :refer [url-encode]]
            [memory-cljs.sandbox :refer [log4]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:import [goog.net Jsonp]
           [goog Uri]))

;CLIENT ID	dea1d49dd5684444a44da24d64c88206
;CLIENT SECRET	cdb4a7da55394e66b1c12c45da42ff59
;WEBSITE URL	http://plance.nl/memory-cljs/
;REDIRECT URI	http://plance.nl/memory-cljs#login

(enable-console-print!)

(def labels
    ["aap","noot","mies","wim","vuur","zus","jet","teun","schapen"])

(defn log [s]
  (.log js/console s))


(defn add-card
   "create card element based on a label"
    [v label]
       (conj v {:id (count v) :label label :hidden true :found false}))


(defn create-all-cards
   "create set of cards based on the set of labels"
    [labels]
    (reduce add-card []
         (shuffle
           (concat labels labels))))

(defn create-board
  []
  {:cards
            (into []
                  (create-all-cards labels))
   :turns   0
   :session {}})

(def board
  (atom (create-board)))

(defn reset-board
   []
   (reset! board
      (create-board)))

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
                     (dom/span nil label))))))

(defn found-card-view [card _]
  (reify
     om/IRenderState
       (render-state [this {:keys [turnaround]}]
          (let [{label :label} card]
             (dom/div #js {:className "found-card"}
                     (dom/span nil label))))))

(defmulti card-view
   (fn [{found :found hidden :hidden} card]
        [found hidden]))


(defmethod card-view [false true]
  [card owner] (hidden-card-view card owner))

(defmethod card-view [false false]
  [card owner] (shown-card-view card owner))

(defmethod card-view [true true]
  [card owner] (found-card-view card owner))


(def insta-client-id "dea1d49dd5684444a44da24d64c88206")
(def insta-redirect-url "http://localhost:3449/index.html#login")

(def insta-oauth-url "https://instagram.com/oauth/authorize/?client_id=CLIENT-ID&redirect_uri=REDIRECT-URI&response_type=token")

(def insta-user-info-url "https://api.instagram.com/v1/users/self/?access_token=" )


(defn oauth-url
  "creates the oauth url"
  [url client-id redirect-url]
  (str/replace 
      (str/replace url #"CLIENT-ID" client-id)
      #"REDIRECT-URI"
      (url-encode redirect-url)))

(defn get-token
  "retrieve the token"
  [url]
  (second (str/split url #"token=")))

(defn jsonp [uri]
  (let [out (chan)
        req (Jsonp. (Uri. uri))]
    (.send req nil (fn [res] (put! out res)))
    out))

(defn clear-session
  [owner]
  (om/set-state! owner :token nil)
  (om/set-state! owner :id nil)
  (aset window.location "hash" "#")
  )

(defn login-component
  "renders the login link"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [url (.-href (.-location js/window))]
        {:token (get-token url)}
        ))
    om/IWillMount
    (will-mount [_]
      (go
        (let [obj (<! (jsonp (str insta-user-info-url (om/get-state owner :token))))]
          (om/set-state! owner :fullname (-> obj .-data .-full_name))
          (om/set-state! owner :id (-> obj .-data .-id))
          (om/set-state! owner :username (-> obj .-data .-username))
          ))
     )
    om/IRenderState
    (render-state [_ {:keys [token fullname]}]
        (if token
          (dom/div nil
             (dom/span nil (str "Logged in as " fullname " "))
             (dom/button #js {:onClick #(clear-session owner)}
                    (dom/span nil "logout")))
          (dom/a #js {:href (oauth-url insta-oauth-url insta-client-id insta-redirect-url)}
                 (dom/p nil "Login in instagram"))
          ))))


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
             (dom/div #js {:className "pure-u-1-3"} (str "aantal beurten: " (quot (:turns app) 2)))
             (dom/div #js {:className "pure-u-1-3"} (str "resterend: " (amount-remaining-pairs (:cards app))))
             (dom/div #js {:className "pure-u-1-3"}
               (dom/button #js {:onClick #(reset-board) } "opnieuw")))
             (apply dom/div #js {:className "board"}
               (om/build-all card-view (:cards app)
                 {:init-state {:turnaround turnaround}}))))))

(om/root memory-board board
  {:target (. js/document (getElementById "memory"))})

