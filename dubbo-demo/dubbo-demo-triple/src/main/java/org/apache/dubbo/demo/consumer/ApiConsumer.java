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
package org.apache.dubbo.demo.consumer;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.demo.GreeterService;
import org.apache.dubbo.demo.hello.GrpcGreeter;
import org.apache.dubbo.demo.hello.HelloReply;
import org.apache.dubbo.demo.hello.HelloRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ApiConsumer {
    public static void main(String[] args) throws InterruptedException, IOException {
        ReferenceConfig<GreeterService> referenceConfig = new ReferenceConfig<>();
        referenceConfig.setInterface(GreeterService.class);
        referenceConfig.setCheck(false);
        referenceConfig.setProtocol(CommonConstants.TRIPLE);
        referenceConfig.setLazy(true);
        referenceConfig.setTimeout(1000);

        ReferenceConfig<GrpcGreeter> referenceConfig2 = new ReferenceConfig<>();
        referenceConfig2.setInterface(GrpcGreeter.class);
        referenceConfig2.setCheck(false);
        referenceConfig2.setProtocol(CommonConstants.TRIPLE);
        referenceConfig2.setLazy(true);
        referenceConfig2.setTimeout(1000);

        List<ReferenceConfig> referenceConfigs = Arrays.asList(referenceConfig, referenceConfig2);


        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        bootstrap.application(new ApplicationConfig("dubbo-demo-triple-api-consumer"))
            .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
            .protocol(new ProtocolConfig(CommonConstants.TRIPLE, -1))
            .references(referenceConfigs)
            .start();

        GreeterService greeterService = referenceConfig.get();
        GrpcGreeter grpcGreeter = referenceConfig2.get();
        System.out.println("dubbo referenceConfig started");
        try {
            doGrpcStub(grpcGreeter);
            doWrapper(greeterService);

//            StreamObserver<String> request = greeterService.biStream(new StreamObserver<String>() {
//                @Override
//                public void onNext(String data) {
//                    System.out.println(data);
//                }
//
//                @Override
//                public void onError(Throwable throwable) {
//                    throwable.printStackTrace();
//                }
//
//                @Override
//                public void onCompleted() {
//
//                }
//            });
//            for (int i = 0; i < 5; i++) {
//                request.onNext("hello " + i);
//            }
//            Thread.sleep(1000);
//            for (int i = 0; i < 5; i++) {
//                request.onNext("world " + i);
//            }
//            request.onCompleted();
//            for (int i = 0; i < 10; i++) {
//                final HelloReply reply = greeterService.sayHello(HelloRequest.newBuilder()
//                    .setName("triple")
//                    .build());
//                System.out.println((i+1) + " Reply: " + reply.getMessage());
//            }

//
//            CompletableFuture<String> sayHelloAsync = greeterService.sayHelloAsync("triple");
//            System.out.println("Async Reply: "+sayHelloAsync.get());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.in.read();
    }

    private static void doWrapper(GreeterService greeterService) {
        HelloRequest unary = HelloRequest.newBuilder().setName("greeterService unary").build();
        System.out.println(greeterService.sayHello(unary));

        greeterService.serverStream("greeterService server-stream", new StreamObserver<String>() {
            @Override
            public void onNext(String data) {
                System.out.println(data);
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {
                System.out.println("greeterService server-stream complete.");
            }
        });
    }

    private static void doGrpcStub(GrpcGreeter grpcGreeter){
        HelloReply reply = grpcGreeter.exchange(HelloRequest.newBuilder().setName("hello world grpc").build());
        System.out.println(reply.getMessage());

        //server stream
        HelloRequest serverStream = HelloRequest.newBuilder().setName("hello world grpc. server stream").build();
        grpcGreeter.serverStream(serverStream, new StreamObserver<HelloReply>() {
            @Override
            public void onNext(HelloReply data) {
                System.out.println(data.getMessage());
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {
                System.out.println("server stream complete.");
            }
        });

        //bistream
        StreamObserver<HelloRequest> requestObserver = grpcGreeter.biStream(new StreamObserver<HelloReply>() {
            @Override
            public void onNext(HelloReply data) {
                System.out.println(data);
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        });
        HelloRequest biStream = HelloRequest.newBuilder().setName("hello world grpc. bi stream").build();
        requestObserver.onNext(biStream);
        requestObserver.onCompleted();
    }
}
