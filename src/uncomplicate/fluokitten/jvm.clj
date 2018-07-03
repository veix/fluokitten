;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:doc "Extends Clojure core with the implementations of
fluokitten protocols. Defines curried functions. Need to be
used or required for enabling Fluokitten on projects that
run on JVM platform."
      :author "Dragan Djuric"}
    uncomplicate.fluokitten.jvm
  (:require [uncomplicate.fluokitten
             [protocols :refer :all]
             [algo :refer :all]
             [utils :refer [split-last with-context]]])
  (:import clojure.lang.IFn))

(set! *warn-on-reflection* true)

;;======== Set appropriate platform specific vars in algo. ======

(in-ns 'uncomplicate.fluokitten.utils)

(defn deref?
  "Checks whether x is dereferencible. On JVM it checks if it is
   an instance of clojure.lang.IDeref, on other platforms it may
   be implemented in a similar or a completely different way."
  [x]
  (clojure.core/instance? clojure.lang.IDeref x))

(in-ns 'uncomplicate.fluokitten.algo)

(defn create-mapentry
  "Creates a map entry with the supplied key and value. On JVM it
   creates an instance of clojure.lang.MapEntry."
  [k v]
  (clojure.lang.MapEntry. k v))

(in-ns 'uncomplicate.fluokitten.jvm)

;;======== Clojure JVM-specific Extensions =====================

(extend-collection clojure.lang.IPersistentCollection)

(extend-seq clojure.lang.ASeq)

(extend-lazyseq clojure.lang.LazySeq)

(extend-vector clojure.lang.APersistentVector)

(extend-list clojure.lang.IPersistentList)

(extend-list clojure.lang.PersistentList)

(extend-set clojure.lang.APersistentSet)

(extend-map clojure.lang.APersistentMap)

(extend-mapentry clojure.lang.MapEntry)

(extend-keyword clojure.lang.Keyword)

(extend-function clojure.lang.AFunction)

(extend-atom clojure.lang.Atom)

(extend-ref clojure.lang.Ref)

(extend-volatile clojure.lang.Volatile)

(extend-ideref clojure.lang.IDeref)

;; ==================== Java Arrays =========================

(defmacro aget* [atype a i]
  `(aget ~(with-meta a {:tag atype}) ~i))

(defmacro aset* [atype a i etype v]
  `(aset ~(with-meta a {:tag atype}) ~i (~etype ~v)))

(defmacro alength* [atype a]
  `(alength ~(with-meta a {:tag atype})))

(defmacro alength** [atype & as]
  `(+ ~@(map #(list `alength* atype %) as)))

(defmacro array-copy [atype etype a b offset n]
  `(dotimes [i# ~n]
     (aset* ~atype ~b (+ ~offset i#) ~etype (~etype (aget* ~atype ~a i#)))))

(defmacro eval-fn [atype f i & as]
  `(~f ~@(map #(list `aget* atype % i) as)))

(defmacro array-fmap
  ([atype etype]
   `(fn
      ([a# f#]
       (array-fmap ~atype ~etype a# f# a#))
      ([a# f# b#]
       (array-fmap ~atype ~etype a# f# a# b#))
      ([a# f# b# c#]
       (array-fmap ~atype ~etype a# f# a# b# c#))
      ([a# f# b# c# d#]
       (array-fmap ~atype ~etype a# f# a# b# c# d#))
      ([a# f# b# c# d# es#]
       (throw (UnsupportedOperationException. "Array fmap! supports up to 4 arrays.")))))
  ([atype etype eclass]
   `(let [fmap-fn# (fn
                     ([a# f# b#]
                      (let [res# (make-array ~eclass (alength* ~atype a#))]
                        (array-fmap ~atype ~etype res# f# a# b#)))
                     ([a# f# b# c#]
                      (let [res# (make-array ~eclass (alength* ~atype a#))]
                        (array-fmap ~atype ~etype res# f# a# b# c#)))
                     ([a# f# b# c# d#]
                      (let [res# (make-array ~eclass (alength* ~atype a#))]
                        (array-fmap ~atype ~etype res# f# a# b# c# d#)))
                     ([a# f# b# c# d# es#]
                      (throw (UnsupportedOperationException. "Array fmap supports up to 4 arrays."))))]
      (fn
        ([a# f#]
         (let [res# (make-array ~eclass (alength* ~atype a#))]
           (array-fmap ~atype ~etype res# f# a#)))
        ([a# f# as#]
         (apply fmap-fn# a# f# as#)))))
  ([atype etype res f & as]
   `(let [len# (alength* ~atype ~res)]
      (dotimes [i# len#]
        (aset* ~atype ~res i# ~etype (eval-fn ~atype ~f i# ~@as)))
      ~res)))

(defmacro array-fapply
  ([atype etype]
   `(fn
      ([a# gs#]
       (array-fapply ~atype ~etype a# gs# a#))
      ([a# gs# b#]
       (array-fapply ~atype ~etype a# gs# a# b#))
      ([a# gs# b# c#]
       (array-fapply ~atype ~etype a# gs# a# b# c#))
      ([a# gs# b# c# d#]
       (array-fapply ~atype ~etype a# gs# a# b# c# d#))
      ([a# gs# b# c# d# es#]
       (throw (UnsupportedOperationException. "Array fapply! supports up to 4 arrays.")))))
  ([atype etype eclass]
   `(let [fapply-fn# (fn
                       ([a# gs# b#]
                        (let [res# (make-array ~eclass (* (count gs#)(alength* ~atype a#)))]
                          (array-fapply ~atype ~etype res# gs# a# b#)))
                       ([a# gs# b# c#]
                        (let [res# (make-array ~eclass (* (count gs#)(alength* ~atype a#)))]
                          (array-fapply ~atype ~etype res# gs# a# b# c#)))
                       ([a# gs# b# c# d#]
                        (let [res# (make-array ~eclass (* (count gs#)(alength* ~atype a#)))]
                          (array-fapply ~atype ~etype res# gs# a# b# c# d#)))
                       ([a# gs# b# c# d# es#]
                        (throw (UnsupportedOperationException. "Array fapply supports up to 4 arrays."))))]
      (fn
        ([a# gs#]
         (let [res# (make-array ~eclass (* (count gs#) (alength* ~atype a#)))]
           (array-fapply ~atype ~etype res# gs# a#)))
        ([a# gs# as#]
         (apply fapply-fn# a# gs# as#)))))
  ([atype etype res gs & as]
   `(let [cntg# (count ~gs)
          lenv# (alength* ~atype ~(first as))]
      (loop [ig# 0 g# (first ~gs) gs# (rest ~gs)]
        (when g#
          (dotimes [i# lenv#]
            (aset* ~atype ~res (+ (* ig# lenv#) i#) ~etype (eval-fn ~atype g# i# ~@as)))
          (recur (inc ig#) (first gs#) (rest gs#))))
      ~res)))

(defmacro array-pure [atype etype eclass]
  `(fn
     ([a# v#]
      (if (instance? IFn v#)
        (into-array Object [v#])
        (let [res# (make-array ~eclass 1)]
          (aset* ~atype res# 0 ~etype v#)
          res#)))
     ([a# v# vs#]
      (if (and (instance? IFn v#) (every? (partial instance? IFn) vs#))
        (into-array Object (cons v# vs#))
        (into-array ~eclass (cons v# vs#))))))

(defmacro array-op [atype etype eclass]
  `(fn [a# & as#]
     (let [res# (make-array ~eclass (reduce + (alength* ~atype a#) (map #(alength* ~atype %) as#))) ]
       (loop [pos# 0 w# a# ws# as#]
         (when w#
           (array-copy ~atype ~etype w# res# pos# (alength* ~atype w#))
           (recur (+ pos# (alength* ~atype w#)) (first ws#) (next ws#))))
       res#)))

(defmacro num-array-foldmap
  ([atype etype]
   `(fn
      ([a# g#]
       (num-array-foldmap ~atype ~etype + g# 0 a#))
      ([a# g# f# init#]
       (num-array-foldmap ~atype ~etype f# g# init# a#))
      ([a# g# f# init# b#]
       (num-array-foldmap ~atype ~etype f# g# init# a# b#))
      ([a# g# f# init# b# c#]
       (num-array-foldmap ~atype ~etype f# g# init# a# b# c#))
      ([a# g# f# init# b# c# d#]
       (num-array-foldmap ~atype ~etype f# g# init# a# b# c# d#))
      ([a# g# f# init# b# c# d# ds#]
       (throw (UnsupportedOperationException. "Array foldmap supports up to 4 arrays.")))))
  ([atype etype f g init & as]
   `(let [n# (alength* ~atype ~(first as))]
      (loop [i# 0 res# (~etype ~init)]
        (if (< i# n#)
          (recur (inc i#) (~etype (~f res# (~etype (eval-fn ~atype ~g i# ~@as)))))
          res#)))))

(defmacro num-array-fold
  ([atype etype]
   `(fn
      ([a#]
       (num-array-fold ~atype ~etype + 0 a#))
      ([a# f# init#]
       (num-array-fold ~atype ~etype f# init# a#))
      ([a# f# init# b#]
       (num-array-foldmap ~atype ~etype f# + init# a# b#))
      ([a# f# init# b# c#]
       (num-array-foldmap ~atype ~etype f# + init# a# b# c#))
      ([a# f# init# b# c# d#]
       (num-array-foldmap ~atype ~etype f# + init# a# b# c# d#))
      ([a# f# init# b# c# d# ds#]
       (throw (UnsupportedOperationException. "Array fold supports up to 4 arrays.")))))
  ([atype etype f init a]
   `(let [n# (alength* ~atype ~a)]
      (loop [i# 0 res# (~etype ~init)]
        (if (< i# n#)
          (recur (inc i#) (~etype (~f res# (aget* ~atype ~a i#))))
          res#)))))

(defn num-array-bind
  ([a g]
   (apply (op a) (map g a)))
  ([a g as]
   (apply (op a) (apply map g a as))))

(defmacro extend-array [t atype etype eclass]
  `(extend ~t
     Functor
     {:fmap (array-fmap ~atype ~etype ~eclass)}
     PseudoFunctor
     {:fmap! (array-fmap ~atype ~etype)}
     Applicative
     {:pure (array-pure ~atype ~etype ~eclass)
      :fapply (array-fapply ~atype ~etype ~eclass)}
     PseudoApplicative
     {:fapply! (array-fapply ~atype ~etype)}
     Magma
     {:op (constantly (array-op ~atype ~etype ~eclass))}
     Monoid
     {:id (fn [a#] (make-array ~eclass 0))}))

(defmacro extend-num-array [t atype etype eclass]
  `(extend ~t
     Monad
     {:join identity
      :bind num-array-bind}
     Foldable
     {:fold (num-array-fold ~atype ~etype)
      :foldmap (num-array-foldmap ~atype ~etype)}))

(extend-array (class (boolean-array 0)) booleans boolean Boolean/TYPE)
(extend-array (class (char-array 0)) chars char Character/TYPE)

(extend-array (class (byte-array 0)) bytes byte Byte/TYPE)
(extend-array (class (short-array 0)) shorts short Short/TYPE)
(extend-array (class (int-array 0)) ints int Integer/TYPE)
(extend-array (class (long-array 0)) longs long Long/TYPE)
(extend-array (class (float-array 0)) floats float Float/TYPE)
(extend-array (class (double-array 0)) doubles double Double/TYPE)

(extend-num-array (class (byte-array 0)) bytes byte Byte/TYPE)
(extend-num-array (class (short-array 0)) shorts short Short/TYPE)
(extend-num-array (class (int-array 0)) ints int Integer/TYPE)
(extend-num-array (class (long-array 0)) longs long Long/TYPE)
(extend-num-array (class (float-array 0)) floats float Float/TYPE)
(extend-num-array (class (double-array 0)) doubles double Double/TYPE)

(let [objects identity]
  (extend-array (class (make-array Object 0)) objects identity Object))

;;===================== CurriedFn ===========================

(defn ^:private arg-counts [f]
  (map alength (map (fn [^java.lang.reflect.Method m]
                      ( .getParameterTypes m))
                    (.getDeclaredMethods
                     ^java.lang.Class (class f)))))

(defn ^:private gen-invoke [^clojure.lang.IFn f arity n]
  (let [args (map #(symbol (str "a" %)) (range arity))]
    `(invoke [~'_ ~@args]
             (if (> ~n ~arity)
               (CurriedFn. (partial ~f ~@args) (- ~n ~arity))
               (.invoke ~f ~@args)))))

(defn ^:private gen-applyto [^clojure.lang.IFn f n]
  `(applyTo [~'_ ~'args]
            (let [as# (- ~n (count ~'args))]
              (if (pos? as#)
                (CurriedFn. (apply partial ~f ~'args) as#)
                (.applyTo ~f ~'args)))))

(defmacro ^:private deftype-curried-fn []
  `(deftype ~'CurriedFn ~'[^clojure.lang.IFn f ^long n]
     java.lang.Object
     ~'(hashCode [_]
         (clojure.lang.Util/hashCombine f (Long/hashCode n)))
     ~'(equals [x y]
         (or
          (identical? x y)
          (and (instance? CurriedFn y)
               (= n (.n ^CurriedFn y)) (identical? f (.f ^CurriedFn y)))))
     ~'(toString [_]
         (format "#curried-function[arity: %d, %s]" n f))
     clojure.lang.IFn
     ~@(map #(gen-invoke 'f % 'n) (range 22))
     ~(gen-applyto 'f 'n)
     java.util.concurrent.Callable
     ~'(call [_]
         (if (pos? n) f (.call f)))
     java.lang.Runnable
     ~'(run [_]
         (if (pos? n) f (.run f)))))

(deftype-curried-fn)

(defmethod print-method CurriedFn
  [cf ^java.io.Writer w]
  (.write w (str cf)))

(defn curried-arity [^CurriedFn cf]
  (.n cf))

(defn curried-curry
  ([cf]
   cf)
  ([^CurriedFn cf ^long arity]
   (if (pos? arity)
     (->CurriedFn (.f cf) arity)
     cf)))

(defn curried-uncurry [^CurriedFn cf]
  (.f cf))

(defn curried-op
  ([]
   identity)
  ([x]
   curried-op)
  ([x y]
   (if (identical? identity y)
     x
     (->CurriedFn (comp x y) (arity y))))
  ([x y z]
   (if (identical? identity z)
     (curried-op x y)
     (curried-op (function-op x y) z)))
  ([x y z w]
   (curried-op (function-op x y z) w))
  ([x y z w ws]
   (let [[f fs] (split-last ws)]
     (curried-op (function-op x y z w fs) f))))

(defn curried-fmap
  ([cf g]
   (curried-op g cf))
  ([cf g chs]
   (->CurriedFn (function-fmap cf g chs) (apply max (arity cf) (map arity chs)))))

(defn curried-fapply
  ([cf cg]
   (->CurriedFn (function-fapply cf cg) 1))
  ([cf cg chs]
   (->CurriedFn (function-fapply cf cg chs) 1)))

(extend CurriedFn
  Curry
  {:arity curried-arity
   :curry curried-curry
   :uncurry curried-uncurry}
  Functor
  {:fmap curried-fmap}
  Applicative
  {:fapply curried-fapply
   :pure function-pure}
  Monad
  {:join function-join
   :bind function-bind}
  Foldable
  {:fold function-fold
   :foldmap default-foldmap}
  Magma
  {:op curried-op}
  Monoid
  {:id (constantly identity)})

;; ====================== Functions as Curry =======================

(defn fn-curry
  ([f]
   (curry f (min 2 ^long (apply max (arg-counts f)))))
  ([f ^long arity]
   (if (pos? arity)
     (->CurriedFn f arity)
     f)))

(extend clojure.lang.IFn
  Curry
  {:arity (constantly 0)
   :curry fn-curry
   :uncurry identity})

;; ====================== Maybe Just ==============================

(deftype Just [v]
  Object
  (hashCode [_]
    (hash v))
  (equals [this that]
    (or (identical? this that)
        (and (instance? Just that)
             (= v (value that)))))
  (toString [_]
    (format "#just[%s]" v))
  Maybe
  (value [_]
    v))

(defmethod print-method Just
  [x ^java.io.Writer w]
  (.write w (str x)))

(defn just-pure [x v]
  (Just. v))

(extend-just Just just-pure)

(defn nil-fapply
  ([_ _] nil)
  ([_ _ _] nil))

(extend nil
  Applicative
  {:pure just-pure
   :fapply nil-fapply})
