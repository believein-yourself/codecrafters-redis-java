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
        // 设置端口号为6379（Redis默认端口）
        int port = 6379;
        try {
            // 创建服务器套接字，绑定到指定端口
            serverSocket = new ServerSocket(port);
            // 设置SO_REUSEADDR，避免端口占用导致重启失败
            serverSocket.setReuseAddress(true);
            while (true) {
                // 等待客户端连接（阻塞，直到有客户端连接进来）
                Socket client = serverSocket.accept();
                // 为每个客户端连接启动一个新线程进行处理
                new Thread(() -> {
                    try {
                        OutputStream outputStream = client.getOutputStream();
                        InputStream inputStream = client.getInputStream();
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            String input = new String(buffer, 0, bytesRead);
                            String[] parts = parseRESP(input);
                            if (parts.length == 0) {
                                continue;
                            }
                            String command = parts[0].toUpperCase();
                            if ("PING".equals(command)) {
                                outputStream.write("+PONG\r\n".getBytes());
                            } else if ("ECHO".equals(command) && parts.length > 1) {
                                String arg = parts[1];
                                String resp = "$" + arg.length() + "\r\n" + arg + "\r\n";
                                outputStream.write(resp.getBytes());
                            } else {
                                // 未知命令，简单返回错误
                                outputStream.write("-ERR unknown command\r\n".getBytes());
                            }
                            outputStream.flush();
                        }
                    } catch (IOException e) {
                        // 可以忽略客户端异常断开等情况
                    } finally {
                        try {
                            client.close();
                        } catch (IOException e) {
                            // 忽略关闭异常
                        }
                    }
                }).start();
            }
        } catch (IOException e) {
            // 捕获并打印异常信息
            System.out.println("IOException: " + e.getMessage());
        } finally {
            try {
                // 关闭服务器套接字，释放资源
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.out.println("IOException: " + e.getMessage());
            }
        }
    }

    // 简单RESP协议解析器，只支持*2\r\n$4\r\nECHO\r\n$3\r\nhey\r\n 这种格式
    private static String[] parseRESP(String input) {
        if (!input.startsWith("*")) {
            return new String[0];
        }
        String[] lines = input.split("\\r\\n");
        int idx = 0;
        if (lines.length < 4) return new String[0];
        int arrLen = Integer.parseInt(lines[idx++].substring(1));
        String[] result = new String[arrLen];
        for (int i = 0; i < arrLen; i++) {
            if (lines[idx].startsWith("$") && idx + 1 < lines.length) {
                result[i] = lines[idx + 1];
                idx += 2;
            } else {
                break;
            }
        }
        return result;
    }
}
