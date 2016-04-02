# vertx-killed-verticle

I have a problem with killed clustered verticles. This demo project helps to show the problem.
The workflow of the app is really simple. There is a producer producing messages with a given rate. 
These messages are sent to the address „consumer“. There are several consumer verticles, all in a 
separate JVM and started with the cluster flag. They listen to the "consumer" address and write the received messages 
into a file.

When starting one producer and several consumer, killing the majority of the consumer vertx still sends 
messages to "dead" verticles. 

## install
- clone it
- check the cluster.xml in src/main/resources
- build it with 
`./gradlew jar`

## usage
start the producer and a simple Webpage

`vertx run de.notizwerk.AppStarter -cluster -cp build/libs/vertx-killed-verticle-1.0.jar`

start several consumer, for each one use: 

`vertx run de.notizwerk.Consumer -cluster -cp build/libs/vertx-killed-verticle-1.0.jar`


open the webpage at http://localhost:8080/app/index.html

There are buttons for starting and stopping the producing of the messages and you can set the throttling of the producer. 
