/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 *  Solace JMS 1.1 Examples: SimpleJMSReceiver
 */

package com.solacesystems.samples;

import java.io.BufferedWriter;
import java.io.FileWriter;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import com.solacesystems.jms.SupportedProperty;

/**
 * Demonstrates the use of a blocking receive(...) call against a Solace queue.
 * The application listens for new/existing messages indefinitely & prints them
 * to the screen until killed.
 * 
 * Note: JNDI initialization is done using `jndi.properties`
 */
public class SimpleJMSReceiveNoWait {

    final static String QUEUE_NAME = "test.queue";
    final static String QUEUE_JNDI_NAME = "/JNDI/" + QUEUE_NAME;
    final static String CONNECTION_FACTORY_JNDI_NAME = "/jms/cf/default";

    public static void main(String...args) throws Exception {

	// Create the initial context that will be used to lookup the JMS Administered
	// objects.
	InitialContext initialContext = new InitialContext();

	// Lookup the connection factory
	ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup(CONNECTION_FACTORY_JNDI_NAME);

	// Create connection to the Solace router
	Connection connection = connectionFactory.createConnection();

	// Create a transacted session. In this case the 2nd argument is ignored
	// (although it must still be supplied)
	Session session = connection.createSession(false, SupportedProperty.SOL_CLIENT_ACKNOWLEDGE);

	// Lookup the queue using its JNDI name
	Queue queue = (Queue) initialContext.lookup(QUEUE_JNDI_NAME);

	// From the session, create a consumer for the destination.
	MessageConsumer messageConsumer = session.createConsumer(queue);
	Message message = null;

	// start our connection
	connection.start();

	//Start a file for writing
	BufferedWriter bw = new BufferedWriter(new FileWriter("messages.txt"));
	
	System.out.println("===== Waiting for Messages =====");
	
	/* run indefinitely till the thread is killed
	 * this is not an ideal approach since it doesn't allow
	 * us to clean up our resources. Also, we don't have an exit 
	 * condition, and the console floods with data.
	 * However, it serves to illustrate the use of the receiveNoWait() method.
	 */
	while (true) {

	    //get the next message available on the queue
	    message = messageConsumer.receiveNoWait();

	    if (message != null) {
		// Write the message out to a file if it's a text message or output a default
		// message if not. We do this since the screen floods with text when using a 
		// non-blocking method like receiveNoWait().
		if (message instanceof TextMessage) {
		    bw.write(((TextMessage) message).getText() + "\n");
		    bw.flush();
		} else {
		    System.out.println("Message received.");
		}

		// ack the message off the queue
		message.acknowledge();

	    } else {
		// the message was null, print out something indicating that we're waiting
		//keep in mind that this prints continually to screen until a message is received 
		    System.out.println("...Waiting for a message...");
	    }
	}
    }
}
