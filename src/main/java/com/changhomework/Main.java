package com.changhomework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        init();
        start();
    }

    // read config, setup connection
    private static void init() {
        log.info("Initializing Application");

        TCPClient.getInstance().init();
        UDPListener.getInstance().init();
    }

    // read config, setup connection
    private static void start() {
        log.info("Starting TCPClient");
        TCPClient.getInstance().start();

        log.info("Starting UDPListener");
        UDPListener.getInstance().start();
    }
}
