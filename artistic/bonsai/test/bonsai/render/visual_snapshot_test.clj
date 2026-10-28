;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.render.visual-snapshot-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bonsai.model.config :as config]
   [bonsai.model.core :as model]
   [bonsai.render.ascii :as ascii]
   [bonsai.utility.theme :as theme])
  (:import
   [java.nio.charset StandardCharsets]
   [java.security MessageDigest]
   [java.util HexFormat]))

(def ^:private snapshot-seed 424242)

;; These hashes cover every character in the complete ASCII frame, including
;; the header, trunk, branches, foliage or snow, and pot. A deliberate visual
;; change should be inspected first and these values updated only afterward.
(def ^:private expected-snapshots
  {[:chokkan "SPRING"] "eb65206265efda7bbf152833bcff66c87bca9accd9caa0bcb3e2c649e4bd1db5"
   [:chokkan "SUMMER"] "ecbcc00b224164d6472bfa45da1efa066fb3ac2a98907acece24fb5c17f24477"
   [:chokkan "AUTUMN"] "f6f3ea70c967ae7d0a0b2b6fc03687bb59834cb820924eecc4f107879541ba6a"
   [:chokkan "WINTER"] "037855e22dc8fba3b6e1aaca5c90f8f0169203fa024e22a8b1042a64de38e9f7"
   [:chokkan "CHERRY"] "4ea6807743bd33bdbd62d67e8e51064c845ca8d4bab18d5cd2e9e93ee9c57012"
   [:chokkan "MONO"] "386f6a33fd1f18b78c4274035ad962850b8cbccdf341620cd35ff32653fd7704"

   [:moyogi "SPRING"] "db74c99c723ebbb0806146022083c9712cced79dc7ff21c3301a395c3af6fd9c"
   [:moyogi "SUMMER"] "575e0ac5a520de26a14f2a948adcf5e26a576c39b7774c8292d51cf702f122ac"
   [:moyogi "AUTUMN"] "f97a88dca4f0efc0ee118778c582c215f9e205e801b30abada19b8e2e85e5246"
   [:moyogi "WINTER"] "ee159636eb0d225da17a797db58a9d4b9a48dcf89224377826271ddc88e3f8ed"
   [:moyogi "CHERRY"] "4407522923b6605ce036894ddfbfa92452fbe0e0a93d63735c2f4e0745cdd96d"
   [:moyogi "MONO"] "a346ce09c5b562b74ea5a740ab7a3c39f0d2f45e57c178da4c81a5d72c2d3a21"

   [:shakan "SPRING"] "8082b26f7c3cf38b7736e439fe19e3883de53ec7468fae84a3d6edabb7d90665"
   [:shakan "SUMMER"] "495fe8203c843399a42f91051e643eadb1c3162b3dbeaaba3d2822b8e5825cb1"
   [:shakan "AUTUMN"] "55c3c3bb46558f2824abcc11e5e2b2df35b2132a688c6e3711797e1ca36f8ed1"
   [:shakan "WINTER"] "338729ebee090477307962e5d1ebe6c5fa8729bf1aa4cc9c69b08e71351d6036"
   [:shakan "CHERRY"] "da2ba91eeaf2ec5fc4ff43a53599b306db6b12f31f0ce7112879547815546ead"
   [:shakan "MONO"] "76ca74bbf0dc6bf949192747e5b049df28e96e08122f404504b21f0dcc8b577a"

   [:kengai "SPRING"] "d5b72172dd1578526ece3ec1f987d7b68d5dea07cbf21146700e8a8d5212310e"
   [:kengai "SUMMER"] "0ff980998d758162a1670572a51b18e348d1aa1a644a94aeb6a7be13fa19827b"
   [:kengai "AUTUMN"] "20601ca24b852af2175c68ab523503d604990da06d56735aafb7ae6d276e0cf4"
   [:kengai "WINTER"] "477ffc41e6a8d1c6cfa4771d43f520b6c7b4242cfae756ddcfd416a04da4cc54"
   [:kengai "CHERRY"] "824f8981b24888c01ca4b777ad437cddf5b34785cc8c5568f5c7d76c043157ff"
   [:kengai "MONO"] "bc09ad9d08b73286343110ea643d7ee93e82c2a31f75f86e42d6092f97e078a0"

   [:bunjin "SPRING"] "984c8674141efbb06a9c86ee65ce0ef1d846f7d29779a524b8af8cae71669e2f"
   [:bunjin "SUMMER"] "93584ff8f458d29d0e5d76f867f71dcb2a09c9ae5458579ab1023cbf929c542f"
   [:bunjin "AUTUMN"] "8ec990df617bec79b35a8e1581ab34e09332ba4655e5ae005fc2afc5daa4a271"
   [:bunjin "WINTER"] "e2a2f4cd74bf87d9ebc57e04de0095968de21b0eaca47ad1161bd1ed85564db6"
   [:bunjin "CHERRY"] "a17ca0bd46221345810b1fa426b2649bfc4955c7bb8f4ad95a48a89d1c069c2d"
   [:bunjin "MONO"] "880513271ffe0cea25c1749a6680f6d3afc3168a1bb6ae0fcce7f8577dece5dd"})

(defn- sha-256 [text]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (.formatHex (HexFormat/of)
                (.digest digest (.getBytes text StandardCharsets/UTF_8)))))

(deftest every-style-and-theme-matches-its-visual-snapshot
  (is (= (* (count config/style-order) (count theme/themes))
         (count expected-snapshots)))
  (let [base-world (model/initial-world snapshot-seed)]
    (doseq [style-index (range (count config/style-order))
            theme-index (range (count theme/themes))
            :let [world (model/rebuild-scene
                         (assoc base-world
                                :style-index style-index
                                :theme-index theme-index))
                  snapshot-key [(model/current-style-key world)
                                (:name (theme/current-theme world))]
                  actual (sha-256 (ascii/render-string world))]]
      (testing (str snapshot-key)
        (is (= (get expected-snapshots snapshot-key) actual))))))
