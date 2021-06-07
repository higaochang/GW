package com.changhomework;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;

public class MessageFactory {
    static ArrayBlockingQueue<Message> pool = new ArrayBlockingQueue<Message>(100_000);

    static {
        for(int i = 0 ; i < 100_000; i++){
            pool.add(new Message());
        }
    }

    public static Message getMessage(){
        Message temp = pool.poll();
        if(temp == null){
            return new Message();
        }
        return temp;
    }

    public static void returnMessage(Message message){
        message.forwardNeeded = true;
        pool.offer(message);
    }

    public static class Message{
        volatile byte[] bytes = new byte[21];
        volatile boolean forwardNeeded = true;
        volatile char msgType;
        Long ciOrdId = 0L;
        Long price = 0L;

        public void fill(ByteBuffer byteBuffer){
            byteBuffer.flip();
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            msgType = (char) byteBuffer.get(2);
            byteBuffer.position(3);
            ciOrdId = byteBuffer.getLong();
            byteBuffer.position(13);
            price = byteBuffer.getLong();
            byteBuffer.flip();
            byteBuffer.get(bytes, 0, bytes.length);
        }

        public ByteBuffer getBytes() {
            return ByteBuffer.wrap(bytes);
        }

        public void cancelOrd(){
            forwardNeeded = false;
        }

        @Override
        public String toString() {
            return "msgType=" + msgType + " ciOrdId=" + String.valueOf(ciOrdId)
                    + " price " + String.valueOf(price);
        }
    }
}

