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
package org.apache.dubbo.rpc.protocol.tri.h12.http2;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.threadpool.serial.SerializingExecutor;
import org.apache.dubbo.remoting.http12.ServerCallListener;
import org.apache.dubbo.remoting.http12.ServerStreamServerCallListener;
import org.apache.dubbo.remoting.http12.UnaryServerCallListener;
import org.apache.dubbo.remoting.http12.h2.BiStreamServerCallListener;
import org.apache.dubbo.remoting.http12.h2.H2StreamChannel;
import org.apache.dubbo.remoting.http12.h2.Http2Header;
import org.apache.dubbo.remoting.http12.h2.Http2InputMessage;
import org.apache.dubbo.remoting.http12.h2.Http2ServerChannelObserver;
import org.apache.dubbo.remoting.http12.h2.Http2TransportListener;
import org.apache.dubbo.remoting.http12.message.DefaultListeningDecoder;
import org.apache.dubbo.remoting.http12.message.HttpMessageCodec;
import org.apache.dubbo.remoting.http12.message.JsonCodec;
import org.apache.dubbo.remoting.http12.message.ListeningDecoder;
import org.apache.dubbo.rpc.CancellationContext;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.executor.ExecutorSupport;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.MethodDescriptor;
import org.apache.dubbo.rpc.protocol.tri.h12.AbstractServerTransportListener;

import java.util.concurrent.Executor;

public class GenericHttp2ServerTransportListener extends AbstractServerTransportListener<Http2Header, Http2InputMessage> implements Http2TransportListener {

    protected ServerCallListener serverCallListener;

    protected final Http2ServerChannelObserver responseObserver;

    protected final H2StreamChannel h2StreamChannel;

    private final ExecutorSupport executorSupport;

    protected SerializingExecutor serializingExecutor;

    public GenericHttp2ServerTransportListener(H2StreamChannel h2StreamChannel, URL url, FrameworkModel frameworkModel) {
        super(frameworkModel);
        this.h2StreamChannel = h2StreamChannel;
        this.responseObserver = new Http2ServerChannelObserver(h2StreamChannel);
        this.responseObserver.setHttpMessageCodec(JsonCodec.INSTANCE);
        this.executorSupport = ExecutorRepository.getInstance(url.getOrDefaultApplicationModel()).getExecutorSupport(url);
    }

    @Override
    public H2StreamChannel getHttpChannel() {
        return this.h2StreamChannel;
    }

    @Override
    protected Executor initializeExecutor(Http2Header metadata) {
        if (serializingExecutor == null) {
            Executor executor = executorSupport.getExecutor(metadata);
            this.serializingExecutor = new SerializingExecutor(executor);
        }
        return this.serializingExecutor;
    }

    protected ServerCallListener startListener(RpcInvocation invocation,
                                               MethodDescriptor methodDescriptor,
                                               Invoker<?> invoker) {
        Http2ServerChannelObserver responseObserver = getResponseObserver();
        configurerResponseObserver(responseObserver);
        CancellationContext cancellationContext = RpcContext.getCancellationContext();
        responseObserver.setCancellationContext(cancellationContext);
        switch (methodDescriptor.getRpcType()) {
            case UNARY:
                this.serverCallListener = startUnary(invocation, invoker, responseObserver);
                return this.serverCallListener;
            case SERVER_STREAM:
                this.serverCallListener = startServerStreaming(invocation, invoker, responseObserver);
                return this.serverCallListener;
            case BI_STREAM:
            case CLIENT_STREAM:
                this.serverCallListener = startBiStreaming(invocation, invoker, responseObserver);
                return this.serverCallListener;
            default:
                throw new IllegalStateException("Can not reach here");
        }
    }

    protected void configurerResponseObserver(Http2ServerChannelObserver responseObserver) {

    }

    public Http2ServerChannelObserver getResponseObserver() {
        return responseObserver;
    }

    @Override
    public void cancelByRemote(long errorCode) {
        this.serverCallListener.onCancel(errorCode);
    }

    @Override
    protected ListeningDecoder newListeningDecoder(HttpMessageCodec codec, Class<?>[] actualRequestTypes) {
        DefaultListeningDecoder listeningDecoder = new DefaultListeningDecoder(codec, actualRequestTypes);
        ServerCallListener serverCallListener = startListener(getRpcInvocation(), getMethodDescriptor(), getInvoker());
        listeningDecoder.setListener(serverCallListener::onMessage);
        return listeningDecoder;
    }

    protected void doOnMetadata(Http2Header metadata) {
        try {
            if (metadata.isEndStream()) {
                return;
            }
            super.doOnMetadata(metadata);
        } catch (Throwable e) {
            this.responseObserver.onError(e);
        }
    }

    @Override
    protected void onMetadataCompletion(Http2Header metadata) {
        super.onMetadataCompletion(metadata);
        Http2ServerChannelObserver responseObserver = this.responseObserver;
        responseObserver.setHttpMessageCodec(getHttpMessageCodec());
    }

    @Override
    protected void doOnData(Http2InputMessage message) {
        super.doOnData(message);
        if (message.isEndStream()) {
            serverCallListener.onComplete();
        }
    }

    protected UnaryServerCallListener startUnary(RpcInvocation invocation,
                                                 Invoker<?> invoker,
                                                 Http2ServerChannelObserver responseObserver) {
        return new UnaryServerCallListener(invocation, invoker, responseObserver);
    }

    protected ServerStreamServerCallListener startServerStreaming(RpcInvocation invocation, Invoker<?> invoker, Http2ServerChannelObserver responseObserver) {
        return new ServerStreamServerCallListener(invocation, invoker, responseObserver);
    }

    protected BiStreamServerCallListener startBiStreaming(RpcInvocation invocation, Invoker<?> invoker, Http2ServerChannelObserver responseObserver) {
        return new BiStreamServerCallListener(invocation, invoker, responseObserver);
    }
}
