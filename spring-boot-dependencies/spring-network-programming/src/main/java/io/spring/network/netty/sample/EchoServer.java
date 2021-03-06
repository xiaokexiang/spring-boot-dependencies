package io.spring.network.netty.sample;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * @author xiaokexiang
 * @since 2020/11/23
 */
@Slf4j
public class EchoServer {

    private int port;

    public EchoServer(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        EchoServerHandler serverHandler = new EchoServerHandler();
        NioEventLoopGroup group = new NioEventLoopGroup(); // 构建EventLoop组
        try {
            // 创建引导用于bind端口
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(group)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(port)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    log.info("handler channelActive ...");
                                }
                            });
                        }
                    })
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 初始化channel时将channelHandler绑定到channelPipeline的最后一个
                            ch.pipeline().addLast(serverHandler);
                        }
                    });
            // 异步绑定端口号，sync阻塞等待绑定完成
            ChannelFuture channelFuture = bootstrap.bind().sync();
            // 阻塞等待channel关闭
            channelFuture.channel().closeFuture().sync();
        } finally {
            // 关闭eventLoopGroup，释放所有资源
            group.shutdownGracefully().sync();
        }
    }

    public static void main(String[] args) {
        EchoServer echoServer = new EchoServer(8001);
        try {
            echoServer.start();
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
