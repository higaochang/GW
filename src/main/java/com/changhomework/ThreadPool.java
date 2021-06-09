package com.changhomework;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

public class ThreadPool {
    private static final Logger log = LoggerFactory.getLogger(ThreadPool.class);

    final static ConcurrentHashMap<Long, LinkedBlockingQueue<MessageFactory.Message>> previousMessages
            = new ConcurrentHashMap<>();
    final static Map<Long, Long> prevPrice = new ConcurrentHashMap<>();
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
        
        rulesThreadPool.submit(() -> {
            // 1. price checking for D
//            long startTime = System.nanoTime();
            final Map<Long, Boolean> priceCheckingResult = new HashMap<>();
            if (msg.msgType == 'D') {
                final Long newerPrice = msg.price;
                prevPrice.compute(msg.ciOrdId, (k, v) -> {
                    if (v == null) {
                        log.info("update initial price for ciOrdId {} with {}", msg.ciOrdId, newerPrice);
                        priceCheckingResult.put(k, true);
                        return newerPrice;
                    }
                    if (Math.abs(newerPrice - v * 1.05) > 0) {
                        log.info("price over limit for ciOrdId {}, drop it", msg.ciOrdId);
                        priceCheckingResult.put(k, false);
                        return v;
                    } else {
                        log.info("update previous price for ciOrdId {} with new price {}", msg.ciOrdId, newerPrice);
                        priceCheckingResult.put(k, true);
                        return newerPrice;
                    }
                });
                final boolean validPrice = priceCheckingResult.getOrDefault(msg.ciOrdId, false);
                if (!validPrice) { // D msg price over limit, just drop it without further rules checking
                    log.info("D msg price over limit for ciOrdId {} with price {}", msg.ciOrdId, newerPrice);
                    return;
                }
            }
//            long endTime = System.nanoTime();
//            log.info("Price Checking took for "+(endTime - startTime) + " ns");


            // 2. handle cancel logic
            previousMessages.compute(msg.ciOrdId, (k, v) -> {
                if (v == null) { // initial message
                    LinkedBlockingQueue<MessageFactory.Message> list = new LinkedBlockingQueue<>();
                    if (msg.msgType == 'F') { // doesn't make sense to forward F without any D received before
                        msg.cancelOrd();
                    }
                    list.offer(msg);
                    tcpForwardingQueue.offer(msg);
                    return list;
                }
                // has previous message
                if (msg.msgType == 'F') {
                    log.info("received Cancel msg {}", msg.ciOrdId);
                    boolean foundOrd = false;
                    Iterator<MessageFactory.Message> iterator = v.iterator();
                    while (iterator.hasNext()) {
                        MessageFactory.Message m = iterator.next();
                        if (m.forwardNeeded && m.msgType == 'D') {
                            // cancel on fly D, no need forward F
                            m.cancelOrd();
                            foundOrd = true;
                        } else if (m.forwardNeeded && m.msgType == 'F') {
                            // found already F, no need forward duplicated F
                            foundOrd = true;
                        }
                    }
                    if (!foundOrd) {
                        // No matching, should send Cancel Ord
                        tcpForwardingQueue.offer(msg);
                    }
                }
                if (msg.msgType == 'D') {
                    log.info("received New Order msg {}", msg.ciOrdId);
                    Iterator<MessageFactory.Message> iterator = v.iterator();
                    while (iterator.hasNext()) {
                        MessageFactory.Message m = iterator.next();
                        if (m.msgType == 'F') {
                            // F msg came before, for the same ciOrdId D msg, should not forward at all
                            msg.cancelOrd();
                        }
                    }
                    tcpForwardingQueue.offer(msg);
                }
                v.offer(msg);
                return v;
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

        return new ThreadPoolExecutor(
                12, 12,
                4, TimeUnit.SECONDS,
                queue, new ThreadFactoryBuilder().setNameFormat("threadpool-%d").build(), (r, executor) -> {
            try {
                if (!executor.getQueue().offer(r, 0, TimeUnit.SECONDS)) {
                    throw new RejectedExecutionException("ThreadPool queue full, failed to offer " + r.toString());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

}
