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
 *  Solace JMS 1.1 Examples: TransactedJMSSession
 */

package com.solacesystems.samples;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SupportedProperty;

/**
 * -Demonstrates the use of a rollback for transacted JMS sessions in Solace PubSub+. 
 * -Demonstrates the use of Message Listeners to asynchronously receive messages from a queue.
 *
 * The application reads messages from a queue before writing the contents to a
 * file. Once the file is written, a transaction rollback is initiated resulting
 * in no text being written to the file.
 *  
 *  Note: JNDI initialization is done using `jndi.properties`
 */
public class TransactedJMSSession {

    final String QUEUE_NAME = "test.queue";
    final String QUEUE_JNDI_NAME = "/JNDI/" + QUEUE_NAME;
    final String CONNECTION_FACTORY_JNDI_NAME = "/jms/cf/default";

    // Latch used for synchronizing between threads
    final CountDownLatch latch = new CountDownLatch(10);

    /*
     * This is the primary method called to run our listener thread
     */
    public void run(String... args) throws Exception {

	// Create the initial context that will be used to lookup the JMS Administered objects.
	InitialContext initialContext = new InitialContext();

	// Lookup the connection factory
	ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup(CONNECTION_FACTORY_JNDI_NAME);

	// Create connection to the Solace router
	Connection connection = connectionFactory.createConnection();

	// Create a transacted session. In this case the 2nd argument is ignored
	// (although it must still be supplied)
	Session session = connection.createSession(true, SupportedProperty.SOL_CLIENT_ACKNOWLEDGE);

	// Lookup the queue using its JNDI name
	Queue queue = (Queue) initialContext.lookup(QUEUE_JNDI_NAME);

	// From the session, create a consumer for the destination.
	MessageConsumer messageConsumer = session.createConsumer(queue);

	// Prepare a file to write messages to
	BufferedWriter bw = new BufferedWriter(new FileWriter("transacted_write.txt"));

	// Use the anonymous inner class for receiving messages asynchronously
	messageConsumer.setMessageListener(new MessageListener() {
	    @Override
	    public void onMessage(Message message) {
		try {

		    // Print the message out to screen if it's a text message or output a default
		    // message if not
		    if (message instanceof TextMessage) {
			System.out.printf("TextMessage received: '%s'%n", ((TextMessage) message).getText());
		    } else {
			System.out.println("Message received.");
		    }

		    // write the message to our file
		    bw.write(SolJmsUtility.dumpMessage(message));

		    // Pretend there was some sort of failure & do a rollback
		    session.rollback();

		    // unblock the main thread
		    latch.countDown(); 
		} catch (Exception ex) {
		    System.out.println("Error processing incoming message.");
		    ex.printStackTrace();

		}
	    }
	});

	// Start receiving messages
	connection.start();
	System.out.println("Awaiting message...");

	latch.await();

	connection.stop();

	// Close everything in the order reversed from the opening order
	// NOTE: as the interfaces below extend AutoCloseable,
	// with them it's possible to use the "try-with-resources" Java statement
	// see details at
	// https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
	messageConsumer.close();
	session.commit();
	session.close();
	connection.close();

	// The initial context needs to be closed; it does not extend AutoCloseable
	initialContext.close();

	// close our file handle
	bw.close();
    }

    public static void main(String... args) throws Exception {

	// spawn a listener thread
	new TransactedJMSSession().run(args);
    }

}
