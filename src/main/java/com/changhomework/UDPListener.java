package com.changhomework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPListener {
    private static final Logger log = LoggerFactory.getLogger(UDPListener.class);
    static ExecutorService executorService = Executors.newSingleThreadExecutor();
    DatagramChannel channel;
    static ByteBuffer byteBuffer = ByteBuffer.allocate(21);
    private static UDPListener ins;
    String hostname;
    int port;

    public static synchronized UDPListener getInstance() {
        if (ins == null) {
            ins = new UDPListener();
        }
        return ins;
    }

    private UDPListener() {

    }

    public void start() {
        executorService.execute(() -> {
            try {
                Selector selector = Selector.open();
                channel = DatagramChannel.open();
                channel.configureBlocking(false);
                channel.bind(new InetSocketAddress(hostname, port));
                channel.register(selector, SelectionKey.OP_READ);
                while (true) {
                    int selectInt = selector.select(); // blocks until at least one channel is ready
                    if (selectInt == 0)
                        continue;
                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        if (key.isReadable()) {
//                            long startTime = System.nanoTime();
                            channel = (DatagramChannel) key.channel();
                            byteBuffer.clear();
                            channel.receive(byteBuffer);
                            MessageFactory.Message m = MessageFactory.getMessage();
                            m.fill(byteBuffer);
                            //log.info(m.toString());
                            ThreadPool.getInstance().submit(m);
//                            long endTime = System.nanoTime();
//                            System.out.println("Took UDP received "+(endTime - startTime) + " ns");
                        }
                        it.remove();
                    }
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            } finally {
                try {
                    channel.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        });
    }

    public void init() {
        hostname = PropertiesUtil.getInstance("app.properties").getProperty("udp_hostname", "127.0.0.1");
        port =     PropertiesUtil.getInstance("app.properties").getInt("udp_port", 10002);
    }
}
