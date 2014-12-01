package com.codahale.metrics.graphite;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class DatagramChannelDelegateImpl implements DatagramChannelDelegate {

    private final DatagramChannel delegate;

    public DatagramChannelDelegateImpl(DatagramChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void connect(InetSocketAddress address) throws IOException {
        delegate.connect(address);
    }

    @Override
    public int write(ByteBuffer buffer) throws IOException {
        return delegate.write(buffer);
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public boolean isClosed() {
        return delegate.socket().isClosed();
    }

}
