package com.codahale.metrics.graphite;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.WritableByteChannel;

public interface DatagramChannelDelegate extends Closeable, WritableByteChannel {

    void connect(InetSocketAddress address) throws IOException;

    boolean isClosed();

}
