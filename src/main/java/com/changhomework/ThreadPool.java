package com.changhomework;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class ThreadPool {
    private static final Logger log = LoggerFactory.getLogger(ThreadPool.class);

    final static ConcurrentHashMap<Long, LinkedBlockingQueue<MessageFactory.Message>> previousMessages
            = new ConcurrentHashMap<Long, LinkedBlockingQueue<MessageFactory.Message>>();
    final static Map<Long, Long> prevPrice = new ConcurrentHashMap<Long, Long>();
    static ExecutorService rulesThreadPool = proactiveExecutorService();
    public final static LinkedBlockingQueue<MessageFactory.Message> tcpForwardingQueue
            = new LinkedBlockingQueue<>();

    private static ThreadPool ins;

    public static synchronized ThreadPool getInstance() {
        if (ins == null) {
            ins = new ThreadPool();
        }
        return ins;
    }

    private ThreadPool() {

    }

    public void submit(final MessageFactory.Message msg) {
        // Cancel Order Checking: cancel msg in queue, if no match outbound cancel msg
        rulesThreadPool.submit(() -> {
            // 1. price checking for D
//            long startTime = System.nanoTime();
            if (msg.msgType == 'D') {
                final Long newerPrice = msg.price;
                prevPrice.compute(msg.ciOrdId, (k, v) -> {
                    if(v == null){
                        log.info("update price for {} with {}", msg.ciOrdId, newerPrice);
                        tcpForwardingQueue.offer(msg);
                        return newerPrice;
                    }

                    if (Math.abs(newerPrice - v * 1.05) > 0) {
                        log.info("price over limit for {}, drop it", msg.ciOrdId);
                        return v;
                    } else {
                        log.info("update previous price for {} with {}", msg.ciOrdId, newerPrice);
                        tcpForwardingQueue.offer(msg);
                        return newerPrice;
                    }
                });
            }
//            long endTime = System.nanoTime();
//            log.info("Price Checking took for "+(endTime - startTime) + " ns");

            // 2. handle cancel
            previousMessages.compute(msg.ciOrdId, (k, v) -> {
                if (v == null) {
                    LinkedBlockingQueue<MessageFactory.Message> list = new LinkedBlockingQueue<MessageFactory.Message>();
                    if (msg.msgType == 'F') {
                        msg.cancelOrd();
//                        tcpForwardingQueue.offer(msg);
                    }
                    list.offer(msg);
                    return list;
                } else { // has previous msg
                    if (msg.msgType == 'F') {
                        log.info("received Cancel msg {}", msg.ciOrdId);
                        boolean foundOrd = false;
                        Iterator<MessageFactory.Message> iterator = v.iterator();
                        while (iterator.hasNext()) {
                            MessageFactory.Message m = iterator.next();
                            if (m.forwardNeeded && m.msgType == 'D') {// cancel on fly D, no need forward F
                                m.cancelOrd();
                                foundOrd = true; // skip forward
                            } else if(m.forwardNeeded && m.msgType == 'F'){ // found already F, no need forward duplicated F
                                foundOrd = true; // skip forward
                            }
                        }
                        if (!foundOrd) {
                            // No matching, should send Cancel Ord
                            tcpForwardingQueue.offer(msg);
                        }
                    } else if (msg.msgType == 'D') {
                        log.info("received updated New Order msg {}", msg.ciOrdId);
                    }
                    v.offer(msg);
                    return v;
                }
            });
//            long endTime2 = System.nanoTime();
//            log.info("Handling Cancel took for "+(endTime2 - endTime) + " ns");
        });
    }


    private static ExecutorService proactiveExecutorService() {

        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(100_000) {
            @Override
            public boolean offer(Runnable o) {
                return false;
            }
        };
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                1, 1,
                5, TimeUnit.SECONDS,
                queue, new ThreadFactoryBuilder().setNameFormat("threadpool-%d").build(), (r, executor) -> {
            try {
                if (!executor.getQueue().offer(r, 0, TimeUnit.SECONDS)) {
                    throw new RejectedExecutionException("ThreadPool queue full, failed to offer " + r.toString());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        return threadPool;
    }

}
