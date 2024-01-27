package com.lang.reactor.first;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Map;

/**
 * 此处handler可以改写成多线程处理
 */
public class Handler implements Runnable{
    private String state;
    private Selector selector;
    private SocketChannel socketChannel;

    public Handler(Selector selector, SocketChannel socketChannel) throws IOException {
        this.state="read";
        this.selector = selector;
        this.socketChannel = socketChannel;
        socketChannel.configureBlocking(false);
        SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
        selectionKey.attach(this);
    }

    @Override
    public void run() {
        try{
            if (state.equals("read")){
                read();
            }else if(state.equals("write")){
                write();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 不建议使用
     * @throws IOException
     */
    private void write() throws IOException {
//        System.out.println("wirte");
//        ByteBuffer byteBuffer=ByteBuffer.allocate(10);
//        byteBuffer.put("heelo write".getBytes());
//        byteBuffer.flip();
//        socketChannel.write(byteBuffer);
////        socketChannel.register(selector,SelectionKey.OP_READ);
////        state="read";
    }

    private void read() throws IOException {
        ByteBuffer byteBuffer=ByteBuffer.allocate(10);
        int count = socketChannel.read(byteBuffer);
        if (count>0){
            byteBuffer.clear();
            String msg=new String(byteBuffer.array(),0,count);
            for (Map.Entry<String,SocketChannel> entry: Reactor.mebers.entrySet()){
                if (entry.getValue()==socketChannel){
                    System.out.println(entry.getKey()+">>>>>>say:"+msg);
                }else {
                    ByteBuffer readBuffer=ByteBuffer.allocate(10);
                    readBuffer.put(msg.getBytes());
                    readBuffer.flip();
                    entry.getValue().write(readBuffer);
                }
            }
        }
//        socketChannel.register(selector,SelectionKey.OP_WRITE);
//        1.如果这里添加 SelectionKey.OP_WRITE 感兴趣的事件，那么channle一直都是可写入状态,selector就不会阻塞，直到cpu卡死
//        2.正确操作是将msg放入到缓存区，在添加 SelectionKey.OP_WRITE 感兴趣的事件，Selector.isWriteable()方法监控的是内核的写缓冲器是否可写
//        state="write";
    }
}
