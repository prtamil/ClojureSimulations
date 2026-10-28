;; Copyright (c) 2026 Tamilselvan R  SPDX-License-Identifier: MIT
(ns bonsai.terminal.framework
  "Reusable Lanterna lifecycle, input, drawing, buffering, and frame loop."
  (:require
   [bonsai.utility.clock :as clock])
  (:import
   (com.googlecode.lanterna SGR TerminalSize TextCharacter TextColor$RGB)
   (com.googlecode.lanterna.graphics TextGraphics)
   (com.googlecode.lanterna.input KeyStroke KeyType)
   (com.googlecode.lanterna.screen Screen Screen$RefreshType TerminalScreen)
   (com.googlecode.lanterna.terminal DefaultTerminalFactory)
   (com.googlecode.lanterna.terminal.ansi ANSITerminal UnixLikeTerminal$CtrlCBehaviour)
   (com.sun.jna.platform.win32 Kernel32 Wincon Wincon$CONSOLE_SCREEN_BUFFER_INFO
                               Wincon$INPUT_RECORD)
   (com.sun.jna.ptr IntByReference)
   (java.io ByteArrayInputStream)
   (java.nio.charset StandardCharsets)))

(def ^:private nanoseconds-per-second 1000000000)
(def ^:private nanoseconds-per-millisecond 1000000)
(def ^:private fps-update-ns (* 500 nanoseconds-per-millisecond))
(def ^:private max-events-per-frame 64)
(def ^:private bold-modifiers (into-array SGR [SGR/BOLD]))

(def quit
  "Return this value from :handle-event to stop an animation."
  ::quit)

(defn- windows? []
  (.startsWith (.toLowerCase (System/getProperty "os.name")) "windows"))

(defn- checked-console-mode [kernel32 handle stream-name]
  (let [mode (IntByReference.)]
    (when-not (.GetConsoleMode kernel32 handle mode)
      (throw (ex-info (str "No native Windows " stream-name
                           " console is attached")
                      {:stream stream-name})))
    (.getValue mode)))

(defn- set-console-mode! [kernel32 handle mode stream-name]
  (when-not (.SetConsoleMode kernel32 handle mode)
    (throw (ex-info (str "Could not configure the Windows " stream-name
                         " console for terminal animation")
                    {:stream stream-name :mode mode}))))

(defn- acquire-windows-console! []
  (let [kernel32 Kernel32/INSTANCE
        input-handle (.GetStdHandle kernel32 Wincon/STD_INPUT_HANDLE)
        output-handle (.GetStdHandle kernel32 Wincon/STD_OUTPUT_HANDLE)
        input-mode (checked-console-mode kernel32 input-handle "input")
        output-mode (checked-console-mode kernel32 output-handle "output")
        interactive-input-mode
        (-> input-mode
            (bit-and (bit-not (bit-or Wincon/ENABLE_LINE_INPUT
                                      Wincon/ENABLE_ECHO_INPUT
                                      Wincon/ENABLE_QUICK_EDIT_MODE
                                      Wincon/ENABLE_PROCESSED_INPUT
                                      Wincon/ENABLE_VIRTUAL_TERMINAL_INPUT)))
            (bit-or Wincon/ENABLE_EXTENDED_FLAGS
                    Wincon/ENABLE_WINDOW_INPUT))
        ansi-output-mode
        (bit-or output-mode
                Wincon/ENABLE_PROCESSED_OUTPUT
                Wincon/ENABLE_WRAP_AT_EOL_OUTPUT
                Wincon/ENABLE_VIRTUAL_TERMINAL_PROCESSING)]
    (set-console-mode! kernel32 input-handle interactive-input-mode "input")
    (try
      (set-console-mode! kernel32 output-handle ansi-output-mode "output")
      {:kernel32 kernel32
       :input-handle input-handle
       :output-handle output-handle
       :input-mode input-mode
       :output-mode output-mode}
      (catch Throwable error
        (.SetConsoleMode kernel32 input-handle input-mode)
        (throw error)))))

(defn- release-windows-console!
  [{:keys [kernel32 input-handle output-handle input-mode output-mode]}]
  (.SetConsoleMode kernel32 input-handle input-mode)
  (.SetConsoleMode kernel32 output-handle output-mode))

