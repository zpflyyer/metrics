package com.codahale.metrics.graphite;

import java.io.IOException;
import java.nio.channels.DatagramChannel;

public abstract class DatagramChannelFactory {

    private static final DatagramChannelFactory DEFAULT = new DatagramChannelFactory() {

        @Override
        public DatagramChannel createDatagramChannel() throws IOException {
            return DatagramChannel.open();
        }

    };

    public static DatagramChannelFactory getDefault() {
        return DEFAULT;
    }

    public abstract DatagramChannel createDatagramChannel() throws IOException;

}
