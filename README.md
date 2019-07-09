# solace-transacted-jms
A sample application that demonstrates the use of local transaction rollbacks with Solace's JMS API.
The application establishes a transacted session to read message from a queue & write them to a file. Before completing, we make the receiving thread perform a `session.rollback()`. The end result is an empty file, and no messages removed from the queue.

**Optional**: You can also test Retries & Dead Message Queue (DMQ) functionality by appropriately configuring the sample queue using PubSub+ Manager. In this scenario, the message is sent to the DMQ once `session.rollback()` exceeds the preset number of retries.

## Prerequisites
- An instance of Solace PubSub+
- A JNDI connection factory named `/jms/cf/default` configured through the WebUI
- `Publisher Messages DMQ Eligible` set on the connection factory
- `Publisher Messages Delivery Mode` set to `Persistent` on the connection factory
- A sample queue named `test.queue` with a JNDI name of `/JNDI/test.queue` created through the WebUI

## Usage
1. Update `src\main\resources\jndi.properties` to reflect your PubSub+ environment
2. `mvn clean install exec:java`
3. Send a JMS message to `test.queue`
4. The application receives the message in a transaction block which is subsequently rolled back. The end result is the empty file `transacted_write.txt`.
