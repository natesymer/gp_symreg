(ns darwin.problems.pathfinding
  (:require [darwin.gp.selection :as selection])
  (:require [darwin.gp.crossover :as crossover])
  (:require [darwin.gp.mutation :as mutation])
  (:require [darwin.problems.pathfindingtests.machine :as testing])
  (:gen-class))

(def instructions
  '(new_angle
    set_speed
    new_cond_moves
    set_angle_target
    loop_moves
    while_moves
    ;move-dup
    ;integer-dup
    ;integer-frombool
    ;boolean-and
    ;boolean-or
    ;exec-if
    ;exec-dup
    ))

(defn most-novel
  "Takes an individual and returns a tuple. Gets the max point"
  [indiv]
  (let [novelty (:novelty indiv)]
    (if (= 1 (count novelty))
      (first novelty)
      (reduce  ;take individual, get most novel
        #(if (and (> (first %2) (first %1))
                  (> (second %2) (second %1)))
           %2
           %1)
        novelty))))

(def novelty-archive (ref '()))
(defn add-novel
  [machine-out]
  (dosync
    (alter novelty-archive conj (:novelty machine-out))
    machine-out))

;; FIXME: NULL POINTER EXCEPTION IS IN HERE!
(defn novelty-selection
  "select novel individual by comparing all individuals ending locations against the ending locations
  in the archive"
  [population]
;  (dosync
    (let [;plus-archive (concat (map most-novel population) (deref novelty-archive))
          plus-archive (map most-novel population)
          archive-size (count plus-archive)
          average-x (/ (apply +' (map first plus-archive)) archive-size)
          average-y (/ (apply +' (map second plus-archive)) archive-size)
          average-size (/ (apply +' (map #(nth % 2) plus-archive)) archive-size)
          calc-distance (fn [pt]
                          ;; In the subtraction shits is where the bug is encountered
                          (let [xdif (- average-x (first pt))
                                ydif (- average-y (second pt))
                                ret (Math/sqrt (+ (* xdif xdif) (* ydif ydif)))]
                            ret))
          reduce-f (fn [longest-indiv next-indiv]
                     (if (and (> (calc-distance (most-novel next-indiv))
                                 (calc-distance (most-novel longest-indiv))) ;; TRYME: calc this by (/ distance program size) to balance out bloating
                              (> average-size (nth (most-novel next-indiv) 2)))
                       next-indiv
                       longest-indiv))]
          ;find longest distance from average (includes archived anomolies)
;          (add-novel
            (reduce reduce-f population))) ; )

(def test-criteria
  ;constant multiples for each attribute of a machine run
  {:distance-from-target 0.8
   :total-crashes 0.5
   :moves-made 2})

(defn gradate-error
  [err]
  (let [logerr (Math/log err)]
    (Math/floor (* (/ logerr 10) logerr))))

(defn test-on-map
  "take movestack and location of map and run test"
  [map]
  (let [maploaded (testing/load-obstacle-list map)]
    (fn [movestack]
      (let [testresult (testing/test-instructions-list movestack maploaded test-criteria)
            dist-from-target (:distance-from-target test-criteria)
            total-crashes (:total-crashes test-criteria)
            moves-made (:moves-made test-criteria)

            dist-to-target (:dist-to-target testresult)
            end-loc (:end-loc testresult)
            num-crash (:num-crash testresult)
            instr-total (:instr-total testresult)
            fitness (bigint (+' (*' dist-from-target dist-to-target)
                                (*' total-crashes num-crash)
                            ))]
;        (println testresult)
      {:error (if (zero? (gradate-error fitness)) 0 fitness)
       :novelty end-loc}))))

;TODO: Generalize testcases field to problem. (Testcases currently lists map file location.  This is then loaded
; into the machine and run against an individual.  This generates an error map.)

(defn set-exit-states-to-move
  [ind]
  (assoc ind :exit-states (map #(:move %) (:exit-states ind))))

(def configuration
  {:genomic true
   :instructions instructions
   :literals (range 180)
   :inputses '(())
   :program-arity 0
   :testcases (list
                (test-on-map "data/obsfiles/test1.txt")
                (test-on-map "data/obsfiles/test2.txt"))
                ;(test-on-map "data/obsfiles/test3.txt")) ;; This should be a list of functions which take a final push state and returns a fitness.
   :behavioral-diversity #(testing/calculate-behavior-div % 5) ; TODO: play with the frame
   :max-generations 500
   :population-size 200
   :initial-percent-literals 0.4
   :max-initial-program-size 100
   :min-initial-program-size 50
   :evolution-config {:selection (list
                                   [50 novelty-selection]
                                   [50 #(selection/lexicase-selection % 30)])
                      :crossover crossover/age-hotness-crossover
                      :mutation #(mutation/refresh-youngest-genome %1 3 %3)
                      :percentages '([20 :crossover]
                                     [20 :deletion]
                                     [20 :addition]
                                     [20 :mutation]
                                     [20 :copy])
                      :deletion-percent 7
                      :addition-percent 7
                      :mutation-percent 7
                      :keep-test-attribute :novelty
                      :individual-transform #(set-exit-states-to-move %)
                      }})
