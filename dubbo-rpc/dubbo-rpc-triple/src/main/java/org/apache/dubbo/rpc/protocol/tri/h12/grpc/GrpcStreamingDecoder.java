/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.protocol.tri.h12.grpc;

import org.apache.dubbo.remoting.http12.CompositeInputStream;
import org.apache.dubbo.remoting.http12.exception.DecodeException;
import org.apache.dubbo.remoting.http12.message.HttpMessageCodec;
import org.apache.dubbo.remoting.http12.message.ListeningDecoder;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.protocol.tri.compressor.DeCompressor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class GrpcStreamingDecoder implements ListeningDecoder {

    private static final int HEADER_LENGTH = 5;
    private static final int COMPRESSED_FLAG_MASK = 1;
    private static final int RESERVED_MASK = 0xFE;
    private boolean compressedFlag;
    private long pendingDeliveries;
    private boolean inDelivery = false;
    private boolean closing;
    private boolean closed;
    private GrpcDecodeState state = GrpcDecodeState.HEADER;
    private int requiredLength = HEADER_LENGTH;
    private DeCompressor decompressor = DeCompressor.NONE;

    private final CompositeInputStream accumulate = new CompositeInputStream();

    private final HttpMessageCodec codec = GrpcCompositeCodec.INSTANCE;

    private final Class<?>[] targetTypes;

    private Listener listener;

    public GrpcStreamingDecoder(Class<?>[] targetTypes) {
        this.targetTypes = targetTypes;
    }

    public void setDecompressor(DeCompressor decompressor) {
        this.decompressor = decompressor;
    }

    @Override
    public void decode(InputStream inputStream) throws DecodeException {
        if (closing || closed) {
            // ignored
            return;
        }
        accumulate.addInputStream(inputStream);
        deliver();
    }

    public void request(int numMessages) {
        pendingDeliveries += numMessages;
        deliver();
    }

    @Override
    public void close() {
        closing = true;
        deliver();
    }

    @Override
    public HttpMessageCodec getCodec() {
        return codec;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void deliver() {
        // We can have reentrancy here when using a direct executor, triggered by calls to
        // request more messages. This is safe as we simply loop until pendingDelivers = 0
        if (inDelivery) {
            return;
        }
        inDelivery = true;
        try {
            // Process the uncompressed bytes.
            while (pendingDeliveries > 0 && hasEnoughBytes()) {
                switch (state) {
                    case HEADER:
                        processHeader();
                        break;
                    case PAYLOAD:
                        // Read the body and deliver the message.
                        processBody();

                        // Since we've delivered a message, decrement the number of pending
                        // deliveries remaining.
                        pendingDeliveries--;
                        break;
                    default:
                        throw new AssertionError("Invalid state: " + state);
                }
            }
            if (closing) {
                if (!closed) {
                    closed = true;
                    accumulate.close();
                    listener.onClose();
                }
            }
        } catch (IOException e) {
            throw new DecodeException(e);
        } finally {
            inDelivery = false;
        }
    }

    /**
     * Processes the GRPC message body, which depending on frame header flags may be compressed.
     */
    private void processBody() throws IOException {
        // There is no reliable way to get the uncompressed size per message when it's compressed,
        // because the uncompressed bytes are provided through an InputStream whose total size is
        // unknown until all bytes are read, and we don't know when it happens.
        byte[] stream = compressedFlag ? getCompressedBody() : getUncompressedBody();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(stream);
        Object[] decodeParameters = codec.decode(inputStream, targetTypes);
        this.listener.onMessage(decodeParameters);

        // Done with this frame, begin processing the next header.
        state = GrpcDecodeState.HEADER;
        requiredLength = HEADER_LENGTH;
    }

    private byte[] getCompressedBody() throws IOException {
        final byte[] compressedBody = getUncompressedBody();
        return decompressor.decompress(compressedBody);
    }

    private byte[] getUncompressedBody() throws IOException {
        byte[] data = new byte[requiredLength];
        accumulate.read(data, 0, requiredLength);
        return data;
    }

    private void processHeader() throws IOException {
        int type = accumulate.read();
        if ((type & RESERVED_MASK) != 0) {
            throw new RpcException("gRPC frame header malformed: reserved bits not zero");
        }
        compressedFlag = (type & COMPRESSED_FLAG_MASK) != 0;

        byte[] lengthBytes = new byte[4];
        accumulate.read(lengthBytes);
        requiredLength = bytesToInt(lengthBytes);

        // Continue reading the frame body.
        state = GrpcDecodeState.PAYLOAD;
    }

    private boolean hasEnoughBytes() {
        return requiredLength - accumulate.available() <= 0;
    }

    private static int bytesToInt(byte[] bytes) {
        return (bytes[0] << 24) & 0xFF | (bytes[1] << 16) & 0xFF | (bytes[2] << 8) & 0xFF | (bytes[3]) & 0xFF;
    }

    private enum GrpcDecodeState {
        HEADER,
        PAYLOAD
    }
}
