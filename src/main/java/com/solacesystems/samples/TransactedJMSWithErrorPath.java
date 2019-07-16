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
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.solacesystems.jms.SupportedProperty;

/**
 *Demonstrates using 2 message consumers to monitor a message queue and its associated
 *Dead Message Queue (DLQ).
 *
 * 
 * Note: JNDI initialization is done using `jndi.properties`
 */
public class TransactedJMSWithErrorPath {

    private static final Logger LOGGER = LogManager.getFormatterLogger();

    final String CONNECTION_FACTORY_JNDI_NAME = "/jms/cf/default";

    final String QUEUE_NAME = "test.queue";
    final String DMQ_NAME = "test.queue.dmq";
    final String QUEUE_JNDI_NAME = "/JNDI/" + QUEUE_NAME;
    final String DMQ_JNDI_NAME = "/JNDI/" + DMQ_NAME;

    // Latch used for synchronizing between threads
    final CountDownLatch latch = new CountDownLatch(10);
    
    /*
     * This is the primary method called to run our listener thread
     */
    public void run(String... args) throws Exception {

	// Create the initial context that will be used to lookup the JMS Administered
	// objects.
	InitialContext initialContext = new InitialContext();

	// Lookup the connection factory
	ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup(CONNECTION_FACTORY_JNDI_NAME);

	// Create connection to the Solace router
	Connection connection = connectionFactory.createConnection();

	// Create a transacted session. In this case the 2nd argument is ignored
	// (although it must still be supplied)
	Session session = connection.createSession(true, SupportedProperty.SOL_CLIENT_ACKNOWLEDGE);

	// Lookup the queue & dmq using their JNDI names
	Queue queue = (Queue) initialContext.lookup(QUEUE_JNDI_NAME);
	Queue dmq = (Queue) initialContext.lookup(DMQ_JNDI_NAME);

	// From the session, create a consumer for the destination.
	MessageConsumer messageConsumer = session.createConsumer(queue);

	// Also create a consumer for the DMQ
	MessageConsumer dmqMessageConsumer = session.createConsumer(dmq);

	// Prepare a file to write messages to
	BufferedWriter bw = new BufferedWriter(new FileWriter("transacted_write.txt"));

	// create our stub handler object
	Handler handler = new Handler();

	// Start receiving messages asynchronously on our primary queue
	messageConsumer.setMessageListener(new MyMessageListener(session, queue.getQueueName(), handler,latch));

	// Start listening for messages on our DMQ
	dmqMessageConsumer.setMessageListener(new MessageListener() {

	    @Override
	    public void onMessage(Message message) {
		
		/*
		 * Per the code snippet this is based on, the Delivery Count seems to be the only stateful
		 * entity associated with processing the message. That is, the same message can be 
		 * redelivered to, & handled by multiple threads, with the only thing we really care about being
		 * the Delivery Count. Put yet another way, the consumer that initially processed a given message
		 * does _not_ have to be the same one that removes it from the DLQ/DMQ - anyone can.
		 * Consequently, it is perfectly reasonable for me to consume a message when it appears on the DLQ &
		 * do postProcessing without worrying about who originally processed it.
		 */
		
		try {
		// read from the DMQ & commit the message to prevent redelivery
		LOGGER.debug("Message `%s` received on DMQ", ((TextMessage) message).getText());
		handler.postJMSMessageCommit(false);
		session.commit();
		latch.countDown();
		}catch(JMSException ex) {
		    LOGGER.error("Couldn't confirm message from DMQ");
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
	dmqMessageConsumer.close();

	session.close();
	connection.close();

	// The initial context needs to be closed; it does not extend AutoCloseable
	initialContext.close();

	// close our file handle
	bw.close();
    }

    public static void main(String... args) throws Exception {

	LOGGER.info("Starting up...");
	
	// spawn a listener thread
	new TransactedJMSWithErrorPath().run(args);
    }

    private class MyMessageListener implements MessageListener {

	private Session session = null;
	private String queueName = null;
	private Handler handler = null;
	private CountDownLatch latch = null;

	// basic constructor where we pass in the JMS session already in use
	public MyMessageListener(Session in_session, String queue_name, Handler in_handler, CountDownLatch in_latch) {
	    session = in_session;
	    queueName = queue_name;
	    handler = in_handler;
	    latch = in_latch;
	}

	@Override
	public void onMessage(Message message) {
	    long messageDeliveredCount = 0;

	    try {
		if (message != null && message instanceof TextMessage) {
		    LOGGER.info(((TextMessage) (message)).getText());
		} else {
		    LOGGER.error("MessageType is invalid");
		}

		LOGGER.info("Did some business logic");
		/*
		 * Do some business logic to process message
		 * 
		 * Logic Logic
		 * 
		 */
		
		/*****
		 * Force an error so that we never successfully 
		 * process the message
		 *****
		 */

		throw new Exception("Forced Exception");
		
		// we processed the message fine, remove it from the queue
		// and do whatever cleanup
		/*since we forcibly throw an exception, this code isn't reachable & so has been commented out*/
		
		//session.commit();

		//handler.postJMSMessageCommit(true);

	    } catch (Exception e) {
		LOGGER.error("Exception caught while processing message on queue");

		try {

		    messageDeliveredCount = message.getLongProperty("JMSXDeliveryCount");

		    LOGGER.warn("A message handler requested a rollback of message on queue %s. \n"
			    + "The message is put back on the queue and is likely to be processed again. "
			    + "Message has been delivered %d times", queueName, messageDeliveredCount);

		    long rollbackPauseInterval = 3000L;
		    LOGGER.warn("Sleeping for %d milliseconds before rolling back message.", rollbackPauseInterval);

		    Thread.sleep(rollbackPauseInterval);

		    // error occurred during processing, rollback the session & try again.
		    session.rollback();
		    latch.countDown();
		    
		} catch (InterruptedException ignore) {
		    LOGGER.error("Thread was interrupted while waiting to rollback");
		} catch (JMSException jmsEx) {
		    LOGGER.error("Error trying to rollback the session");
		}
	    }

	}

    }// end MyMessageListener

    private class Handler {
	public void postJMSMessageCommit(boolean messageProcessed) {
	    LOGGER.info("Did some postJMSMessageCommit action. messageProcessed is %s", messageProcessed);
	}
    }
}
