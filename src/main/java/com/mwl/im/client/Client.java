package com.mwl.im.client;

import com.mwl.im.client.handler.LoginReponseHandler;
import com.mwl.im.client.handler.MessageResponseHandler;
import com.mwl.im.codec.PacketDecoder;
import com.mwl.im.codec.PacketEncoder;
import com.mwl.im.protocol.request.MessageRequestPacket;
import com.mwl.im.utils.LoginUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * @author mawenlong
 * @date 2019/02/18
 */
@Slf4j
public class Client {
    static final int PORT = Integer.parseInt(System.getProperty("port", "5050"));
    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int MAX_RETRY = 3;

    public void start(String host, int port) {
        EventLoopGroup group = new NioEventLoopGroup();

        Bootstrap b = new Bootstrap();
        b.group(group)
         .channel(NioSocketChannel.class)
         // 表示连接的超时时间，超过这个时间还是建立不上的话则代表连接失败
         .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
         // 表示是否开启 TCP 底层心跳机制，true 为开启
         .option(ChannelOption.SO_KEEPALIVE, true)
         // 表示是否开始 Nagle 算法，true 表示关闭，false 表示开启，通俗地说，如果要求高实时性，有数据发送时就马上发送，就设置为 true 关闭，如果需要减少发送次数减少网络交互，就设置为 false 开启
         .option(ChannelOption.TCP_NODELAY, true)
         .attr(AttributeKey.newInstance("clientName"), "IM Client")
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) throws Exception {
                 ch.pipeline()
                   .addLast(new PacketDecoder())
                   .addLast(new LoginReponseHandler())
                   .addLast(new MessageResponseHandler())
                   .addLast(new PacketEncoder());
             }
         });
        connect(b, host, port, MAX_RETRY);
    }

    public void connect(Bootstrap bootstrap, String host, int port, int retry) {
        bootstrap.connect(host, port).addListener(future -> {
            if (future.isSuccess()) {
                log.info("连接成功!");
                Channel channel = ((ChannelFuture) future).channel();
                startConsoleThread(channel);
            } else if (retry == 0) {
                log.info("重试次数已用完，放弃连接！");
            } else {
                int order = (MAX_RETRY - retry) + 1;
                //本次重连间隔
                int delay = 1 << order;
                log.info("连接失败，第" + order + "次重连……");
                bootstrap.config().group()
                         .schedule(() -> connect(bootstrap, host, port, retry - 1), delay,
                                   TimeUnit.SECONDS);
            }
        });
    }

    private static void startConsoleThread(Channel channel) {
        new Thread(() -> {
            while (!Thread.interrupted()) {
                if (LoginUtil.hasLogin(channel)) {
                    System.out.println("输入要发送给服务器端的消息：");
                    Scanner sc = new Scanner(System.in);
                    String line = sc.nextLine();

                    MessageRequestPacket requestPacket = new MessageRequestPacket();
                    requestPacket.setMessage(line);
                    channel.writeAndFlush(requestPacket);
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        new Client().start(HOST, PORT);
    }
}
