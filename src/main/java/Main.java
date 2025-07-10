import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // 打印日志，方便调试
        System.out.println("Logs from your program will appear here!");

        // 声明服务器套接字和客户端套接字
        ServerSocket serverSocket = null;
        // 设置端口号为6379（Redis默认端口）
        int port = 6379;
        // 全局线程安全存储
        Map<String, String> store = new ConcurrentHashMap<>();
        Map<String, Long> expiry = new ConcurrentHashMap<>(); // 记录过期时间戳
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
                        StringBuilder bufferString = new StringBuilder();
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            bufferString.append(new String(buffer, 0, bytesRead));
                            while (true) {
                                ParseResult result = parseRESP(bufferString.toString());
                                if (result == null) break; // 没有完整命令，等待更多数据
                                String[] parts = result.parts;
                                bufferString.delete(0, result.consumedLen); // 移除已处理部分
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
                                } else if ("SET".equals(command) && parts.length > 2) {
                                    String key = parts[1];
                                    String value = parts[2];
                                    store.put(key, value);
                                    // 检查是否有PX参数
                                    if (parts.length > 4 && "PX".equalsIgnoreCase(parts[3])) {
                                        try {
                                            long px = Long.parseLong(parts[4]);
                                            expiry.put(key, System.currentTimeMillis() + px);
                                        } catch (Exception e) {
                                            // PX参数无效，忽略
                                        }
                                    } else {
                                        expiry.remove(key); // 没有PX参数则移除过期
                                    }
                                    outputStream.write("+OK\r\n".getBytes());
                                } else if ("GET".equals(command) && parts.length > 1) {
                                    String key = parts[1];
                                    // 检查过期
                                    if (expiry.containsKey(key)) {
                                        long exp = expiry.get(key);
                                        if (System.currentTimeMillis() >= exp) {
                                            store.remove(key);
                                            expiry.remove(key);
                                        }
                                    }
                                    String value = store.get(key);
                                    if (value != null) {
                                        String resp = "$" + value.length() + "\r\n" + value + "\r\n";
                                        outputStream.write(resp.getBytes());
                                    } else {
                                        outputStream.write("$-1\r\n".getBytes());
                                    }
                                } else {
                                    outputStream.write("-ERR unknown command\r\n".getBytes());
                                }
                                outputStream.flush();
                            }
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

    // 解析结果结构体
    private static class ParseResult {
        String[] parts;
        int consumedLen;
        ParseResult(String[] parts, int consumedLen) {
            this.parts = parts;
            this.consumedLen = consumedLen;
        }
    }
    // 支持任意参数数量的RESP协议解析器，返回null表示数据不完整
    private static ParseResult parseRESP(String input) {
        if (!input.startsWith("*")) {
            return null;
        }
        int pos = 0;
        int rn = input.indexOf("\r\n", pos);
        if (rn == -1) return null;
        int arrLen = Integer.parseInt(input.substring(1, rn));
        pos = rn + 2;
        String[] result = new String[arrLen];
        for (int i = 0; i < arrLen; i++) {
            if (pos >= input.length() || input.charAt(pos) != '$') return null;
            int rnLen = input.indexOf("\r\n", pos);
            if (rnLen == -1) return null;
            int strLen = Integer.parseInt(input.substring(pos + 1, rnLen));
            pos = rnLen + 2;
            if (pos + strLen > input.length()) return null;
            result[i] = input.substring(pos, pos + strLen);
            pos += strLen;
            if (pos + 2 > input.length() || !input.substring(pos, pos + 2).equals("\r\n")) return null;
            pos += 2;
        }
        return new ParseResult(result, pos);
    }
}
