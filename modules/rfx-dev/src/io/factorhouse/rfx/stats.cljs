(ns io.factorhouse.rfx.stats
  "Adapted from: https://scicloj.github.io/stats-with-clojure/stats_with_clojure.basic_statistics.html")

(defn median-odd
  [vector]
  (nth vector (/ (count vector) 2)))

(defn median-even
  [vector]
  (let [middle-idx (/ (count vector) 2)]
    (/ (+ (nth vector middle-idx) (nth vector (dec middle-idx))) 2)))

(defn median
  [vector]
  (if (even? (count vector))
    (median-even vector)
    (median-odd vector)))

(defn total
  [vector]
  (apply + vector))

(defn mean
  [vector]
  (/ (total vector) (count vector)))

(defn percentile
  [vector percentile]
  (let [sorted-vector (sort vector)
        idx           (int (* percentile (/ (count sorted-vector) 100)))]
    (nth sorted-vector idx)))

(defn variance
  [vector]
  (let [m (mean vector)]
    (apply + (map #(* % %) (map #(- % m) vector)))))

(defn standard-deviation
  [vector]
  (Math/sqrt (variance vector)))

(defn stats
  [vector]
  (when (seq vector)
    (let [total  (total vector)
          count  (count vector)
          mean   (mean vector)
          p25    (percentile vector 25)
          p50    (percentile vector 50)
          p75    (percentile vector 75)
          p90    (percentile vector 90)
          p99    (percentile vector 99)
          p99-9  (percentile vector 99.9)
          stdev  (standard-deviation vector)
          median (median vector)]
      {:total       total
       :count       count
       :mean        mean
       :min         (apply min vector)
       :max         (apply max vector)
       :percentiles {25   p25
                     50   p50
                     75   p75
                     90   p90
                     99   p99
                     99.9 p99-9}
       :stdev       stdev
       :median      median})))
