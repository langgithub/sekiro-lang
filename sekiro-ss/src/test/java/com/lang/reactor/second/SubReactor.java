package com.lang.reactor.second;

import com.lang.reactor.second.Reactor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 各自存储selector
 */
public class SubReactor implements Runnable {

    private Selector selector;

    public SubReactor() throws IOException {
        selector= SelectorProvider.provider().openSelector();
    }

    public void registerChannel(SocketChannel ch) throws IOException {
        ch.configureBlocking(false);
        ch.register(selector, SelectionKey.OP_READ);
    }

    @Override
    public void run() {
        try{
            while (!Thread.interrupted()){
                // 这里不能阻塞获取，不然，通道没获取一直阻塞到这里，无法后续工作
                selector.selectNow();
                // 也可以 selector.select(1230l);
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> selectionKeyIterator = selectionKeys.iterator();
                while (selectionKeyIterator.hasNext()){
                    SelectionKey selectionKey = selectionKeyIterator.next();
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    if (selectionKey.isReadable()){
                        ByteBuffer byteBuffer=ByteBuffer.allocate(10);
                        int count = socketChannel.read(byteBuffer);
                        if (count>0){
                            byteBuffer.clear();
                            String msg=new String(byteBuffer.array(),0,count);
                            // 先遍历一次找到 当前socketChannel,获取客户端id
                            String currentClient=null;
                            for (Map.Entry<String,SocketChannel> entry: Reactor.mebers.entrySet()){
                                if (entry.getValue()==socketChannel){
                                    System.out.println(entry.getKey()+">>>>>>say:"+msg);
                                    currentClient=entry.getKey();
                                }
                            }
                            // 再遍历一遍，将当前客户端id,put发送给其他客户端
                            for (Map.Entry<String,SocketChannel> entry: Reactor.mebers.entrySet()){
                                if (entry.getValue()!=socketChannel){
                                    ByteBuffer readBuffer=ByteBuffer.allocate(1024);
                                    readBuffer.put((currentClient+" say: "+msg).getBytes());
                                    readBuffer.flip();
                                    entry.getValue().write(readBuffer);
                                }
                            }
                        }
                    }else if (selectionKey.isWritable()){
                        //Todo
                    }
                    selectionKeyIterator.remove();
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
