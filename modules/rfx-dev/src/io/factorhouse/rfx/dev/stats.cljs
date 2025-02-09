(ns io.factorhouse.rfx.dev.stats)

(defn median-odd
  [xs]
  (nth xs (/ (count xs) 2)))

(defn median-even
  [xs]
  (let [middle-idx (/ (count xs) 2)]
    (/ (+ (nth xs middle-idx) (nth xs (dec middle-idx))) 2)))

(defn median
  [xs]
  (if (even? (count xs))
    (median-even xs)
    (median-odd xs)))

(defn total
  [xs]
  (apply + xs))

(defn mean
  [xs]
  (/ (total xs) (count xs)))

(defn percentile
  [xs percentile]
  (let [sorted-xs (sort xs)
        idx       (int (* percentile (/ (count sorted-xs) 100)))]
    (nth sorted-xs idx)))

(defn variance
  [xs]
  (let [m (mean xs)]
    (apply + (map #(* % %) (map #(- % m) xs)))))

(defn standard-deviation
  [xs]
  (Math/sqrt (variance xs)))

(defn stats
  [xs]
  (when (seq xs)
    {:total       (total xs)
     :count       (count xs)
     :mean        (mean xs)
     :min         (apply min xs)
     :max         (apply max xs)
     :percentiles {25   (percentile xs 25)
                   50   (percentile xs 50)
                   75   (percentile xs 75)
                   90   (percentile xs 90)
                   99   (percentile xs 99)
                   99.9 (percentile xs 99.9)}
     :stdev       (standard-deviation xs)
     :median      (median xs)}))
