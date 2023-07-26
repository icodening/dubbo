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

import com.google.protobuf.Message;
import org.apache.dubbo.remoting.http12.exception.DecodeException;
import org.apache.dubbo.remoting.http12.exception.EncodeException;
import org.apache.dubbo.remoting.http12.message.HttpMessageCodec;
import org.apache.dubbo.remoting.http12.message.MediaType;
import org.apache.dubbo.rpc.RpcException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * compatible low version.
 * version < 3.3
 *
 * @since 3.3
 */
public class CompatibleLowVersionCodec implements HttpMessageCodec {

    private static final int RESERVED_MASK = 0xFE;

    private static final int COMPRESSED_FLAG_MASK = 1;

    private static final MediaType MEDIA_TYPE = new MediaType("application", "grpc+proto");

    public static HttpMessageCodec INSTANCE = new CompatibleLowVersionCodec();

    private final ProtobufHttpMessageCodec protobufHttpMessageCodec = new ProtobufHttpMessageCodec();

    private final WrapperHttpMessageCodec wrapperHttpMessageCodec = new WrapperHttpMessageCodec();

    @Override
    public void encode(OutputStream outputStream, Object data) throws EncodeException {
        //protobuf
        //TODO int compressed = Identity.MESSAGE_ENCODING.equals(requestMetadata.compressor.getMessageEncoding()) ? 0 : 1;
        try {
            int compressed = 0;
            outputStream.write(compressed);
            if (data instanceof Message) {
                int serializedSize = ((Message) data).getSerializedSize();
                //write length
                writeLength(outputStream, serializedSize);
                protobufHttpMessageCodec.encode(outputStream, data);
                return;
            }
            //wrapper
            wrapperHttpMessageCodec.encode(outputStream, data);
        } catch (IOException e) {
            throw new EncodeException(e);
        }
    }

    @Override
    public void encode(OutputStream outputStream, Object[] data) throws EncodeException {
        if (data[0] instanceof Message) {
            int serializedSize = ((Message) data[0]).getSerializedSize();
            //write length
            writeLength(outputStream, serializedSize);
            protobufHttpMessageCodec.encode(outputStream, data);
            return;
        }
        //wrapper
        wrapperHttpMessageCodec.encode(outputStream, data);
    }

    @Override
    public Object decode(InputStream inputStream, Class<?> targetType) throws DecodeException {
        try {
            InputStream bis = toDataInputStream(inputStream);
            if (Message.class.isAssignableFrom(targetType)) {
                return protobufHttpMessageCodec.decode(bis, targetType);
            }
            //wrapper
            return wrapperHttpMessageCodec.decode(bis, targetType);
        } catch (IOException e) {
            throw new DecodeException(e);
        }
    }

    @Override
    public Object[] decode(InputStream inputStream, Class<?>[] targetTypes) throws DecodeException {
        if (hasProtobuf(targetTypes)) {
            return new Object[]{this.decode(inputStream, targetTypes[0])};
        }
        try {
            return wrapperHttpMessageCodec.decode(toDataInputStream(inputStream), targetTypes);
        } catch (IOException e) {
            throw new DecodeException(e);
        }
    }

    private InputStream toDataInputStream(InputStream inputStream) throws IOException {
        int type = inputStream.read();
        if ((type & RESERVED_MASK) != 0) {
            throw new RpcException("gRPC frame header malformed: reserved bits not zero");
        }
        boolean ignoreCompressFlag = (type & COMPRESSED_FLAG_MASK) != 0;
        byte[] lengthBytes = new byte[4];
        inputStream.read(lengthBytes);
        int length = bytesToInt(lengthBytes);
        byte[] data = new byte[length];
        inputStream.read(data, 0, length);
        return new ByteArrayInputStream(data);
    }

    private static int bytesToInt(byte[] bytes) {
        return (bytes[0] << 24) & 0xFF |
            (bytes[1] << 16) & 0xFF |
            (bytes[2] << 8) & 0xFF |
            (bytes[3]) & 0xFF;
    }

    private boolean hasProtobuf(Class<?>[] targetTypes) {
        for (Class<?> targetType : targetTypes) {
            if (Message.class.isAssignableFrom(targetType)) {
                return true;
            }
        }
        return false;
    }

    private static void writeLength(OutputStream outputStream, int length) {
        try {
            outputStream.write(((length >> 24) & 0xFF));
            outputStream.write(((length >> 16) & 0xFF));
            outputStream.write(((length >> 8) & 0xFF));
            outputStream.write((length & 0xFF));
        } catch (IOException e) {
            throw new EncodeException(e);
        }
    }

    @Override
    public MediaType contentType() {
        return MEDIA_TYPE;
    }
}
