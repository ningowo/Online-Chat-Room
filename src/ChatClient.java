import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Scanner;

/**
 *
 * This is a multi-thread client program based on NIO(althought might be indifferent from BIO)
 * And it use two threads for selecting and listening.
 *
 * @author Ning Ding
 * @version 2021.2.27
 */
public class ChatClient {
    private SocketChannel socketChannel;
    private Selector selector = null;
    private final String username;

    public ChatClient(String username) {
        this.username = username;
    }

    public static void main(String[] args) {
        System.out.println("======== Program Start ========");
        ChatClient client = new ChatClient(args[2]);
        client.start(args[0], Integer.parseInt(args[1]));
    }

    public void start(String host, int port) {
        try {
            selector = Selector.open();
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(host, port));
            socketChannel.register(selector, SelectionKey.OP_READ);

            System.out.println("Enter whatever to chat with other\n" +
                    "Use '/msg someUsername yourMsg' to send to a specific user\n" +
                    "Use '/logout' to logout\n" +
                    "===============================");

            write(new ChatMessage(username, 0));
            new Thread(new Send()).start();
            listen();
        } catch (IOException e) {
            System.out.println("Unable to connect");
            e.printStackTrace();
        }
    }
    private class Send implements Runnable {
        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            try {
                while (true) {
                    String msg = scanner.nextLine();
                    String[] info = msg.split(" ");
                    if (info.length > 0) {
                        if ("/logout".equals(info[0])) {
                            System.out.println("Thank you for using our chatRoom!");
                            write(new ChatMessage(username, -1));
                            throw new IllegalStateException();
                        } else if (info.length > 2 && "/tell".equals(info[0])) {
                            // msg is like: /tell ding hello there!
                            // public ChatMessage(String from, int type, String content, String to)
                            write(new ChatMessage(username, 1, msg.substring(6), info[1]));
                        } else {
                            write(new ChatMessage(username, 2, msg));
                        }
                    }
                }
            } catch (IllegalStateException e) {
                System.out.println("Bye~");
                System.exit(0);
            }
        }
    }

    public void write(ChatMessage msg) {
        ObjectOutputStream oos = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(msg);
            oos.flush();
            // 一定！一定！要加这一句！
            // 不然start里socketChannel只是申请建立连接，不代表已经可以连接
            // 会报NotYetConnectedException，写的很明确了，还没连上
            socketChannel.finishConnect();
            socketChannel.write(ByteBuffer.wrap(baos.toByteArray()));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NotYetConnectedException e) {
            System.out.println("Server not started");
            System.exit(0);
        } finally {
            try {
                assert oos != null;
                oos.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }


    }

    public void listen() {

        try {
            while (true) {
                //阻塞等待事件触发
                selector.select();
                Iterator<SelectionKey> keysToRead = selector.selectedKeys().iterator();
                while (keysToRead.hasNext()) {
                    SelectionKey key = keysToRead.next();
                    SocketChannel sc = (SocketChannel) key.channel();
                    if (key.isReadable()) {
                        // channel -> buffer
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        sc.read(buffer);
                        buffer.flip();
                        String content = new String(buffer.array());
                        if ("Server: Sorry, username has been occupied.".equals(content)) {
                            throw new IllegalArgumentException();
                        }
                        // 打印server送过来的内容
                        System.out.println(content);
                    }
                    keysToRead.remove();
                }
            }
        } catch (IOException e) {
            System.out.println("Server closed connection.");
            System.exit(0);
        } catch (IllegalArgumentException e) {
            System.out.println("Server: Sorry, username has been occupied.");
            System.exit(0);
        }
    }

}






