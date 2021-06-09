package com.changhomework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

public class TCPClient {
    private static final Logger log = LoggerFactory.getLogger(TCPClient.class);
    static ExecutorService executorService = Executors.newSingleThreadExecutor();
    static ExecutorService ticketGeneratorService = Executors.newSingleThreadExecutor();
    static SynchronousQueue<Integer> ticketQueue = new SynchronousQueue<>(true);
    int sleepDuration = 100;
    private static TCPClient ins;
    String hostname;
    int port;

    public static synchronized TCPClient getInstance() {
        if (ins == null) {
            ins = new TCPClient();
        }
        return ins;
    }

    private TCPClient() {

    }

    public void handleConnect(SelectionKey key) throws Exception {
        SocketChannel channel = (SocketChannel) key.channel();
        if (channel.isConnectionPending())
            channel.finishConnect();
        channel.register(key.selector(), SelectionKey.OP_WRITE);

        log.info("TCPClient connected");
    }

    public void handleWrite(SelectionKey key) throws Exception {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        while (true) {
            MessageFactory.Message message = ThreadPool.tcpForwardingQueue.take();
            this.ticketQueue.take();
            //long startTime = System.nanoTime();
            if (message != null && message.forwardNeeded) {
                log.info("TCP forward ID {}, MsgType {}", message.ciOrdId, message.msgType);
                socketChannel.write(message.getBytes());
                MessageFactory.returnMessage(message);
            }
            if(message != null && !message.forwardNeeded){
                log.info("TCP skip forwarding ID {}, MsgType {}", message.ciOrdId, message.msgType);
            }
            //long endTime = System.nanoTime();
            //System.out.println("Took TCP send "+(endTime - startTime) + " ns");
        }
    }

    public void init() {
        int rate = PropertiesUtil.getInstance("app.properties").getInt("rate");
        sleepDuration = 1000 / rate;
        log.info("Sleep Duration {}ms", sleepDuration);
        hostname = PropertiesUtil.getInstance("app.properties").getProperty("tcp_hostname", "127.0.0.1");
        port =     PropertiesUtil.getInstance("app.properties").getInt("tcp_port", 10001);
    }

    public void start() {
        ticketGeneratorService.execute(()->{
            while (true){
                ticketQueue.offer(Integer.valueOf(1));
                try {
                    Thread.sleep(sleepDuration);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        executorService.execute(() -> {
            try {
                Selector selector = Selector.open();
                SocketChannel socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);
                socketChannel.connect(new InetSocketAddress(hostname, port));
                socketChannel.register(selector, SelectionKey.OP_CONNECT);

                while (true) {
                    int selectInt = selector.select();
                    if (selectInt == 0)
                        continue;
                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        if (key.isConnectable())
                            handleConnect(key);
                        if (key.isWritable())
                            handleWrite(key);
                        it.remove();
                    }
                }

            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }

        });
    }
}
