package com.lang.reactor.second;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多个reactor模型
 */
public class Reactor implements Runnable {
    private Selector selector;
    public static ServerSocketChannel serverSocketChannel;
    public static Map<String, SocketChannel> mebers=new ConcurrentHashMap<>();
    volatile int nextHandler=0;

    public static void main(String[] args) throws IOException {
        Reactor reactor = new Reactor();
        new Thread(reactor).start();
    }


    public Reactor() throws IOException {
        serverSocketChannel=ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(8890));
        System.out.println(serverSocketChannel.getLocalAddress());
        selector= SelectorProvider.provider().openSelector();
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

        private SubReactor[] subReactors=new SubReactor[1];

        public Accpet() throws IOException {
            initSubReactor();
        }

        /**
         * 根据传递的线程数模拟出SubReactor
         */
        private void initSubReactor() throws IOException {
            for (int i=0;i<1;i++){
                subReactors[i]=new SubReactor();
                new Thread(subReactors[i]).start();
            }
        }

        @Override
        public void run() {
            try {
                SocketChannel socketChannel = serverSocketChannel.accept();
                synchronized (socketChannel){
                    SubReactor subReactor=subReactors[nextHandler];
                    subReactor.registerChannel(socketChannel);
                    nextHandler++;
                    if(nextHandler>=subReactors.length){
                        nextHandler=0;
                    }
//                    subReactor.run();

                    UUID uuid = UUID.randomUUID();
                    System.out.println("客户端："+uuid+"上线了");
                    mebers.put(uuid.toString(),socketChannel);

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
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }
}
