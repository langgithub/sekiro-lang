package com.lang.reactor.first;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个reactor模型
 */
public class Reactor implements Runnable {
    private Selector selector;
    public static ServerSocketChannel serverSocketChannel;
    public static Map<String, SocketChannel> mebers=new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        Reactor reactor = new Reactor();
        new Thread(reactor).run();
    }


    public Reactor() throws IOException {
        serverSocketChannel=ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(8899));
        System.out.println(serverSocketChannel.getLocalAddress());
        selector=Selector.open();
        SelectionKey selctionKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        selctionKey.attach(new Accpet());
    }

    @Override
    public void run() {
        while (true){
            try {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()){
                    SelectionKey selectionKey = iterator.next();
                    Runnable runnable= (Runnable) selectionKey.attachment();
                    if (runnable!=null){
                        runnable.run();
                    }
                    iterator.remove();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class Accpet implements Runnable {

        @Override
        public void run() {
            try {
                SocketChannel socketChannel = serverSocketChannel.accept();
                if (socketChannel!=null){
                    UUID uuid = UUID.randomUUID();
                    System.out.println("客户端："+uuid+"上线了");
                    mebers.put(uuid.toString(),socketChannel);
                    new Handler(selector,socketChannel);

                    for (Map.Entry<String,SocketChannel> entry: mebers.entrySet()){
                        if (entry.getValue()==socketChannel){
                            //当前用户
                        }else {
                            ByteBuffer byteBuffer=ByteBuffer.allocate(1024);
                            byteBuffer.put((uuid+"链接成功\n").getBytes());
                            byteBuffer.flip();
                            entry.getValue().write(byteBuffer);
                        }
                    }
                }

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
