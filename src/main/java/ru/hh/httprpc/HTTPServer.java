package ru.hh.httprpc;

import com.google.common.util.concurrent.AbstractService;
import io.netty.bootstrap.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import java.nio.channels.ClosedChannelException;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import io.netty.handler.codec.http.HttpServerCodec;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.util.netty.Http;

public class HTTPServer extends AbstractService {
  public static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

  private final EventLoopGroup bossGroup;
  private final EventLoopGroup workerGroup;
  private final ServerBootstrap bootstrap;
  private final ChannelTracker channelTracker = new ChannelTracker();
  volatile private Channel serverChannel;
  
  /**
   * @param options
   * @param ioThreads the maximum number of I/O worker threads
   */
  public HTTPServer(TcpOptions options, int ioThreads, final ChannelHandler handler) {
    bossGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup(ioThreads);
    bootstrap = new ServerBootstrap()
    .group(bossGroup, workerGroup)
    .localAddress(options.localAddress())
    .channel(NioServerSocketChannel.class)
    .childHandler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline()
            .addLast("tracker", channelTracker)
            .addLast("httpCodec", new HttpServerCodec(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE))
            .addLast("httpAggregator", new HttpObjectAggregator(Integer.MAX_VALUE))
            // todo: exception handler
            .addLast("handler", handler)
        ;
      }
    });
    for (Map.Entry<ChannelOption, Object> option : options.toMap().entrySet()) {
      bootstrap.option(option.getKey(), option.getValue());
    }
    for (Map.Entry<ChannelOption, Object> option : options.child().toMap().entrySet()) {
      bootstrap.childOption(option.getKey(), option.getValue());
    }
  }
  
  public InetSocketAddress getLocalAddress() {
    return InetSocketAddress.createFromJavaInetSocketAddress((java.net.InetSocketAddress) serverChannel.localAddress());
  }

  protected void doStart() {
    logger.debug("starting");
    try {
      ChannelFuture bind = bootstrap.bind().sync();
      serverChannel = bind.channel();
      logger.debug("started");
      notifyStarted();
    } catch (RuntimeException e){
      logger.error("can't start", e);
      notifyFailed(e);
      throw e;
    } catch (InterruptedException e) {
      logger.error("can't start", e);
      notifyFailed(e);
      throw new RuntimeException(e);
    }
  }

  protected void doStop() {
    logger.debug("stopping");
    try {
      serverChannel.close().awaitUninterruptibly();
      channelTracker.waitForChildren();
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
      logger.debug("stopped");
      notifyStopped();
    } catch (RuntimeException e) {
      logger.error("can't stop", e);
      notifyFailed(e);
      throw e;
    }
  }
  
  @ChannelHandler.Sharable
  private static class ChannelTracker extends ChannelInitializer {
    private final ChannelGroup group = new DefaultChannelGroup(null);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      if (ClosedChannelException.class.isInstance(cause)) {
        logger.debug("Got " + cause.getClass().getName());
      } else {
        logger.error("Unexpected exception ", cause);
        Http.response(INTERNAL_SERVER_ERROR).
            containing(cause).
            sendAndClose(ctx.channel());
      }
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
      group.add(ch);
    }

    public void waitForChildren() {
      for (Channel channel : group) {
        channel.closeFuture().awaitUninterruptibly();
      }
    }
  }
}
