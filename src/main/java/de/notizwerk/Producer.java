/*
Copyright (c) 2011-2015 The original author or authors
------------------------------------------------------
All rights reserved. This program and the accompanying materials
are made available under the terms of the Eclipse Public License v1.0
and Apache License v2.0 which accompanies this distribution.

    The Eclipse Public License is available at
    http://www.eclipse.org/legal/epl-v10.html

    The Apache License v2.0 is available at
    http://www.opensource.org/licenses/apache2.0.php

You may elect to redistribute this code under either of these licenses.
*/
package de.notizwerk;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Tarek El-Sibay
 */
public class Producer extends AbstractVerticle {

	
	private static final int SEND_DELAY_IN_MSEC = 100;
	private static final int REPORT_DELAY_IN_MSEC = 1000;
   	private final String name = "producer_" + AppStarter.TIME_FILE_FORMATTER.format(ZonedDateTime.now());

    private long msgPerSec = 100;
    private boolean sendingActive = false;
    private long sentMessages = 0;
    private long undeliveredMessages = 0;
	private long generatedMessagesInTick = 0;
    private long generatedMessages = 0;
    private final Set<Consumer> consumer = new HashSet<>();
    private AsyncFile file;

	@Override
	public void start() throws Exception {
	
		setUpFileSystem(v -> {
			vertx.setPeriodic(REPORT_DELAY_IN_MSEC, this::report);
			vertx.setTimer(SEND_DELAY_IN_MSEC, this::generateAndSend);
			vertx.eventBus().consumer("producer.control", (Message<JsonObject> msg) -> {
				 String action = msg.headers().get("action");
		            switch (action) {
						case "startSending":
							this.sendingActive = true;
							break;
						case "stopSending":
							this.sendingActive = false;
							break;
						case "clearStats":
							this.sentMessages = 0;
							this.generatedMessages = 0;
							this.undeliveredMessages = 0;
							this.generatedMessagesInTick = 0;
							break;
						case "throttle":
							this.msgPerSec = msg.body().getLong("throttle",-1l);
							break;
					}
			});
		});
		
	}

	private void generateAndSend(Long id) {
		if ( !sendingActive ) {
			vertx.setTimer(SEND_DELAY_IN_MSEC, this::generateAndSend);
			return;
		}
		long start = System.currentTimeMillis();
		long msgsToSend = Math.max(Math.floorDiv(msgPerSec * SEND_DELAY_IN_MSEC, 1000), 1);
		int m = 0;
		while ( m < msgsToSend ) {
			JsonObject msg = new JsonObject().put("ts",System.currentTimeMillis()).put("message","messsage "+m);
			this.generatedMessages++;
			generatedMessagesInTick++;
			m++;
			vertx.eventBus().send("consumer", msg, new DeliveryOptions().setSendTimeout(10000), reply -> {
				if (reply.succeeded()) {
					if ("ok".equalsIgnoreCase(reply.result().body().toString())) {
						this.sentMessages++;
					} else {
						this.undeliveredMessages++;
						file.write(Buffer.buffer("failed message :"+ msg.getLong("ts") + ". "+ reply.cause().getMessage()+ String.format("%n")));
					}
				} else {
					this.undeliveredMessages++;
					file.write(Buffer.buffer("failed message :"+ msg.getLong("ts") + ". "+ reply.cause().getMessage()+ String.format("%n")));
				}
			});
		};
		long diff = System.currentTimeMillis()-start;
		long delay = Math.max(SEND_DELAY_IN_MSEC-diff, 10);
		vertx.setTimer(delay, this::generateAndSend);
	}
	
	private void report(Long id) {
		long messageRate = Math.floorDiv(generatedMessagesInTick*1000, REPORT_DELAY_IN_MSEC);
		generatedMessagesInTick=0;
		JsonObject stats = new JsonObject()
			.put("name",name)
			.put("id",name)
			.put("throttle",msgPerSec)
			.put("timestamp",AppStarter.TIME_FORMATTER.format(ZonedDateTime.now()))
			.put("generatedMessages",generatedMessages)
			.put("sentMessages",sentMessages)
			.put("undeliveredMessages",undeliveredMessages)
			.put("messageRate", messageRate);
		
		file.write(Buffer.buffer(stats.encode() + String.format("%n")));
		vertx.eventBus().publish("producer.stats", stats);

	}
	
	private void generateAndSendToConsumer(Long id) {
        if (!sendingActive || consumer.isEmpty()) {
            vertx.setTimer(SEND_DELAY_IN_MSEC, this::generateAndSendToConsumer);
            return;
        }
        consumer.removeIf(c -> c.online == false);
        if (consumer.isEmpty()) {
            vertx.setTimer(SEND_DELAY_IN_MSEC, this::generateAndSendToConsumer);
            return;
        }
		long start = System.currentTimeMillis();
        long msgsToSend = Math.max(Math.floorDiv(msgPerSec * SEND_DELAY_IN_MSEC, 1000), 1);
		int m = 0;
		while ( m < msgsToSend ) {
			for (Consumer consumer : consumer) {
				if (consumer.online ) {
					JsonObject msg = new JsonObject().put("ts",System.currentTimeMillis()).put("message","messsage "+m);
					this.generatedMessages++;
					generatedMessagesInTick++;
					m++;
					vertx.eventBus().send("consumer", msg, new DeliveryOptions().setSendTimeout(10000), reply -> {
						if (reply.succeeded()) {
							if ("ok".equalsIgnoreCase(reply.result().body().toString())) {
								this.sentMessages++;
								consumer.online = true;
							} else {
								this.undeliveredMessages++;
								file.write(Buffer.buffer("failed message :"+ msg.getLong("ts") + ". "+ reply.result().body() + String.format("%n")));
							}
						} else {
							this.undeliveredMessages++;
							file.write(Buffer.buffer("failed message :"+ msg.getLong("ts") + ". "+ reply.cause().getMessage() + String.format("%n")));
							consumer.online = false;
						}
					});
					if ( m >= msgsToSend) break;
				}
			};
		};
		long diff = System.currentTimeMillis()-start;
		long delay = Math.max(SEND_DELAY_IN_MSEC-diff, 10);
        vertx.setTimer(delay, this::generateAndSendToConsumer);
    }

	
	private static class Consumer {
		public String address;
		public boolean online;
	}
	
	private void setUpFileSystem(Handler<Void> onCompletion) {
        OpenOptions options = new OpenOptions();
        options.setCreate(true).
                setTruncateExisting(true).
                setDsync(false);

        String fileName = name  + ".txt";
        vertx.fileSystem().open(fileName, options, res -> {
            if (res.succeeded()) {
                file = res.result();
				onCompletion.handle(null);
            } else {
                System.out.println("unable to setup file:" + fileName);
            }
        });
    }

}
