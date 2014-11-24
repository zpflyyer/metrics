package com.codahale.metrics.graphite;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

/**
 * A client to a Carbon server using unconnected UDP
 */
public class GraphiteUDP implements GraphiteSender {

    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final int MAX_DATAGRAM_SIZE = 576;

    private final String hostname;
    private final int port;
    private InetSocketAddress address;
    private final Charset charset;
    private final DatagramChannelFactory datagramChannelFactory;

    private final ByteBuffer buffer = ByteBuffer.allocate(MAX_DATAGRAM_SIZE);
    private DatagramChannel datagramChannel = null;
    private int failures;

    /**
     * Creates a new client which sends data to given address using UDP
     *
     * @param hostname The hostname of the Carbon server
     * @param port The port of the Carbon server
     */
    public GraphiteUDP(String hostname, int port) {
        this(hostname, port, DatagramChannelFactory.getDefault(), UTF_8);
    }

    /**
     * Creates a new client which sends data to given address using UDP
     *
     * @param hostname The hostname of the Carbon server
     * @param port The port of the Carbon server
     * @param datagramChannelFactory the datagram channel factory
     */
    public GraphiteUDP(String hostname, int port, DatagramChannelFactory datagramChannelFactory) {
        this(hostname, port, datagramChannelFactory, UTF_8);
    }

    /**
     * Creates a new client which sends data to given address using UDP
     *
     * @param hostname The hostname of the Carbon server
     * @param port The port of the Carbon server
     * @param datagramChannelFactory the datagram channel factory
     * @param charset the character set used by the server
     */
    public GraphiteUDP(String hostname, int port, DatagramChannelFactory datagramChannelFactory, Charset charset) {
        this.hostname = hostname;
        this.port = port;
        this.address = null;
        this.datagramChannelFactory = datagramChannelFactory;
        this.charset = charset;
    }

    /**
     * Creates a new client which sends data to given address using UDP
     *
     * @param address the address of the Carbon server
     */
    public GraphiteUDP(InetSocketAddress address) {
        this(address, DatagramChannelFactory.getDefault(), UTF_8);
    }

    /**
     * Creates a new client which sends data to given address using UDP
     *
     * @param address the address of the Carbon server
     * @param datagramChannelFactory the datagram channel factory
     */
    public GraphiteUDP(InetSocketAddress address, DatagramChannelFactory datagramChannelFactory) {
        this(address, datagramChannelFactory, UTF_8);
    }

    /**
     * Creates a new client which sends data to given address using UDP
     *
     * @param address the address of the Carbon server
     * @param datagramChannelFactory the datagram channel factory
     * @param charset the character set used by the server
     */
    public GraphiteUDP(InetSocketAddress address, DatagramChannelFactory datagramChannelFactory, Charset charset) {
        this.hostname = null;
        this.port = -1;
        this.address = address;
        this.datagramChannelFactory = datagramChannelFactory;
        this.charset = charset;
    }

    @Override
    public void connect() throws IllegalStateException, IOException {
        // Only open the channel the first time...
        if (isConnected()) {
            throw new IllegalStateException("Already connected");
        }

        if (datagramChannel != null) {
            datagramChannel.close();
        }

        // Resolve hostname
        if (hostname != null) {
            address = new InetSocketAddress(hostname, port);
        }

        datagramChannel = datagramChannelFactory.createDatagramChannel();

        datagramChannel.connect(address);
    }

    @Override
    @SuppressWarnings("resource")
    public boolean isConnected() {
        if (datagramChannel == null) {
            return false;
        }
        DatagramSocket socket = datagramChannel.socket();
        return socket != null && !socket.isClosed();
    }

    @Override
    public void send(String name, String value, long timestamp) throws IOException {
        byte[] nameBytes = sanitize(name).getBytes(charset);
        byte[] valueBytes = sanitize(value).getBytes(charset);
        byte[] timestampBytes = sanitize(Long.toString(timestamp)).getBytes(charset);

        int length = nameBytes.length + valueBytes.length + timestampBytes.length + 3;

        if (buffer.remaining() < length) {
            flush();
        }

        buffer
            .put(nameBytes)
            .putChar(' ')
            .put(valueBytes)
            .putChar(' ')
            .put(timestampBytes)
            .putChar('\n');
    }

    @Override
    public int getFailures() {
        return failures;
    }

    @Override
    public void flush() throws IOException {
        if (buffer.position() > 0) {
            // Underlying socket can be closed by ICMP
            if (!isConnected()) {
                connect();
            }

            try {
                buffer.flip();
                datagramChannel.write(buffer);
                this.failures = 0;
            } catch (IOException e) {
                failures++;
                throw e;
            } finally {
                buffer.clear();
            }
        }
    }

    @Override
    public void close() throws IOException {
        flush();

        datagramChannel.close();
    }

    protected String sanitize(String s) {
        return WHITESPACE.matcher(s).replaceAll("-");
    }

}