(defn- windows-console-size [{:keys [kernel32 output-handle]}]
  (let [info (Wincon$CONSOLE_SCREEN_BUFFER_INFO.)]
    (if (.GetConsoleScreenBufferInfo kernel32 output-handle info)
      (let [window (.-srWindow info)]
        (TerminalSize. (inc (- (int (.-Right window))
                               (int (.-Left window))))
                       (inc (- (int (.-Bottom window))
                               (int (.-Top window))))))
      (TerminalSize. 80 24))))

(defn- default-screen [prefer-emulator?]
  (.createScreen
   (doto (DefaultTerminalFactory.)
     (.setPreferTerminalEmulator prefer-emulator?)
     (.setUnixTerminalCtrlCBehaviour UnixLikeTerminal$CtrlCBehaviour/TRAP))))

(defn- windows-screen-context []
  (try
    (let [console-state
          (assoc (acquire-windows-console!)
                 :available-events (IntByReference.)
                 :records-read (IntByReference.)
                 :input-records
                 (.toArray (Wincon$INPUT_RECORD.) max-events-per-frame))]
      (try
        (let [terminal
              ;; Windows events are consumed directly below. A disconnected
              ;; Lanterna input stream prevents two decoders racing for them.
              (proxy [ANSITerminal]
                     [(ByteArrayInputStream. (byte-array 0))
                      System/out
                      StandardCharsets/UTF_8]
                (getTerminalSize []
                  (windows-console-size console-state)))]
          {:screen (TerminalScreen. terminal)
           :console-state console-state
           :release #(release-windows-console! console-state)})
        (catch Throwable error
          (release-windows-console! console-state)
          (throw error))))
    (catch Throwable _error
      ;; IDE consoles are not native terminals; use Lanterna's emulator.
      {:screen (default-screen true)
       :release (constantly nil)})))

(defn- screen-context []
  (if (windows?)
    (windows-screen-context)
    {:screen (default-screen false)
     :release (constantly nil)}))

(defn keystroke->event
  "Normalize a Lanterna keystroke into renderer-independent terminal data."
  [^KeyStroke keystroke]
  (when keystroke
    (let [key-type (.getKeyType keystroke)]
      (condp = key-type
        KeyType/Character {:type :character
                           :character (.getCharacter keystroke)}
        KeyType/Escape {:type :escape}
        KeyType/EOF {:type :eof}
        {:type :key
         :key (keyword (.toLowerCase (.name key-type)))}))))

(defn windows-key->event
  "Normalize one low-level Windows console key event."
  [key-down? virtual-key-code character]
  (when key-down?
    (cond
      (= 27 virtual-key-code) {:type :escape}
      (not= \u0000 character) {:type :character :character character}
      :else {:type :key :virtual-key-code virtual-key-code})))

(defn- input-record->event [^Wincon$INPUT_RECORD record]
  (when (= Wincon$INPUT_RECORD/KEY_EVENT (.-EventType record))
    (let [key-event (.-KeyEvent (.-Event record))]
      (windows-key->event (.-bKeyDown key-event)
                          (int (.-wVirtualKeyCode key-event))
                          (.-uChar key-event)))))

(defn- windows-console-events
  [{:keys [kernel32 input-handle available-events records-read
           input-records]}]
  (let [^IntByReference available-events available-events
        ^IntByReference records-read records-read]
    (when-not (.GetNumberOfConsoleInputEvents kernel32 input-handle
                                               available-events)
      (throw (ex-info "Could not inspect the Windows console input buffer" {})))
    (if (zero? (.getValue available-events))
      []
      (let [record-count (min max-events-per-frame
                              (.getValue available-events))]
        (when-not (.ReadConsoleInput kernel32 input-handle input-records
                                    record-count records-read)
          (throw (ex-info "Could not read the Windows console input buffer" {})))
        (->> input-records
             (take (.getValue records-read))
             (keep input-record->event)
             vec)))))

(defn- lanterna-events [^Screen screen]
  (loop [events []
         keystroke (.pollInput screen)
         event-count 0]
    (if (and keystroke (< event-count max-events-per-frame))
      (recur (conj events (keystroke->event keystroke))
             (.pollInput screen)
             (inc event-count))
      events)))

(defn rgb
  "Create a Lanterna color from an [red green blue] vector."
  [[red green blue]]
  (TextColor$RGB. (int red) (int green) (int blue)))

(defn- graphics ^TextGraphics [{:keys [^TextGraphics graphics]}]
  graphics)

(defn clear!
  "Clear the retained back buffer in a render callback."
  [{:keys [^Screen screen] :as terminal}]
  (.clear screen)
  (.clearModifiers (graphics terminal)))

(defn set-foreground!
  "Select a color previously created with rgb."
  [terminal color]
  (.setForegroundColor (graphics terminal) color))

(defn set-bold!
  "Enable or disable bold for subsequent drawing operations."
  [terminal bold?]
  (if bold?
    (.enableModifiers (graphics terminal) bold-modifiers)
    (.disableModifiers (graphics terminal) bold-modifiers)))

(defn clear-style!
  "Clear all text modifiers for subsequent drawing operations."
  [terminal]
  (.clearModifiers (graphics terminal)))

(defn put-string!
  "Write text into the retained back buffer."
  [terminal column row text]
  (.putString (graphics terminal) column row text))

(defn put-string-clipped!
  "Write at most `width` characters into the retained back buffer."
  [terminal column row width text]
  (let [visible-width (min (count text) (max 0 width))]
    (when (pos? visible-width)
      (put-string! terminal column row (subs text 0 visible-width)))))

(defn put-string-right!
  "Right-align text on a terminal row, clipping it to the surface width."
  [{:keys [columns] :as terminal} row text]
  (let [visible-width (min (count text) (max 0 columns))
        visible-text (subs text 0 visible-width)]
    (when (pos? visible-width)
      (put-string! terminal (- columns visible-width) row visible-text))))

(defn put-character!
  "Write one character into the retained back buffer."
  [terminal column row character]
  (.setCharacter (graphics terminal) column row character))

(defn erase-character!
  "Restore one retained-buffer cell to Lanterna's default blank character."
  [{:keys [^Screen screen]} column row]
  (.setCharacter screen column row TextCharacter/DEFAULT_CHARACTER))

(defn draw-line!
  "Draw an inclusive line into the retained back buffer."
  [terminal column-1 row-1 column-2 row-2 character]
  (.drawLine (graphics terminal)
             (int column-1) (int row-1)
             (int column-2) (int row-2)
             (char character)))

(defn draw-box!
  "Draw an ASCII box into the retained back buffer."
  [terminal left top width height]
  (when (and (>= width 2) (>= height 2))
    (let [right (+ left width -1)
          bottom (+ top height -1)]
      (draw-line! terminal (inc left) top (dec right) top \-)
      (draw-line! terminal (inc left) bottom (dec right) bottom \-)
      (draw-line! terminal left (inc top) left (dec bottom) \|)
      (draw-line! terminal right (inc top) right (dec bottom) \|)
      (put-character! terminal left top \+)
      (put-character! terminal right top \+)
      (put-character! terminal left bottom \+)
      (put-character! terminal right bottom \+))))

(defn fit-viewport
  "Fit aspect-correct logical content inside a padded terminal region.

  `cell-aspect` is terminal cell height divided by width. `border` reserves an
  equal number of cells on all four sides of the returned content area."
  [window-columns window-rows logical-width logical-height
   {:keys [cell-aspect padding-left padding-right padding-top padding-bottom
           border]
    :or {cell-aspect 2.0
         padding-left 0
         padding-right 0
         padding-top 0
         padding-bottom 0
         border 0}}]
  (when-not (and (pos? logical-width) (pos? logical-height)
                 (pos? cell-aspect))
    (throw (ex-info "Logical dimensions and cell aspect must be positive"
                    {:logical-width logical-width
                     :logical-height logical-height
                     :cell-aspect cell-aspect})))
  (let [usable-columns (max 0 (- window-columns padding-left padding-right))
        usable-rows (max 0 (- window-rows padding-top padding-bottom))
        max-columns (max 0 (- usable-columns (* 2 border)))
        max-rows (max 0 (- usable-rows (* 2 border)))]
    (if (or (zero? max-columns) (zero? max-rows))
      {:columns max-columns
       :rows max-rows
       :left padding-left
       :top padding-top
       :border border}
      (let [target-ratio (/ (* (double logical-width) cell-aspect)
                            logical-height)
            available-ratio (/ (double max-columns) max-rows)
            [columns rows]
            (if (> available-ratio target-ratio)
              [(min max-columns
                    (max 1 (int (Math/round (* max-rows target-ratio)))))
               max-rows]
              [max-columns
               (min max-rows
                    (max 1 (int (Math/round (/ max-columns
                                               target-ratio)))))])
            outer-width (+ columns (* 2 border))
            outer-height (+ rows (* 2 border))]
        {:columns columns
         :rows rows
         :left (+ padding-left (quot (- usable-columns outer-width) 2))
         :top (+ padding-top (quot (- usable-rows outer-height) 2))
         :border border}))))

(defn project-point
  "Project a logical point into a fitted viewport with stable half-up rounding."
  [{:keys [columns rows left top border]}
   x y logical-width logical-height]
  (when (and (pos? columns) (pos? rows))
    (let [cell-x (-> (* x (/ (double columns) logical-width))
                     (+ 0.5) Math/floor int
                     (max 0) (min (dec columns)))
          cell-y (-> (* y (/ (double rows) logical-height))
                     (+ 0.5) Math/floor int
                     (max 0) (min (dec rows)))]
      {:column (+ left border cell-x)
       :row (+ top border cell-y)})))

(defn- refresh-type [refresh]
  (case refresh
    :automatic Screen$RefreshType/AUTOMATIC
    :delta Screen$RefreshType/DELTA
    :complete Screen$RefreshType/COMPLETE
    (throw (ex-info "Unknown terminal refresh strategy"
                    {:refresh refresh}))))

(defn- sleep-until-next-frame! [frame-start-ns render-fps]
  (when-not (pos-int? render-fps)
    (throw (ex-info "Render FPS must be a positive integer"
                    {:frames-per-second render-fps})))
  (let [frame-duration-ns (quot nanoseconds-per-second render-fps)
        remaining-ns (- frame-duration-ns
                        (- (System/nanoTime) frame-start-ns))]
    (when (pos? remaining-ns)
      (Thread/sleep (quot remaining-ns nanoseconds-per-millisecond)
                    (int (rem remaining-ns
                              nanoseconds-per-millisecond))))))

(defn- handle-events [state events handle-event]
  (loop [state state
         [event & remaining] events]
    (if (nil? event)
      {:state state :quit? false}
      (let [next-state (handle-event state event)]
        (if (= quit next-state)
          {:state state :quit? true}
          (recur next-state remaining))))))

(defn- present! [^Screen screen refresh]
  (.setCursorPosition screen nil)
  (.refresh screen (refresh-type refresh)))

(defn- terminal-context [^Screen screen ^TerminalSize size]
  ;; TextGraphics is a lightweight view over Screen, but constructing one on
  ;; every frame is needless allocation. Rebuild it only when Screen resizes.
  {:screen screen
   :graphics (.newTextGraphics screen)
   :columns (.getColumns size)
   :rows (.getRows size)})

(defn- setting-value [setting state setting-name]
  (let [value (if (fn? setting) (setting state) setting)]
    (when-not (pos-int? value)
      (throw (ex-info (str (name setting-name)
                           " must resolve to a positive integer")
                      {:setting setting-name :value value})))
    value))

(defn- tick-state [state tick-count dt tick]
  (loop [state state
         remaining tick-count]
    (if (zero? remaining)
      state
      (recur (tick state dt) (dec remaining)))))

(defn- render-frame!
  [^Screen screen terminal state frame draw! complete?]
  ;; Immediate-mode contract: every frame starts from a known blank retained
  ;; buffer. Lanterna still compares it with its front buffer and transmits
  ;; only changed cells when present! runs once below.
  (clear! terminal)
  (draw! terminal state frame)
  (present! screen (if complete? :automatic :delta)))

(defn- animation-loop!
  [^Screen screen console-state
   {:keys [start resize tick tick-batch draw! handle-event simulation-fps
           render-fps max-frame-delta-ms finish]}]
  (let [initial-terminal (terminal-context screen (.getTerminalSize screen))
        initial-state (start initial-terminal)
        loop-start-ns (System/nanoTime)]
    (loop [state initial-state
           clock-state (clock/initial-state loop-start-ns)
           terminal initial-terminal
           fps-window-start-ns loop-start-ns
           frame-count 0
           measured-fps 0.0
           frame-number 0]
      (let [frame-start-ns (System/nanoTime)
            resized-size (.doResizeIfNecessary screen)
            resized? (some? resized-size)
            resized-terminal (when resized?
                               (terminal-context screen resized-size))
            ;; A resize can block in the host terminal. Reset timing before
            ;; advancing so that delay never becomes simulation catch-up.
            state (if (and resized? resize)
                    (resize state resized-terminal)
                    state)
            clock-state (if resized?
                          (clock/initial-state frame-start-ns)
                          clock-state)
            sim-fps (setting-value simulation-fps state :simulation-fps)
            max-delta-ms (setting-value max-frame-delta-ms state
                                        :max-frame-delta-ms)
            {:keys [clock-state ticks dt alpha]}
            (clock/advance clock-state frame-start-ns sim-fps
                           (* max-delta-ms nanoseconds-per-millisecond))
            state (cond
                    (zero? ticks) state
                    tick-batch (tick-batch state ticks dt)
                    :else (tick-state state ticks dt tick))
            fps-window-start-ns (if resized?
                                  frame-start-ns
                                  fps-window-start-ns)
            frame-count (if resized? 1 (inc frame-count))
            fps-elapsed-ns (- frame-start-ns fps-window-start-ns)
            fps-updated? (>= fps-elapsed-ns fps-update-ns)
            measured-fps (if fps-updated?
                           (/ (* frame-count (double nanoseconds-per-second))
                              fps-elapsed-ns)
                           measured-fps)
            fps-window-start-ns (if fps-updated?
                                  frame-start-ns
                                  fps-window-start-ns)
            frame-count (if fps-updated? 0 frame-count)
            terminal (if resized? resized-terminal terminal)
            frame {:number frame-number
                   :now-ns frame-start-ns
                   :ticks ticks
                   :dt dt
                   :alpha alpha
                   :measured-fps measured-fps
                   :resized? resized?}]
        (render-frame! screen terminal state frame draw!
                       (or (zero? frame-number) resized?))
        (let [events (if console-state
                       (windows-console-events console-state)
                       (lanterna-events screen))
              {:keys [state quit?]} (handle-events state events handle-event)]
          (if quit?
            (finish state)
            (do
              ;; Refresh is included in the budget. If the terminal itself is
              ;; slower than the target rate we skip sleep rather than queueing
              ;; catch-up frames that can never be displayed.
              (sleep-until-next-frame! frame-start-ns
                                       (setting-value render-fps state
                                                      :render-fps))
              (recur state clock-state terminal fps-window-start-ns
                     frame-count measured-fps (inc frame-number)))))))))

(defn run-animation!
  "Run an immediate-mode, fixed-step terminal animation.

  The framework owns screen lifecycle, the fixed-step accumulator, resize
  recovery, buffer clearing, one delta-buffered present per frame, measured
  FPS, pacing, and bounded input draining.

  Animation callbacks:
  - start [terminal] -> initial immutable state
  - tick [state fixed-dt-seconds] -> next state
  - tick-batch [state tick-count fixed-dt-seconds] -> next state (optional
    optimized alternative to repeated tick calls)
  - draw! [terminal state frame] -> draw into the retained back buffer
  - handle-event [state normalized-event] -> next state or terminal/quit
  - resize [state terminal] -> resized state (optional)
  - simulation-fps, render-fps, max-frame-delta-ms: positive integers or
    functions of state
  - finish [state] -> final application value"
  [callbacks]
  (let [callbacks
        (merge {:start (fn [_terminal] nil)
                :resize nil
                :tick (fn [state _dt] state)
                :tick-batch nil
                :handle-event (fn [state _event] state)
                :simulation-fps 60
                :render-fps 60
                :max-frame-delta-ms 100
                :finish identity}
               callbacks)
        {:keys [screen console-state release]} (screen-context)]
    (when-not (fn? (:draw! callbacks))
      (throw (ex-info "run-animation! requires a :draw! callback" {})))
    (try
      (with-open [screen screen]
        (.startScreen screen)
        (animation-loop! screen console-state callbacks))
      (finally
        (release)))))
