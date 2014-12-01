package com.codahale.metrics.graphite;

import static org.mockito.Mockito.doAnswer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.codahale.metrics.graphite.InterceptingArgumentCaptor.Interceptor;

@RunWith(MockitoJUnitRunner.class)
public class GraphiteUDPTest {

    public static void main(String[] args) throws Throwable {
        final AtomicReference<DatagramChannel> datagramChannelReference = new AtomicReference<DatagramChannel>();
        GraphiteUDP gudp = new GraphiteUDP(new InetSocketAddress("localhost", 1234), new DatagramChannelFactory() {

            @Override
            protected DatagramChannel createDatagramChannel() throws IOException {
                DatagramChannel datagramChannel = DatagramChannel.open();
                datagramChannel.bind(new InetSocketAddress(InetAddress.getLocalHost(), 12345));
                datagramChannelReference.set(datagramChannel);
                return datagramChannel;
            }
        });

        System.out.println("SENDING");
        gudp.connect();
        //datagramChannelReference.get().write(UTF_8.newEncoder().encode(CharBuffer.wrap("Sending now!\n")));
        for (int i = 0; i < 1; i++) {
            gudp.send("asdf", "1234", 100);
        }
        gudp.flush();
        gudp.close();
    }

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String UNAVAILABLE_HOST = "unknown-host-10el6m7yg56ge7dm.com";

    private final String host = "example.com";
    private final int port = 1234;
    private final InetSocketAddress address = new InetSocketAddress(host, port);

    private DatagramChannelFactory datagramChannelFactory = mock(DatagramChannelFactory.class);
    private DatagramChannelDelegate datagramChannel = mock(DatagramChannelDelegate.class);

    private final ArgumentCaptor<ByteBuffer> bufferCaptor = new InterceptingArgumentCaptor<ByteBuffer>(new Interceptor<ByteBuffer>() {
        @Override
        public ByteBuffer captured(ByteBuffer buffer) {
            /*
            int position = buffer.position();
            int limit = buffer.limit();
            byte[] dst = new byte[buffer.remaining()];
            buffer.get(dst);
            ByteBuffer output = ByteBuffer.wrap(dst);
            output.limit(limit);
            output.position(position);
            return output;
            */
            System.out.println(buffer.position());
            System.out.println(buffer.limit());
            System.out.println(buffer.remaining());
            System.out.println(buffer.capacity());
            return buffer;
        }
    });

    private GraphiteUDP graphite;

    @Before
    public void setUp() throws Exception {
        reset(datagramChannelFactory, datagramChannel);

        when(datagramChannelFactory.createDatagramChannelDelegate()).thenReturn(datagramChannel);

        // Throw UnknownHostException from connect if InetSocketAddress is unresolvable
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                InetSocketAddress socketAddress = ((InetSocketAddress) invocation.getArguments()[0]);
                if (socketAddress.getAddress() == null) {
                    throw new UnknownHostException(socketAddress.getHostName());
                }
                return null;
            }
        }).when(datagramChannel).connect(Matchers.any(InetSocketAddress.class));

        graphite = new GraphiteUDP(address, datagramChannelFactory, UTF_8);
    }

    @Test
    public void usesDatagramChannelFactory() throws Exception {
        graphite.connect();

        verify(datagramChannelFactory).createDatagramChannel();
    }

    @Test
    public void measuresFailures() throws Exception {
        assertThat(graphite.getFailures())
                .isZero();
    }

    @Test
    public void disconnectsFromGraphiteUDP() throws Exception {
        graphite.connect();
        graphite.close();

        verify(datagramChannel).close();
    }

    @Test
    public void doesNotAllowDoubleConnections() throws Exception {
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
        graphite.connect();
        graphite.send("name", "value", 100);
        graphite.close();

        verify(datagramChannel).write(bufferCaptor.capture());

        String string = extractStringFromBuffer();
        assertThat(string).isEqualTo("name value 100\n");
    }

    @Test
    public void sanitizesNames() throws Exception {
        graphite.connect();
        graphite.send("name woo", "value", 100);
        graphite.close();

        verify(datagramChannel).write(bufferCaptor.capture());

        String string = extractStringFromBuffer();
        assertThat(string).isEqualTo("name-woo value 100\n");
    }

    @Test
    public void sanitizesValues() throws Exception {
        graphite.connect();
        graphite.send("name", "value woo", 100);
        graphite.close();

        verify(datagramChannel).write(bufferCaptor.capture());

        String string = extractStringFromBuffer();
        String expected = "name value-woo 100\n";
        System.out.println(string.length());
        System.out.println('"' + string + '"');
        System.out.println(expected.length());
        System.out.println('"' + expected + '"');
        assertThat(string).isEqualTo("name value-woo 100\n");
    }

    private String extractStringFromBuffer() throws CharacterCodingException {
        return UTF_8.newDecoder().decode(bufferCaptor.getValue()).toString();
    }

    @Test
    public void notifiesIfGraphiteIsUnavailable() throws Exception {
        InetSocketAddress unavailableAddress = new InetSocketAddress(UNAVAILABLE_HOST, 1234);
        GraphiteUDP unavailableGraphite = new GraphiteUDP(unavailableAddress, datagramChannelFactory);

        try {
            unavailableGraphite.connect();
            failBecauseExceptionWasNotThrown(UnknownHostException.class);
        } catch (Exception e) {
            assertThat(e.getMessage())
                .isEqualTo(UNAVAILABLE_HOST);
        }
    }

}
