; Copied from https://github.com/steenhansen/clojure-text-diff

(comment

  ; How to use
  (ns my-name-space
    (:require [clojure.test :refer [is]])
    (:require [text-diff :refer [are-vars-eq]]))

  (let [value-1 "<same>DIFFERENT</same>"
        value-2 "<same>different</same>"
        [diff-1 diff-2] (are-vars-eq value-1 value-2)]
    (is (= diff-1 diff-2)))
      ;
      ; |START|"<same>"
      ; |DIFF1|"e>DIFFERENT</"
      ; |DIFF2|"e>different</"
      ; |  END|"</same>"
      ;
      ; expected (= diff-1 diff-2)
      ; actual (not (= "DIFFERENT" "different"))
      ; false

  ; Examples
      ; text-diff-examples> (diff-examples)

  ; Testing
      ; test-text-diff> (do-tests)
  )

(ns text-diff
  (:require [clojure.test :refer [is]]))

(def DEFAULT-PRE-POST-SIZE 2)
(def DEFAULT-ELLIPSIS-SIZE 30)
(def ONE-CHAR-QUOTED-STR-SIZE 3)

(def TEXT-N-EOL  "\\\\n")
(def REAL-ANSI-COLOR  #"\u001b")
(def TEXT-ANSI-COLOR  "\\\\u001b")
(def MATCH-ANSI-COLOR #"\\u001b\[\d+m")

(def START-OR-END-QUOTES  #"^\"|\"$")
(def END-QUOTE  #"\"$")
(def START-QUOTE  #"^\"")

(def END-DOT-TYPE  #"\.[^\.]*$")

(def T-NO-ERR ["" "" "" "" ""])

(def REPL-ERROR-PRINT "REPL-ERROR-PRINT")
(def NO-REPL-PRINT "NO-REPL-PRINT")

(def ANSI-RED "\u001b[31m")
(def ANSI-GREEN "\u001b[32m")
(def ANSI-YELLOW "\u001b[33m")
(def ANSI-BLUE "\u001b[34m")
(def ANSI-MAGENTA "\u001b[35m")
(def ANSI-CYAN "\u001b[36m")
(def ANSI-RESET "\u001b[0m")

(def DEFAULT-CHAR-COLORS
  (hash-map :ERROR-COLOR ANSI-RED            ; dissimilar text
            :LEGEND-COLOR ANSI-BLUE          ; start/diff1/diff2/end
            :RESET-COLOR ANSI-RESET
            :SAME-COLOR ANSI-GREEN           ; similar text
            :TYPE-COLOR ANSI-YELLOW          ; String Long Map Set List
            :WHITESPACE-COLOR ANSI-MAGENTA)) ; tabs eols
(def T-NO-ANSI-COLORS {})

(def FUNCTION-REPRESENTATION "A_Function")
(def NIL-REPRESENTATION "A_Nil")
(def STRING-DELIM "\"")

(def NIL-TYPE-STR "Nil")

(def VARIETY-RATIOS-INTEGERS   "VARIETY-RATIOS-INTEGERS")
(def VARIETY-FLOATS  "VARIETY-FLOATS")

(def VARIETY-BIGDEC  "VARIETY-BIGDEC")
(def VARIETY-STRING "VARIETY-STRING")

(def VARIETY-MAP "VARIETY-MAP")
(def VARIETY-SET "VARIETY-SET")
(def VARIETY-VECTOR "VARIETY-VECTOR")
(def VARIETY-LIST "VARIETY-LIST")
(def VARIETY-BOOLEAN "VARIETY-BOOLEAN")

(def VARIETY-FUNCTION "VARIETY-FUNCTION")
(def VARIETY-NIL "VARIETY-NIL")
(def VARIETY-OTHER "VARIETY-OTHER")

(comment
  (get-colors {:RESET-COLOR 42})
  ; ["" "" 42 "" "" ""]
  )
(defn get-colors [char-colors]
  (let [error-col (get char-colors :ERROR-COLOR "")
        legend-col (get char-colors :LEGEND-COLOR "")
        reset-col (get char-colors :RESET-COLOR "")
        same-col (get char-colors :SAME-COLOR "")
        type-col (get char-colors :TYPE-COLOR "")
        whitespace-col (get char-colors :WHITESPACE-COLOR "")
        the-colors [error-col legend-col reset-col same-col type-col whitespace-col]]
    the-colors))

(comment
  (n-eoln " 1\r\n2\r3 ")
  ; "1\n2\n3"
  )
(defn n-eoln [padded-result]
  (let [trimmed-result (clojure.string/trim padded-result)
        rn-result (clojure.string/replace trimmed-result #"\r\n" "\n")
        n-result (clojure.string/replace rn-result  #"\r" "\n")]
    n-result))

(comment
  (find-start "_" ["1" "1"])
  ; "_1"

  (find-start "_" ["1" "2"])
  ; "_"
  )
(defn find-start [accum char-tuple]
  (let [[char-1 char-2] char-tuple]
    (if (= char-1 char-2)
      (str accum char-1)
      (reduced accum))))

(comment
  (common-start "123" "129")
  ; "12"
  )
(defn common-start [str-1 str-2]
  (let [chars-1 (to-array str-1)
        chars-2 (to-array str-2)
        char-tuples (map vector chars-1 chars-2)
        my-start (reduce find-start "" char-tuples)]
    (if (= my-start STRING-DELIM)
      ""
      my-start)))

(comment
  (common-end "123" "423")
  ; "23"
  )
(defn common-end [str-1 str-2]
  (let [chars-1 (clojure.string/reverse str-1)
        chars-2 (clojure.string/reverse str-2)
        char-tuples (map vector chars-1 chars-2)
        reversed-end (reduce find-start "" char-tuples)
        ordered-end (clojure.string/reverse reversed-end)
        ordered-str (apply str ordered-end)]
    (if (= ordered-str STRING-DELIM)
      ""
      ordered-str)))

(comment
  (get-middle "1234567890" 3 5)
  ;"45"
  )

(defn get-middle [str-text start-length end-pos]
  (if (or (= end-pos 0) (> start-length end-pos))
    ""
    (subs str-text start-length end-pos)))

(comment
  (plain-difference "123edfg" "987edfg" "abcd" "edfg")
  ; ["123" "987"]

  (plain-difference "" "1" "1" "")
  ; ["" "1"]
  )
(defn plain-difference [str-1 str-2  end-same]
  (let [end-length (count end-same)
        length-1 (count str-1)
        end-pos-1 (- length-1 end-length)
        length-2 (count str-2)
        end-pos-2 (- length-2 end-length)
        middle-1 (get-middle str-1 0 end-pos-1)
        middle-2 (get-middle str-2 0 end-pos-2)
        middle-tuple (vector middle-1 middle-2)]
    middle-tuple))

(comment
  (strip-blanks "  in center ok   ")
  ; "in center ok"
  )
(defn strip-blanks [with-blanks]
  (let [no-start  (clojure.string/replace with-blanks #"(^ *)" "")
        no-start-end  (clojure.string/replace no-start #"( *$)" "")]
    no-start-end))

(comment
  (plain-diff-prefix "abc" 2)
  ; "bc"
  )
(defn plain-diff-prefix [front-blanked num-chars]
  (let [front-same (strip-blanks front-blanked)
        front-quote-length (count front-same)]
    (if (> num-chars front-quote-length)
      (let [my-start-quoted (subs front-same 0 front-quote-length)]
        my-start-quoted)
      (let [start-sub (- front-quote-length num-chars)
            end-sub  front-quote-length
            my-start-quoted (subs front-same start-sub end-sub)]
        my-start-quoted))))

(comment
  (plain-diff-postfix "abc" 2)
  ; "ab"
  )
(defn plain-diff-postfix [back-blanked num-chars]
  (let [back-same (strip-blanks back-blanked)
        end-quote-length (count back-same)]
    (if (>  num-chars end-quote-length)
      (let [my-end-quoted (subs back-same 0 end-quote-length)]
        my-end-quoted)
      (let [my-end-quoted (subs back-same 0  num-chars)]
        my-end-quoted))))

(comment
  (shrink-middle "abcdefghijklmnopqrstuvwxyz" 5)
  ; "abcde ... vwxyz"
  )
(defn shrink-middle [long-html size-partition]
  (let [length-html (count long-html)
        start-length size-partition
        end-length size-partition
        middle-length (- length-html start-length end-length)]
    (if (< middle-length 1)
      long-html
      (let [start-html (subs long-html 0 size-partition)
            middle-html " ... "
            end-html (subs long-html (- length-html size-partition) length-html)
            short-html (str start-html middle-html end-html)]
        short-html))))

(comment
  (text-matches "1234567890abcdefghijkl0987654321" "1234567890zyxwvutsrqpon0987654321" 4)
  ; ["1234 ... 7890" "abcd ... ijkl" "zyxw ... qpon" "0987 ... 4321"]
  )
(defn text-matches [str-value-1 str-value-2 prefix-size]
  (let [no-quotes-1 (clojure.string/replace str-value-1 START-OR-END-QUOTES "")
        no-quotes-2 (clojure.string/replace str-value-2 START-OR-END-QUOTES "")
        start-same (common-start no-quotes-1 no-quotes-2)
        no-quotes-11 (subs no-quotes-1 (count start-same))
        no-quotes-22 (subs no-quotes-2 (count start-same))
        end-same (common-end no-quotes-11 no-quotes-22)
        [middle-11 middle-22] (plain-difference no-quotes-11 no-quotes-22 end-same)
        start-short (shrink-middle start-same prefix-size)
        middle-1-short (shrink-middle middle-11 prefix-size)
        middle-2-short (shrink-middle middle-22 prefix-size)
        end-short (shrink-middle end-same prefix-size)
        start-middle2-end (vector start-short middle-1-short middle-2-short end-short)]
    start-middle2-end))

(comment
  (display-rnt-slashes "__\t__\r\n__\r__\n__" "" "")
  ; "__\\t__\\r\\n__\\r__\\n__"
  )
(defn display-rnt-slashes [whitespace-str current-color whitespace-col]
  (let [tab-show (str whitespace-col "\\\\t" current-color)
        rn-show (str whitespace-col  "\\\\r\\\\n" current-color)
        r-show (str whitespace-col  "\\\\r" current-color)
        n-show (str whitespace-col  "\\\\n" current-color)
        no-tabs (clojure.string/replace whitespace-str #"\t" tab-show)
        no-rns (clojure.string/replace no-tabs #"\r\n" rn-show)
        no-rs (clojure.string/replace no-rns #"\r" r-show)
        no-ns (clojure.string/replace no-rs #"\n" n-show)]
    no-ns))

(comment
  (println (colored-diff-prefix "ab\tc" 3 DEFAULT-CHAR-COLORS))
  ;  b\tc   green "b", magenta "\t" and green "c"
  )
(defn colored-diff-prefix [start-same pre-post-size char-colors]
  (let [[_error-col _legend-col _reset-coll same-col _type-col whitespace-col] (get-colors char-colors)
        front-sandwich (plain-diff-prefix start-same pre-post-size)
        front-sand-show (display-rnt-slashes front-sandwich same-col whitespace-col)]
    front-sand-show))

(comment
  (println (colored-diff-postfix "a\tbc" 3 DEFAULT-CHAR-COLORS))
  ; a\tb    green "a", magenta "\t" and green "b"
  )
(defn colored-diff-postfix [end-same pre-post-size char-colors]
  (let [[_error-col _legend-col _reset-coll same-col _type-col whitespace-col] (get-colors char-colors)
        end-sandwich (plain-diff-postfix end-same pre-post-size)
        end-sand-show (display-rnt-slashes end-sandwich same-col whitespace-col)]
    end-sand-show))

(comment
  (strip-quotes "\"  \"  \"")
  ; "  \"  "
  )
(defn strip-quotes [quoted-str]
  (let [trimmed-str (clojure.string/trim quoted-str)
        no-end-quote (clojure.string/replace trimmed-str END-QUOTE "")
        no-start-quote (clojure.string/replace no-end-quote START-QUOTE "")]
    no-start-quote))

(comment
  (build-pre-post "123" "789" {} "s" "e")
  ; ["s123e" "s789e"]
  )
(defn build-pre-post [start-same end-same char-colors delim-start delim-end]
  (let [start-no-quote (strip-quotes start-same)
        end-no-quote (strip-quotes end-same)
        delim-start-used (if (= "" start-no-quote) "" delim-start)
        delim-end-used (if (= "" end-no-quote) "" delim-end)
        [_error-col _legend-col _reset-coll same-col _type-col whitespace-col] (get-colors char-colors)
        start-show (str delim-start-used (display-rnt-slashes start-no-quote same-col whitespace-col) delim-end-used)
        end-show (str delim-start-used (display-rnt-slashes end-no-quote same-col whitespace-col) delim-end-used)]
    [start-show end-show]))

(comment
  (bool-str-int VARIETY-STRING "abcdefghijklmnopqrstuvwxyz" "abc...xyz")
  ; "abc...xyz"
  (bool-str-int VARIETY-BOOLEAN true "tru")
  ; true
  )
(defn bool-str-int [variety str-value middle-plain]
  (if (= variety VARIETY-RATIOS-INTEGERS)
    (read-string str-value)
    (if (= variety VARIETY-BOOLEAN)
      str-value
      middle-plain)))

(defn internal-divider-char  [variety-type]
  (if (or (= variety-type VARIETY-STRING)
          (= variety-type VARIETY-FLOATS)
          (= variety-type VARIETY-BIGDEC)
          (= variety-type VARIETY-RATIOS-INTEGERS))
    ""
    " "))

(defn start-end-delimiter [variety-type]
  (if (= variety-type VARIETY-STRING)
    [STRING-DELIM STRING-DELIM]
    (if (= variety-type VARIETY-BIGDEC)
      ["" "M"]
      ["" ""])))

(comment
  (color-differences "123" "987" 2 30 {} VARIETY-STRING)
  ; ["\"123\"" "\"987\"" "" ""]
  )
(defn color-differences [str-value-1 str-value-2 pre-post-size ellipsis-size char-colors  vars-variety]
  (let [[start-same middle-1-plain middle-2-plain end-same] (text-matches str-value-1 str-value-2 ellipsis-size)
        [delim-start delim-end] (start-end-delimiter vars-variety)
        internal-space (internal-divider-char vars-variety)
        [error-col _legend-col _reset-coll same-col _type-col whitespace-col] (get-colors char-colors)
        front-sand-show (colored-diff-prefix start-same pre-post-size char-colors)
        end-sand-show (colored-diff-postfix end-same pre-post-size char-colors)
        middle-1-show (display-rnt-slashes middle-1-plain error-col whitespace-col)
        middle-2-show (display-rnt-slashes middle-2-plain error-col whitespace-col)
        extra-middle-1  (str same-col delim-start front-sand-show internal-space error-col
                             middle-1-show internal-space same-col end-sand-show delim-end)
        extra-middle-2  (str same-col delim-start front-sand-show internal-space error-col
                             middle-2-show internal-space same-col end-sand-show delim-end)
        [start-show str-end-show] (build-pre-post start-same end-same char-colors delim-start delim-end)
        color-diffs [extra-middle-1 extra-middle-2 start-show str-end-show]]
    color-diffs))

(comment
  (char-difference "aaaXbbb" "aaaYbbb" 1 2 {} VARIETY-STRING)
  ; ["\"aaa\"" "\"aXb\"" "\"aYb\"" "\"bbb\"" "X" "Y"]
  (char-difference "abcd" "axyd" 2 30 {} VARIETY-STRING)
 ;["\"a\"" "\"abcd\"" "\"axyd\"" "\"d\"" "bc" "xy"]
  )
(defn char-difference [str-value-1 str-value-2 pre-post-size ellipsis-size char-colors  vars-variety]
  (let [[_start-same middle-1-plain middle-2-plain _end-same] (text-matches str-value-1 str-value-2 ellipsis-size)
        [error-col _legend-col _reset-coll same-col _type-col whitespace-col] (get-colors char-colors)
        [extra-middle-1 extra-middle-2 start-show str-end-show] (color-differences str-value-1 str-value-2 pre-post-size ellipsis-size char-colors  vars-variety)
        end-show (if (= vars-variety VARIETY-BOOLEAN) "" str-end-show)
        middle-1-short (bool-str-int vars-variety str-value-1 middle-1-plain)
        middle-2-short (bool-str-int vars-variety str-value-2 middle-2-plain)
        char-all (vector start-show extra-middle-1 extra-middle-2 end-show middle-1-short middle-2-short)]
    char-all))

(comment
  (colored-differences "abc" "def" "|START|abc" "|DIFF1|bcXde" "|DIFF2|bcYde" "|  END|def" "X" "Y")
  ; ["X" "Y" "|START|abc|DIFF1|bcXde|DIFF2|bcYde|  END|def"]
  )
(defn colored-differences [start-show end-show front-line middle-1-line middle-2-line back-line middle-1-plain middle-2-plain]
  (let  [front-empty (= 0 (count start-show))
         back-empty (= 0 (count end-show))]
    (if (and front-empty back-empty)
      [middle-1-plain middle-2-plain (str middle-1-line middle-2-line)]
      (if (and front-empty (not back-empty))
        [middle-1-plain middle-2-plain (str  middle-1-line middle-2-line back-line)]
        (if (and (not front-empty) back-empty)
          [middle-1-plain middle-2-plain (str front-line middle-1-line middle-2-line)]
          [middle-1-plain middle-2-plain (str front-line middle-1-line middle-2-line back-line)])))))

(comment
  (string-to-str "string")
  ; "\"string\" "
  )
(defn string-to-str [a-string]
  (str STRING-DELIM a-string STRING-DELIM " "))

(declare variable-to-str)

(comment
  (function-to-str (defn a-func [para] (+ para 1)))
  ; "A_Function"
  )
(defn function-to-str [a-function]
  (str FUNCTION-REPRESENTATION))            ; we just acknowledge functions, we cannot tell values

(comment
  (vector-to-str [1 "two" {:three '(4 ["5"])}])
  ; " [1  \"two\"  { :three  '(4 [\"5\"]) }  ]"
  )
(defn vector-to-str [a-vector]
  (let [vector-members (reduce variable-to-str "" a-vector)]
    (str " [" vector-members " ]")))

(comment
  (map-to-str {:c "three" :b {:two '(4 ["5"])} :a 1})
  ;"  { :a 1  :b  { :two  '(4 [\"5\"]) }  :c \"three\" } "
  )
(defn map-to-str [a-map]
  (let [map-sorted (into (sorted-map) (sort-by first (seq a-map)))
        map-members (reduce variable-to-str "" map-sorted)]
    (str " {" map-members "} ")))

(comment
  (set-to-str #{1 7 3})
  ;"  { :a 1  :b  { :two  '(4 [\"5\"]) }  :c \"three\" } "
  )
(defn set-to-str [a-set]
  (let [set-sorted (sort a-set)
        set-str (str set-sorted)
        no-start-list (clojure.string/replace set-str #"\(" "")
        no-end-list (clojure.string/replace no-start-list #"\)" "")]
    (str " #{" no-end-list "} ")))

(comment
  (list-to-str '(1  "two" '(3 ["4"])))
  ; " '(1 \"two\" (quote (3 [\"4\"]))) "
  )
(defn list-to-str [a-list]
  (str " '" a-list " "))

(comment
  (entry-to-str (first {:a "bc"}))
  ; " :a \"bc\" "
  )
(defn entry-to-str [an-entry]
  (if (string? (val an-entry))
    (str " " (key an-entry) " " STRING-DELIM (val an-entry) STRING-DELIM " ")
    (variable-to-str (str " " (key an-entry) " ") (val an-entry))))

(comment
  (string-to-str "17")
  ; "\"17\""
  )
(defn boolean-to-str [a-boolean]
  (if a-boolean
    "true"
    "false"))

(comment
  (other-to-str 17)
  ; "17 "
  )
(defn other-to-str [a-variable]
  (str a-variable " "))

(comment
  (nil-to-str nil)
  ; "A_Nil "
  )
(defn nil-to-str [a-nil]
  (str NIL-REPRESENTATION " "))

(comment
  (let [bfunc (fn [b] (+ b b))]
    (is-a-function? bfunc))
  ; true
  )
(defn is-a-function? [maybe-func]
  (if (string? maybe-func)
    false
    (if (fn? maybe-func)
      true
      (try
        (if (-> maybe-func symbol resolve deref ifn?)
          true
          false)
        (catch Exception e false)))))

(comment
  (variable-to-str "_start_" {:a [1 "j" 3]})
    ;"_start_ { :a  [1 \"j\" 3  ]} "
  )
(defn variable-to-str [accum-str the-variable]
  (if (map-entry? the-variable)
    (str accum-str (entry-to-str the-variable))
    (if (is-a-function? the-variable)
      (str accum-str (function-to-str the-variable))
      (if (vector? the-variable)
        (str accum-str (vector-to-str the-variable))
        (if (map? the-variable)
          (str accum-str (map-to-str the-variable))
          (if (set? the-variable)
            (str accum-str (set-to-str the-variable))
            (if (list? the-variable)
              (str accum-str (list-to-str the-variable))
              (if (string? the-variable)
                (str accum-str (string-to-str the-variable))
                (if (boolean? the-variable)
                  (str accum-str (boolean-to-str the-variable))
                  (if (nil? the-variable)
                    (str accum-str (nil-to-str the-variable))
                    (str accum-str (other-to-str the-variable))))))))))))

(comment
  (var-to-str {:a [1 "j" 3]})
    ;" { :a  [1 \"j\" 3  ]} "
  )
(defn var-to-str [the-variable]
  (let [str-var (variable-to-str "" the-variable)]
    str-var))

(comment
  (var-variety "string")
  ; "VARIETY-STRING"
  )
(defn var-variety [a-variable]
  (if (or (ratio? a-variable) (integer? a-variable))
    VARIETY-RATIOS-INTEGERS
    (if (float? a-variable)
      VARIETY-FLOATS
      (if (string? a-variable)
        VARIETY-STRING
        (if (boolean? a-variable)
          VARIETY-BOOLEAN
          (if (decimal? a-variable)
            VARIETY-BIGDEC
            (if (map? a-variable)
              VARIETY-MAP
              (if (vector? a-variable)
                VARIETY-VECTOR
                (if (list? a-variable)
                  VARIETY-LIST
                  (if (is-a-function? a-variable)
                    VARIETY-FUNCTION
                    (if (nil? a-variable)
                      VARIETY-NIL
                      VARIETY-OTHER)))))))))))

(comment
  (similar-type-diff "123456+a+567890" "123456+b+567890"  {} VARIETY-STRING  1 3)
  ;["a" "b" "\n|START|\"123 ... 56+\"\n|DIFF1|\"+a+\"\n|DIFF2|\"+b+\"\n|  END|\"+56 ... 890\""]
  )
(defn similar-type-diff [value-1 value-2 char-colors vars-variety pre-post-size ellipsis-size]
  (let  [str-value-1  (clojure.string/trim (var-to-str value-1))
         str-value-2  (clojure.string/trim (var-to-str value-2))]
    (if (= str-value-1 str-value-2)
      ["" "" ""]
      (let [[start-show middle-1-show middle-2-show end-show middle-1-plain middle-2-plain]
            (char-difference str-value-1 str-value-2 pre-post-size ellipsis-size char-colors vars-variety)
            [error-col legend-col reset-col same-col] (get-colors char-colors)
            middle-1-color (str error-col middle-1-show reset-col)
            middle-2-color (str error-col middle-2-show reset-col)
            middle-1-bool (str error-col middle-1-plain reset-col)
            middle-2-bool (str error-col middle-2-plain reset-col)
            new-middle-1 (if (= vars-variety VARIETY-BOOLEAN) middle-1-bool middle-1-color)
            new-middle-2 (if (= vars-variety VARIETY-BOOLEAN) middle-2-bool middle-2-color)
            front-line    (str legend-col "\n|START|" same-col start-show reset-col)
            middle-1-line (str legend-col "\n|DIFF1|" new-middle-1)
            middle-2-line (str legend-col "\n|DIFF2|" new-middle-2)
            back-line     (str legend-col "\n|  END|" same-col end-show  reset-col)
            colored-output (colored-differences start-show end-show front-line middle-1-line middle-2-line
                                                back-line middle-1-plain middle-2-plain)]
        colored-output))))

(comment
  (prefix-duplicate-sizes 10 1)
  ; [10 10]
  )
(defn prefix-duplicate-sizes [prefix-size duplicate-size]
  (if (> prefix-size duplicate-size)
    [prefix-size prefix-size]
    [prefix-size duplicate-size]))

(comment
  (short-type 1/2)
  ; "Ratio "
  )
(defn short-type [a-var]
  (if (nil? a-var)
    NIL-TYPE-STR
    (let [long-type (str (type a-var))
          dot-type (re-find END-DOT-TYPE long-type)
          short-type (subs dot-type 1)]
      short-type)))

(comment
  (conflicting-type-diff "1" 1 {})
  ;["1" 1 "\n|diff1|\"1\" String\n|diff2| 1  Long"]
  )
(defn conflicting-type-diff [value-1 value-2 char-colors]
  (let [delim-1 (if (string? value-1) STRING-DELIM " ")
        delim-2 (if (string? value-2) STRING-DELIM " ")
        [error-col legend-col reset-col _same-col type-col] (get-colors char-colors)
        short-type-1 (short-type value-1)
        short-type-2 (short-type value-2)
        diff-1 (str legend-col "\n|diff1|" error-col delim-1 value-1 delim-1 " " type-col short-type-1 reset-col)
        diff-2 (str legend-col "\n|diff2|" error-col delim-2 value-2 delim-2 " " type-col short-type-2 reset-col)]
    [value-1 value-2 (str diff-1 diff-2)]))

(comment
  (pre-duplicate-post "abcdefghijXklmnopqrst" "abcdefghijYklmnopqrst" 1 3 {})
  ;["X" "Y" "\n|START|\"abc ... hij\"\n|DIFF1|\"jXk\"\n|DIFF2|\"jYk\"\n|  END|\"klm ... rst\""]
  )
(defn pre-duplicate-post
  ([value-1 value-2]
   (pre-duplicate-post value-1 value-2  DEFAULT-PRE-POST-SIZE DEFAULT-ELLIPSIS-SIZE DEFAULT-CHAR-COLORS))
  ([value-1 value-2 pre-post-size]
   (pre-duplicate-post value-1 value-2  pre-post-size         DEFAULT-ELLIPSIS-SIZE DEFAULT-CHAR-COLORS))
  ([value-1 value-2 pre-post-size ellipsis-size]
   (pre-duplicate-post value-1 value-2  pre-post-size         ellipsis-size         DEFAULT-CHAR-COLORS))
  ([value-1 value-2 pre-post-size ellipsis-size char-colors]
   (let [[prefix-size duplicate-size] (prefix-duplicate-sizes pre-post-size ellipsis-size)
         variety-1 (var-variety value-1)
         variety-2 (var-variety value-2)]
     (if (= variety-1 variety-2)
       (similar-type-diff     value-1 value-2 char-colors variety-1 prefix-size duplicate-size)
       (conflicting-type-diff value-1 value-2 char-colors)))))

(comment
  (let [[_ _ _ _ _] (are-vars-eq "1\t7" "1\"7")])
  ;|START|"1"
  ;|DIFF1|"1\t7"
  ;|DIFF2|"1"7"
  ;|  END|"7"

  (let [_ (are-vars-eq "123456789" 123455789)])
  ;|diff1|"123456789" String
  ;|diff2| 123455789  Long

  (are-vars-eq ".X." ".Y.")
  ;|START|"."
  ;|DIFF1|".X."
  ;|DIFF2|".Y."
  ;|  END|"."
  ;["X"
  ; "Y"
  ; "\\u001b[34m\\n|START|\\u001b[32m\".\"\\u001b[0m\\u001b[34m\\n|DIFF1|\\u001b[31m\\u001b[32m\".\\u001b[31mX\\u001b[32m.\"\\u001b[0m\\u001b[34m\\n|DIFF2|\\u001b[31m\\u001b[32m\".\\u001b[31mY\\u001b[32m.\"\\u001b[0m\\u001b[34m\\n|  END|\\u001b[32m\".\"\\u001b[0m"
  ; "\\n|START|\".\"\\n|DIFF1|\".X.\"\\n|DIFF2|\".Y.\"\\n|  END|\".\""
  ; "␛[34m\n|START|␛[32m\".\"␛[0m␛[34m\n|DIFF1|␛[31m␛[32m\".␛[31mX␛[32m.\"␛[0m␛[34m\n|DIFF2|␛[31m␛[32m\".␛[31mY␛[32m.\"␛[0m␛[34m\n|  END|␛[32m\".\"␛[0m"
  ;]
  )
(defn are-vars-eq
  ([value-1 value-2]
   (are-vars-eq value-1 value-2 REPL-ERROR-PRINT DEFAULT-PRE-POST-SIZE DEFAULT-ELLIPSIS-SIZE DEFAULT-CHAR-COLORS))
  ([value-1 value-2 repl-print]
   (are-vars-eq value-1 value-2 repl-print       DEFAULT-PRE-POST-SIZE DEFAULT-ELLIPSIS-SIZE DEFAULT-CHAR-COLORS))
  ([value-1 value-2 repl-print pre-post-size]
   (are-vars-eq value-1 value-2 repl-print       pre-post-size         DEFAULT-ELLIPSIS-SIZE DEFAULT-CHAR-COLORS))
  ([value-1 value-2 repl-print pre-post-size ellipsis-size]
   (are-vars-eq value-1 value-2 repl-print       pre-post-size         ellipsis-size         DEFAULT-CHAR-COLORS))
  ([value-1 value-2 repl-print pre-post-size ellipsis-size char-colors]
   (let [print-errors? (not (= repl-print NO-REPL-PRINT))
         [plain-1-diff plain-2-diff color-diff] (pre-duplicate-post value-1 value-2 pre-post-size ellipsis-size char-colors)
         no-eols-mess (clojure.string/replace color-diff #"\n" TEXT-N-EOL)
         no-escape-mess (clojure.string/replace no-eols-mess REAL-ANSI-COLOR TEXT-ANSI-COLOR)
         no-ansi-mess (clojure.string/replace no-escape-mess MATCH-ANSI-COLOR "")
         values-not-equal? (not (= value-1 value-2))]
     (if (and print-errors? values-not-equal?)
       (println color-diff))
     [plain-1-diff plain-2-diff  no-escape-mess no-ansi-mess color-diff])))

