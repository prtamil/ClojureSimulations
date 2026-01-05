(ns build
  (:require
   [clojure.tools.build.api :as b]))

(def lib 'sim/app)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def version "0.1.0")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/copy-dir
   {:src-dirs ["src" "driver"]
    :target-dir class-dir})

  (b/compile-clj
   {:basis basis
    :src-dirs ["src" "driver"]
    :class-dir class-dir})

  (b/jar
   {:class-dir class-dir
    :jar-file jar-file
    :basis basis
    :main 'driver})

  (println "JAR built:" jar-file))

(defn uber [_]
  (clean nil)

  (b/copy-dir
   {:src-dirs ["src" "driver"]
    :target-dir class-dir})

  (b/compile-clj
   {:basis basis
    :src-dirs ["src" "driver"]
    :class-dir class-dir})

  (b/uber
   {:class-dir class-dir
    :uber-file uber-file
    :basis basis
    :main 'driver})

  (println "UBERJAR built:" uber-file))
