package server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Set;

public class ChatServer {
    private static final int DEFAULT_PORT= 8888;
    private static final String QUIT ="quit";
    private static final int BUFFER =1024;

    private ServerSocketChannel server;
    private Selector selector;
    private int port;
    private ByteBuffer wBuffer=ByteBuffer.allocate(BUFFER);
    private ByteBuffer rBuffer=ByteBuffer.allocate(BUFFER);
    private Charset charset=Charset.forName("UTF-8");

    public ChatServer(int port){
        this.port=port;
    }
    public ChatServer(){
        this.port=DEFAULT_PORT;
    }

    private void start(){
        try {
            server=ServerSocketChannel.open();
            server.configureBlocking(false);
            server.socket().bind(new InetSocketAddress(port));

            selector=Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("启动服务器，监听端口："+port);

            while (true) {
                selector.select();
                Set<SelectionKey> selectionKeys =selector.selectedKeys();
                for(SelectionKey key:selectionKeys){
                    handles(key);
                }
                selectionKeys.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(selector);
        }
    }

    private void handles(SelectionKey key) throws IOException {
        if(key.isAcceptable()){
            ServerSocketChannel server= (ServerSocketChannel) key.channel();
            SocketChannel client =server.accept();
            client.configureBlocking(false);
            client.register(selector,SelectionKey.OP_READ);
            System.out.println(getClientName(client)+"已连接");
        }
        else if(key.isReadable()){
            SocketChannel client= (SocketChannel) key.channel();
            String fwdMsg =receive(client);
            if(fwdMsg.isEmpty()){
                key.cancel();
                selector.wakeup();
            } else{
                System.out.println(getClientName(client)+"："+fwdMsg);
                forwardMessage(client,fwdMsg);
                if(readyToQuit(fwdMsg)){
                    key.cancel();
                    selector.wakeup();
                    System.out.println(getClientName(client)+"已断开");
                }
            }
        }
    }
    private String getClientName(SocketChannel client) {
        return "客户端[" + client.socket().getPort() + "]";
    }

    private boolean readyToQuit(String fwdMsg) {
        return (fwdMsg.equals(QUIT));
    }

    private void forwardMessage(SocketChannel client, String fwdMsg) throws IOException {
        for(SelectionKey key:selector.keys()){
            Channel connectedClient = key.channel();
            if(connectedClient instanceof ServerSocketChannel){
                continue;
            }

            if(key.isValid()&&!client.equals(connectedClient)){
                wBuffer.clear();
                wBuffer.put(charset.encode(getClientName(client)+":"+fwdMsg));
                wBuffer.flip();
                while(wBuffer.hasRemaining()){
                    ((SocketChannel)connectedClient).write(wBuffer);
                }
            }
        }
    }

    private String receive(SocketChannel client) throws IOException {
        rBuffer.clear();
        while(client.read(rBuffer)>0);
        rBuffer.flip();
        return String.valueOf(charset.decode(rBuffer));
    }

    private  void close(Closeable closeable){
        if(closeable!=null){
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        ChatServer chatServer=new ChatServer(7777);
        chatServer.start();
    }
}
