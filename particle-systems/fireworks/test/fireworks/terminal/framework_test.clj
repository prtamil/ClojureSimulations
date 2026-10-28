(ns fireworks.terminal.framework-test
  (:require
   [clojure.test :refer [deftest is]]
   [fireworks.terminal.framework :as terminal])
  (:import
   (com.googlecode.lanterna TerminalSize)
   (com.googlecode.lanterna.input KeyStroke KeyType)
   (com.googlecode.lanterna.screen Screen$RefreshType TerminalScreen)
   (com.googlecode.lanterna.terminal.virtual DefaultVirtualTerminal)))

(deftest lanterna-keys-are-normalized
  (is (= {:type :character :character \q}
         (terminal/keystroke->event (KeyStroke. \q false false))))
  (is (= {:type :escape}
         (terminal/keystroke->event (KeyStroke. KeyType/Escape))))
  (is (= {:type :eof}
         (terminal/keystroke->event (KeyStroke. KeyType/EOF))))
  (is (= {:type :key :key :arrowup}
         (terminal/keystroke->event (KeyStroke. KeyType/ArrowUp))))
  (is (nil? (terminal/keystroke->event nil))))

(deftest windows-console-keys-are-normalized
  (is (= {:type :escape} (terminal/windows-key->event true 27 \u0000)))
  (is (= {:type :character :character (char 3)}
         (terminal/windows-key->event true 67 (char 3))))
  (is (= {:type :character :character \q}
         (terminal/windows-key->event true 81 \q)))
  (is (= {:type :key :virtual-key-code 16}
         (terminal/windows-key->event true 16 \u0000)))
  (is (nil? (terminal/windows-key->event false 81 \q))))

(deftest immediate-mode-primitives-write-the-retained-back-buffer
  (with-open [virtual-terminal
              (DefaultVirtualTerminal. (TerminalSize. 10 5))
              screen (TerminalScreen. virtual-terminal)]
    (.startScreen screen)
    (let [surface {:screen screen
                   :graphics (.newTextGraphics screen)
                   :columns 10
                   :rows 5}]
      (terminal/clear! surface)
      (terminal/draw-box! surface 0 0 6 4)
      (terminal/set-bold! surface true)
      (terminal/put-character! surface 2 2 \O)
      (terminal/put-string-clipped! surface 0 4 2 "footer")
      (terminal/put-string-right! surface 4 "end")
      (is (= \+ (.getCharacter (.getBackCharacter screen 0 0))))
      (is (= \- (.getCharacter (.getBackCharacter screen 3 0))))
      (is (= \| (.getCharacter (.getBackCharacter screen 5 2))))
      (is (.isBold (.getBackCharacter screen 2 2)))
      (is (= \f (.getCharacter (.getBackCharacter screen 0 4))))
      (is (= \e (.getCharacter (.getBackCharacter screen 7 4))))
      (.refresh screen Screen$RefreshType/DELTA)
      (terminal/erase-character! surface 2 2)
      (is (= \space (.getCharacter (.getBackCharacter screen 2 2)))))))

(deftest quit-sentinel-is-public-and-unambiguous
  (is (= :fireworks.terminal.framework/quit terminal/quit)))

(deftest viewport-fitting-and-point-projection-are-aspect-correct
  (let [viewport (terminal/fit-viewport
                  80 24 40 20
                  {:cell-aspect 2.0
                   :padding-top 1
                   :padding-bottom 1
                   :border 1})]
    (is (= {:columns 78 :rows 20 :left 0 :top 1 :border 1}
           viewport))
    (is (= {:column 21 :row 7}
           (terminal/project-point viewport 10.0 5.0 40 20)))))
