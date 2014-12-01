package com.codahale.metrics.graphite;

import java.io.IOException;
import java.nio.channels.DatagramChannel;

public abstract class DatagramChannelFactory {

    public static final DatagramChannelFactory DEFAULT = new DatagramChannelFactory() {

        @Override
        public DatagramChannel createDatagramChannel() throws IOException {
            return DatagramChannel.open();
        }

    };

    public DatagramChannelDelegate createDatagramChannelDelegate() throws IOException {
        return new DatagramChannelDelegateImpl(createDatagramChannel());
    }

    protected abstract DatagramChannel createDatagramChannel() throws IOException;

}
