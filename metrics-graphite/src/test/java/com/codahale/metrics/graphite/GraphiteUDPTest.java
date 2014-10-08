package com.codahale.metrics.graphite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.failBecauseExceptionWasNotThrown;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(DatagramChannel.class)
public class GraphiteUDPTest {

    public static void main(String[] args) throws Throwable {
        GraphiteUDP gudp = new GraphiteUDP(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1234), new DatagramChannelFactory() {

            @Override
            public DatagramChannel createDatagramChannel() throws IOException {
                DatagramChannel datagramChannel = DatagramChannel.open();
                datagramChannel.bind(new InetSocketAddress(InetAddress.getLocalHost(), 12345));
                return datagramChannel;
            }
        });

        gudp.connect();
        gudp.send("asdf", "1234", System.currentTimeMillis());
        gudp.close();
    }

    private final String host = "example.com";
    private final int port = 1234;
    private final InetSocketAddress address = new InetSocketAddress(host, port);

    private DatagramChannelFactory datagramChannelFactory = mock(DatagramChannelFactory.class);
    private DatagramChannel datagramChannel = mock(DatagramChannel.class);
    private DatagramSocket datagramSocket = mock(DatagramSocket.class);

    private final ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);

    private GraphiteUDP graphite;

    @Before
    public void setUp() throws Exception {
        when(datagramChannelFactory.createDatagramChannel()).thenReturn(datagramChannel);
        when(datagramChannel.socket()).thenReturn(datagramSocket);
        doNothing().when(datagramChannel).close(); // Along with using PowerMock, avoids null pointer exception from final method
    }

    @Test
    public void usesDatagramChannelFactory() throws Exception {
        graphite = new GraphiteUDP(address, datagramChannelFactory);
        graphite.connect();

        verify(datagramChannelFactory).createDatagramChannel();
    }

    @Test
    public void measuresFailures() throws Exception {
        graphite = new GraphiteUDP(address, datagramChannelFactory);
        assertThat(graphite.getFailures())
                .isZero();
    }

    @Test
    public void disconnectsFromGraphiteUDP() throws Exception {
        graphite = new GraphiteUDP(address, datagramChannelFactory);
        graphite.connect();
        graphite.close();

        verify(datagramChannel).close();
    }

    @Test
    public void doesNotAllowDoubleConnections() throws Exception {
        graphite = new GraphiteUDP(address, datagramChannelFactory);
        graphite.connect();
        try {
            graphite.connect();
            failBecauseExceptionWasNotThrown(IllegalStateException.class);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .isEqualTo("Already connected");
        }
    }

    @Test
    public void writesValuesToGraphiteUDP() throws Exception {
        graphite = new GraphiteUDP(address, datagramChannelFactory);
        graphite.connect();
        graphite.send("name", "value", 100);
        graphite.close();

        verify(datagramChannel).send(bufferCaptor.capture(), any(SocketAddress.class));

        assertThat(new String(bufferCaptor.getValue().array()))
                .isEqualTo("name value 100\n");
    }

    @Test
    public void sanitizesNames() throws Exception {
        graphite = new GraphiteUDP(address, datagramChannelFactory);
        graphite.connect();
        graphite.send("name woo", "value", 100);
        graphite.close();

        assertThat(new String(bufferCaptor.getValue().array()))
                .isEqualTo("name-woo value 100\n");
    }

    @Test
    public void sanitizesValues() throws Exception {
        graphite = new GraphiteUDP(address, datagramChannelFactory);
        graphite.connect();
        graphite.send("name", "value woo", 100);
        graphite.close();

        assertThat(new String(bufferCaptor.getValue().array()))
                .isEqualTo("name value-woo 100\n");
    }

    @Test
    public void notifiesIfGraphiteIsUnavailable() throws Exception {
        final String unavailableHost = "unknown-host-10el6m7yg56ge7dm.com";
        InetSocketAddress unavailableAddress = new InetSocketAddress(unavailableHost, 1234);
        GraphiteUDP unavailableGraphite = new GraphiteUDP(unavailableAddress, datagramChannelFactory);

        try {
            unavailableGraphite.connect();
            failBecauseExceptionWasNotThrown(UnknownHostException.class);
        } catch (Exception e) {
            assertThat(e.getMessage())
                .isEqualTo(unavailableHost);
        }
    }

}
