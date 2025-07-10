import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static void main(String[] args) {
        // 打印日志，方便调试
        System.out.println("Logs from your program will appear here!");

        // 声明服务器套接字和客户端套接字
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        // 设置端口号为6379（Redis默认端口）
        int port = 6379;
        try {
            // 创建服务器套接字，绑定到指定端口
            serverSocket = new ServerSocket(port);
            // 设置SO_REUSEADDR，避免端口占用导致重启失败
            serverSocket.setReuseAddress(true);
            // 等待客户端连接（阻塞，直到有客户端连接进来）
            clientSocket = serverSocket.accept();
            // 获取客户端的输出流
            OutputStream outputStream = clientSocket.getOutputStream();
            // 获取客户端的输入流，用于读取命令
            InputStream inputStream = clientSocket.getInputStream();
            // 循环读取客户端发送的命令并响应
            byte[] buffer = new byte[1024]; // 缓冲区
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // 只要收到数据就回复+PONG\r\n
                // 实际上这里不解析命令，直接每次收到数据都回复PONG
                outputStream.write("+PONG\r\n".getBytes());
                outputStream.flush(); // 立即发送
            }
        } catch (IOException e) {
            // 捕获并打印异常信息
            System.out.println("IOException: " + e.getMessage());
        } finally {
            try {
                // 关闭客户端套接字，释放资源
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println("IOException: " + e.getMessage());
            }
        }
    }
}
