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
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;

/**
 *
 * @author Tarek El-Sibay
 */
public class AppStarter extends AbstractVerticle {
	
	public static DateTimeFormatter TIME_FILE_FORMATTER = new DateTimeFormatterBuilder()
                .appendValue(HOUR_OF_DAY, 2)
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendValue(SECOND_OF_MINUTE, 2)
                .appendValue(MILLI_OF_SECOND, 3)
                .toFormatter();
	
	public static DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(HOUR_OF_DAY, 2)
		.appendLiteral(':')
		.appendValue(MINUTE_OF_HOUR, 2)
		.appendLiteral(':')
		.appendValue(SECOND_OF_MINUTE, 2)
		.toFormatter();

	private HttpServer server=null;

	
	public static void main(String[] args) {
		Vertx vertx = Vertx.vertx();
		vertx.deployVerticle(AppStarter.class.getName());
	}

	
    @Override
    public void start(Future<Void> startFuture) throws Exception {
		vertx.deployVerticle(Producer.class.getName());
		
		Router router = Router.router(vertx);

		SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
		BridgeOptions options = new BridgeOptions()
			.addInboundPermitted(new PermittedOptions().setAddress("producer.control"))
			.addOutboundPermitted(new PermittedOptions().setAddress("producer.stats"))
			.addOutboundPermitted(new PermittedOptions().setAddress("consumer.stats"));
		
		sockJSHandler.bridge(options,(BridgeEvent event) -> {
			switch (event.type()) {
				case SOCKET_CREATED:
				case REGISTER:
				case SEND:
				case PUBLISH:
				case RECEIVE:
				case UNREGISTER:
				case SOCKET_CLOSED:
					event.complete(true);
					break;
				default:
					System.out.println("unknown event. raw mesg :"+ event.getRawMessage().encodePrettily());
					event.complete(false);
				}
		});
		int port = this.config().getInteger("port",8080);
		String host = this.config().getString("host", "0.0.0.0");

		router.route("/eventbus/*").handler(sockJSHandler);		
		router.route("/app/*").handler(StaticHandler.create().setWebRoot("app"));

		server = vertx.createHttpServer().requestHandler(router::accept).listen(port,host, res -> {
            if (res.succeeded()) {
                System.out.println("Server is now listening "+host+":"+port);
				startFuture.complete();
            } else {
                System.out.println("Failed to bind!");
				startFuture.fail(res.cause());
            }
        });

	}

    @Override
    public void stop(Future<Void> stop) throws Exception {
		System.out.println("stop signal received");
		if ( server != null ) {
			System.out.println("closing server");
			server.close( (ar)->  { 
				System.out.println("server closed");
				stop.complete();
			});
		}
    }

	
}
