;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns build
  (:require
   [clojure.tools.build.api :as b]))

(def lib 'tamilselvan/leaf-fall)
(def version "1.0.0")
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (let [jar-file (format "target/%s-%s.jar" (name lib) version)]
    (clean nil)
    (b/write-pom {:class-dir class-dir :lib lib :version version
                  :basis @basis :src-dirs ["src"]})
    (b/copy-dir {:src-dirs ["src" "driver"] :target-dir class-dir})
    (b/jar {:class-dir class-dir :jar-file jar-file})
    {:jar-file jar-file}))

(defn uber [_]
  (let [uber-file (format "target/%s-%s-standalone.jar" (name lib) version)]
    (clean nil)
    (b/copy-dir {:src-dirs ["src" "driver"] :target-dir class-dir})
    (b/compile-clj {:basis @basis :src-dirs ["src" "driver"]
                    :class-dir class-dir :ns-compile '[driver]})
    (b/uber {:class-dir class-dir :uber-file uber-file :basis @basis
             :main 'driver})
    {:uber-file uber-file}))
