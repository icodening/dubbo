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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.http12.HttpHeaders;
import org.apache.dubbo.remoting.http12.ServerCallListener;
import org.apache.dubbo.remoting.http12.h2.BiStreamServerCallListener;
import org.apache.dubbo.remoting.http12.h2.H2StreamChannel;
import org.apache.dubbo.remoting.http12.h2.Http2Header;
import org.apache.dubbo.remoting.http12.h2.Http2InputMessage;
import org.apache.dubbo.remoting.http12.h2.Http2ServerChannelObserver;
import org.apache.dubbo.remoting.http12.h2.Http2TransportListener;
import org.apache.dubbo.remoting.http12.message.HttpMessageCodec;
import org.apache.dubbo.remoting.http12.message.ListeningDecoder;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.MethodDescriptor;
import org.apache.dubbo.rpc.protocol.tri.h12.http2.GenericHttp2ServerTransportListener;

import java.util.function.Supplier;

public class GrpcHttp2ServerTransportListener extends GenericHttp2ServerTransportListener implements Http2TransportListener {

    private GrpcStreamingDecoder grpcStreamingDecoder;

    public GrpcHttp2ServerTransportListener(H2StreamChannel h2StreamChannel, URL url, FrameworkModel frameworkModel) {
        super(h2StreamChannel, url, frameworkModel);
    }

    @Override
    protected void configurerResponseObserver(Http2ServerChannelObserver responseObserver) {
        responseObserver.setTrailersCustomizer(this::grpcTrailersCustomize);
    }

    private void grpcTrailersCustomize(HttpHeaders httpHeaders, Throwable throwable) {
        httpHeaders.set(GrpcHeaderNames.GRPC_STATUS.getName(), "0");
        if (throwable != null) {
            httpHeaders.set(GrpcHeaderNames.GRPC_MESSAGE.getName(), throwable.getMessage());
        }
    }

    @Override
    protected BiStreamServerCallListener startBiStreaming(RpcInvocation invocation, Invoker<?> invoker, Http2ServerChannelObserver responseObserver) {
        GrpcBiStreamServerCallListener grpcBiStreamServerCallListener = new GrpcBiStreamServerCallListener(invocation, invoker, responseObserver);
        grpcBiStreamServerCallListener.setGrpcListeningDecoder(grpcStreamingDecoder);
        return grpcBiStreamServerCallListener;
    }

    @Override
    protected ListeningDecoder newListeningDecoder(HttpMessageCodec codec, Class<?>[] actualRequestTypes) {
        this.grpcStreamingDecoder = new GrpcStreamingDecoder(actualRequestTypes);
        grpcStreamingDecoder.setHttpMessageCodec(codec);
        ServerCallListener serverCallListener = startListener(getRpcInvocation(), getMethodDescriptor(), getInvoker());
        grpcStreamingDecoder.setListener(new GrpcServerDecodeListener(() -> serverCallListener));
        return grpcStreamingDecoder;
    }

    @Override
    protected void onMetadataCompletion(Http2Header metadata) {
        super.onMetadataCompletion(metadata);
        MethodDescriptor.RpcType rpcType = getMethodDescriptor().getRpcType();
        switch (rpcType) {
            case UNARY:
            case SERVER_STREAM:
                this.grpcStreamingDecoder.request(2);
                break;
            case BI_STREAM:
            case CLIENT_STREAM:
                this.grpcStreamingDecoder.request(1);
                break;
            default:
                throw new IllegalStateException("Can not reach here");
        }
    }

    @Override
    protected void doOnData(Http2InputMessage message) {
        super.doOnData(message);
        if (message.isEndStream()) {
            serverCallListener.onComplete();
        }
    }

    @Override
    protected GrpcStreamingDecoder getListeningDecoder() {
        return (GrpcStreamingDecoder) super.getListeningDecoder();
    }

    private static class GrpcServerDecodeListener implements ListeningDecoder.Listener {

        private final Supplier<ServerCallListener> serverCallListener;

        private GrpcServerDecodeListener(Supplier<ServerCallListener> serverCallListener) {
            this.serverCallListener = serverCallListener;
        }

        @Override
        public void onMessage(Object message) {
            this.serverCallListener.get().onMessage(message);
        }

        @Override
        public void onClose() {
            this.serverCallListener.get().onComplete();
        }
    }
}
