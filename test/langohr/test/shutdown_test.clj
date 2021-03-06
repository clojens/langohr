(ns langohr.test.shutdown-test
  (:import [com.rabbitmq.client Connection Channel Consumer ShutdownSignalException])
  (:require [langohr.queue     :as lhq]
            [langohr.core      :as lhc]
            [langohr.channel   :as lch]
            [langohr.basic     :as lhb]
            [langohr.consumers :as lhcons]
            [langohr.shutdown  :as lh])
  (:use clojure.test))


(defonce ^Connection conn (lhc/connect))

(deftest test-channel-of-with-a-channel-exception
  (let [ch    (lch/open conn)
        q     (:queue (lhq/declare ch))
        latch    (java.util.concurrent.CountDownLatch. 1)
        cha      (atom nil)
        f        (fn [consumer_tag ^ShutdownSignalException reason]
                   (reset! cha (lh/channel-of reason))
                   (.countDown latch))
        consumer (lhcons/create-default ch :handle-shutdown-signal-fn f)]
    (lhb/consume ch q consumer)
    (.start (Thread. (fn []
                       (try
                         (lhq/bind ch "ugggggh" "amq.fanout")
                         (catch Exception e
                           (comment "Do nothing"))))))
    (.await latch)
    (is (= @cha ch))))

(deftest test-connection-of
  (let [ch    (lch/open conn)
        q     (:queue (lhq/declare ch))
        latch    (java.util.concurrent.CountDownLatch. 1)
        conn'    (atom nil)
        f        (fn [consumer_tag ^ShutdownSignalException reason]
                   (reset! conn' (lh/connection-of reason))
                   (.countDown latch))
        consumer (lhcons/create-default ch :handle-shutdown-signal-fn f)]
    (lhb/consume ch q consumer)
    (.start (Thread. (fn []
                       (try
                         (lhq/bind ch "ugggggh" "amq.fanout")
                         (catch Exception e
                           (comment "Do nothing"))))))
    (.await latch)
    (is (= @conn' conn))))

(deftest test-initiator-with-a-channel-exception
  (let [ch    (lch/open conn)
        q     (:queue (lhq/declare ch))
        latch    (java.util.concurrent.CountDownLatch. 1)
        sse      (atom nil)
        f        (fn [consumer_tag ^ShutdownSignalException reason]
                   (reset! sse reason)
                   (.countDown latch))
        consumer (lhcons/create-default ch :handle-shutdown-signal-fn f)]
    (lhb/consume ch q consumer)
    (.start (Thread. (fn []
                       (try
                         (lhq/bind ch "ugggggh" "amq.fanout")
                         (catch Exception e
                           (comment "Do nothing"))))))
    (.await latch)
    (is (lh/initiated-by-broker? @sse))
    (is (not (lh/initiated-by-application? @sse)))))

(deftest test-initiator-with-an-explicit-channel-closure
  (let [ch    (lch/open conn)
        q     (:queue (lhq/declare ch))
        latch    (java.util.concurrent.CountDownLatch. 1)
        sse      (atom nil)
        f        (fn [consumer_tag ^ShutdownSignalException reason]
                   (reset! sse reason)
                   (.countDown latch))
        consumer (lhcons/create-default ch :handle-shutdown-signal-fn f)]
    (lhb/consume ch q consumer)
    (Thread/sleep 250)
    (lhc/close ch)
    (.await latch)
    (is (not (lh/initiated-by-broker? @sse)))
    (is (lh/initiated-by-application? @sse))))

(deftest test-initiator-with-an-unhandled-consumer-exception
  (let [ch    (lch/open conn)
        q     (:queue (lhq/declare ch))
        latch    (java.util.concurrent.CountDownLatch. 1)
        sse      (atom nil)
        dhf      (fn [ch metadata payload]
                   ;; something terrible happens
                   (throw (RuntimeException. "the monster, it is out! Run for life!")))
        ssf      (fn [consumer_tag ^ShutdownSignalException reason]
                   (reset! sse reason)
                   (.countDown latch))
        consumer (lhcons/create-default ch :handle-delivery-fn dhf :handle-shutdown-signal-fn ssf)]
    (lhb/consume ch q consumer)
    (Thread/sleep 250)
    (lhb/publish ch "" q "message")
    (.await latch)
    (is (not (lh/initiated-by-broker? @sse)))
    (is (lh/initiated-by-application? @sse))))
