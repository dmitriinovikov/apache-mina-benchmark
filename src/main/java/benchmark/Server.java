package benchmark;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Server extends IoHandlerAdapter {

    private final IoAcceptor acceptor;

    public Server() {
        acceptor = new NioSocketAcceptor();
    }

    public void start(int port) throws IOException {
        ((NioSocketAcceptor) acceptor).getSessionConfig().setTcpNoDelay(true);

        acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(
                new TextLineCodecFactory()));

        acceptor.setHandler(this);
        acceptor.bind(new InetSocketAddress(port));
    }

    public void stop() throws IOException {
        acceptor.dispose(true);
    }
}
