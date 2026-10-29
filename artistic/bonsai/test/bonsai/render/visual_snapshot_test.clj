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
  {[:chokkan "SPRING"] "56f34267634cd92b9623632c87dd65d4a6a4b84d217d13abdb32320cf05d4018"
   [:chokkan "SUMMER"] "f55b85d60562bb538b78d676f996a2c1c30e789dd0c547763fbefd776730f16c"
   [:chokkan "AUTUMN"] "959db4fabd741e9f5fe13f90bfd94f9a85882edf4e881764c13a65cf520714f6"
   [:chokkan "WINTER"] "1156086e90cb2ec43aa073707084fd5ae3c292d50b8ad426493c48369e794607"
   [:chokkan "CHERRY"] "9334b075e76103865a60bcbd22798fed2c2616671b63be03d9415bf6e4143054"
   [:chokkan "MONO"] "0de877a79dd290402a082b5a6430cf8e3337c41bbd8c0a4a94d82ab9b6224e81"

   [:moyogi "SPRING"] "27cc1cbedcb57df7228db6ba70e2feb8f56cb93b76c36e3ac44b4a7d8a493e7b"
   [:moyogi "SUMMER"] "72b4937f0d60c527755554596396e0f6b78660b7f343f8d174da49c4f6462fc3"
   [:moyogi "AUTUMN"] "13b8863881e1e6739aebfeb66b2326c3d82dfc12c1b5931a2305dd7de930687d"
   [:moyogi "WINTER"] "ef84e2532f98d894225c4cfe35c829597e93b7bcb7f1b34d9689836c117ea2d2"
   [:moyogi "CHERRY"] "8d413d39955f3510e5126673cbaf5991e470f92d23e18a24ce2b21bd50bd6e0b"
   [:moyogi "MONO"] "c0f702f662ce969f74f16d57f930fc6efedd38131c1a02726db4a6355684a23b"

   [:shakan "SPRING"] "03d6b1ca032d45f7bc70a099c3e451ba8380fc4160f2aacc1c482b60864c6956"
   [:shakan "SUMMER"] "99afa93e72990a43091cb8fee4a7510f426ebf911d17093e481d17dedb9a2c47"
   [:shakan "AUTUMN"] "a7c0e32f358d0e0c049689091f6e2b1592c159038ed4c922681dce14f09ef730"
   [:shakan "WINTER"] "72895a825ed40d324459ee08876263e20ba551d579cc4cf1a8aa738e07e3d5c8"
   [:shakan "CHERRY"] "fa6fc4c6715df84437eb2dd9269bd97af8329bfb8b1ababbae9d35779b9c3191"
   [:shakan "MONO"] "22e810d5c64c443cb37e871d355881aa425f56281675029cebf901562b4f97b7"

   [:kengai "SPRING"] "c34dc8838b74f72c43133c6dea5a619d9433dc2eb5afb15b497b636ed72cc9d0"
   [:kengai "SUMMER"] "8bd98e5bcd6f857b7d9c62516dba9aef9d7aec374810b986b08755e1c773d10f"
   [:kengai "AUTUMN"] "b92bfe6f380d0d7299ded9536979cbfb7ce90da47c317be523e5277efb033c46"
   [:kengai "WINTER"] "49c49ba618405cb5fb51384a7b0a76adafb72aee2f70a3589717fd77a91f408f"
   [:kengai "CHERRY"] "e3a432d5c3b4abd9450025d110dd349df2a55f91eac88d9ed22ac1e0e25858b8"
   [:kengai "MONO"] "3e26a060cdc3ca8dc8cdd61c0d692f038503c0d7e9dda762aeaf5ea0fd820a3f"

   [:bunjin "SPRING"] "04b8b65333980d8073da04c6711a8b9d0b56dd57c086e5fff35034f2dc31d94f"
   [:bunjin "SUMMER"] "4db73bcc698432c808ec6a079d5aeb8b1140762bcb6baaa9fcc48db780a0da50"
   [:bunjin "AUTUMN"] "3fddb2b0005d4143f9e5f80d31ef3ff250016d5be578cf8428d2c3102c4cae00"
   [:bunjin "WINTER"] "547d50445b4e534a53c6d391d9804edd1c86a7ef1853cefae75f39576196e842"
   [:bunjin "CHERRY"] "d47b77e1433562870896a10872089f1333d8c9bcbc9826b67dff2b0c38d6bb36"
   [:bunjin "MONO"] "a82b29b2094e4182442feda89e5f363b68738c793373933f857236648653562c"})

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
            :let [world (model/construct-tree
                         (assoc base-world
                                :style-index style-index
                                :theme-index theme-index))
                  snapshot-key [(model/current-style-key world)
                                (:name (theme/current-theme world))]
                  actual (sha-256 (ascii/render-string world))]]
      (testing (str snapshot-key)
        (is (= (get expected-snapshots snapshot-key) actual))))))
