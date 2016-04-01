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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import java.time.ZonedDateTime;

/**
 *
 * @author Tarek El-Sibay
 */
public class Consumer extends AbstractVerticle {

	private final String name = "consumer_" + AppStarter.TIME_FILE_FORMATTER.format(ZonedDateTime.now());
	AsyncFile file;
	long messagesInTick = 0;
	long receivedMessages = 0;
	private static final int REPORT_DELAY_IN_MSEC = 1000;

	@Override
	public void start(Future<Void> startFuture) throws Exception {
	
		setUpFileSystem( v  -> {
			vertx.eventBus().consumer("consumer", (Message<JsonObject> msg) -> {
				 messagesInTick++;
				 receivedMessages++;
				 file.write(Buffer.buffer(msg.body().encode()));
				 msg.reply("ok");
			});
			vertx.setPeriodic(REPORT_DELAY_IN_MSEC, (id)-> {
				long messageRate = Math.floorDiv(messagesInTick*1000, REPORT_DELAY_IN_MSEC);
				messagesInTick=0;
				JsonObject stats = new JsonObject()
					.put("name",name)
					.put("id",name)
					.put("timestamp",AppStarter.TIME_FORMATTER.format(ZonedDateTime.now()))
					.put("messageRate", messageRate)
					.put("receivedMessages", receivedMessages);
				file.write(Buffer.buffer(stats.encode() + String.format("%n")));
				vertx.eventBus().publish("consumer.stats", stats);
			});
			startFuture.complete();
		});
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
