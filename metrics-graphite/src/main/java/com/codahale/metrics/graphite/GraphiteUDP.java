package com.codahale.metrics.graphite;

import java.io.IOException;
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

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(MAX_DATAGRAM_SIZE);
    private DatagramChannel datagramChannel = null;
    private int failures;

    /**
     * Creates a new client which sends data to given address using UDP
     *
     * @param hostname The hostname of the Carbon server
     * @param port The port of the Carbon server
     */
    public GraphiteUDP(String hostname, int port) {
        this(hostname, port, UTF_8);
    }

    /**
     * Creates a new client which sends data to given address using UDP
     *
     * @param hostname The hostname of the Carbon server
     * @param port The port of the Carbon server
     */
    public GraphiteUDP(String hostname, int port, Charset charset) {
        this.hostname = hostname;
        this.port = port;
        this.address = null;
        this.charset = charset;
    }

    /**
     * Creates a new client which sends data to given address using UDP
     *
     * @param address the address of the Carbon server
     */
    public GraphiteUDP(InetSocketAddress address) {
        this(address, UTF_8);
    }

    /**
     * Creates a new client which sends data to given address using UDP
     *
     * @param address the address of the Carbon server
     */
    public GraphiteUDP(InetSocketAddress address, Charset charset) {
        this.hostname = null;
        this.port = -1;
        this.address = address;
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

        datagramChannel = DatagramChannel.open();
    }

    @Override
    public boolean isConnected() {
    		return datagramChannel != null && !datagramChannel.socket().isClosed();
    }

    @Override
    public void send(String name, String value, long timestamp) throws IOException {
        // Underlying socket can be closed by ICMP
        if (!isConnected()) {
            connect();
        }

        byte[] nameBytes = sanitize(name).getBytes(charset);
        byte[] valueBytes = sanitize(value).getBytes(charset);
        byte[] timestampBytes = sanitize(Long.toString(timestamp)).getBytes(charset);

        int length = nameBytes.length + valueBytes.length + timestampBytes.length + 3;

        if (buffer.capacity() < length) {
            buffer
                .put(nameBytes)
                .putChar(' ')
                .put(valueBytes)
                .putChar(' ')
                .put(timestampBytes)
                .putChar('\n');
           }
    }

    @Override
    public int getFailures() {
        return failures;
    }

    @Override
    public void flush() throws IOException {
        try {
            datagramChannel.send(buffer, address);
            this.failures = 0;
        } catch (IOException e) {
            failures++;
            throw e;
        } finally {
            buffer.rewind();
        }
    }

    @Override
    public void close() throws IOException {
        // Leave channel & socket open for next metrics
    }

    protected String sanitize(String s) {
        return WHITESPACE.matcher(s).replaceAll("-");
    }

}
