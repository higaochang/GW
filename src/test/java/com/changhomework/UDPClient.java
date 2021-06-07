package com.changhomework;

import java.io.BufferedReader;
import java.net.*;

public class UDPClient {
    DatagramSocket ds = null;
    private UDPClient(){
        try {
            ds = new  DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }

    }
    static UDPClient ins;
    public static UDPClient getIns(){
        if(ins == null){
            ins = new UDPClient();
        }
        return ins;
    }

    public void pingUDP(){
        byte[] arr = {'a', 'b'};
        InetAddress ia = null;
        try {
            ia = InetAddress.getByName("localhost");
            DatagramPacket dp = new DatagramPacket(arr, arr.length, ia, 10002);
            ds.send(dp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
