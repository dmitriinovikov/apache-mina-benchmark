package benchmark;

import lombok.SneakyThrows;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.service.IoServiceStatistics;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.SocketConnector;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class Client extends IoHandlerAdapter {

    private final IoConnector connector;

    private final List<MessageSentListener> listeners = new CopyOnWriteArrayList<>();

    @SneakyThrows
    public Client() {
        connector = new NioSocketConnector();
    }

    public void init() {
        ((SocketConnector) connector).getSessionConfig().setTcpNoDelay(true);

        connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(
                new TextLineCodecFactory()));

        connector.getFilterChain().addLast("messageSent", new IoFilterAdapter() {
            @Override
            public void messageSent(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
                for (MessageSentListener listener : listeners) {
                    listener.messageSent(session, writeRequest.getOriginalMessage());
                }
            }
        });

        connector.setHandler(this);
    }

    public void stop() throws IOException {
        connector.dispose(true);
    }

    public void registerListener(MessageSentListener listener) {
        listeners.add(listener);
    }

    public void unregisterListener(MessageSentListener listener) {
        listeners.remove(listener);
    }

    @SneakyThrows
    public IoSession establishNewSession(int port) {
        return connector
                .connect(new InetSocketAddress(port))
                .await()
                .getSession();
    }

    public interface MessageSentListener {

        void messageSent(IoSession session, Object message);

    }

    /**
     * "Dirty hack" to imitate no-blocking IoServiceStatistics
     * Set dummy lock to IoServiceStatistics#throughputCalculationLock via java reflection
     */
    public void makeIoServiceStatisticsNonBlocking() throws NoSuchFieldException, IllegalAccessException {
        IoServiceStatistics statistics = connector.getStatistics();

        Field throughputCalculationLockField = statistics.getClass().getDeclaredField("throughputCalculationLock");

        throughputCalculationLockField.setAccessible(true);
        throughputCalculationLockField.set(statistics, new NoOpLock());
        throughputCalculationLockField.setAccessible(false);
    }

    private static class NoOpLock implements Lock {

        @Override
        public void lock() {
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {

        }

        @Override
        public boolean tryLock() {
            return false;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public void unlock() {
        }

        @Override
        public Condition newCondition() {
            return null;
        }
    }
}
