import java.io.IOException;
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
            // 向客户端发送Redis协议格式的PONG响应
            outputStream.write("+PONG\r\n".getBytes());
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
