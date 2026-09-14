package com.testify.client.control;

import com.testify.common.Response;
import ocsf.client.AbstractClient;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Manages network communication between the HSTS client
 * and the server through the OCSF framework.
 */
public class TestifyClient extends AbstractClient {

    private Consumer<Response> responseHandler;

    /**
     * Creates an HSTS client communication object.
     *
     * @param host server address
     * @param port server port
     */
    public TestifyClient(String host, int port) {
        super(host, port);
    }

    /**
     * Defines how server responses will be handled.
     *
     * @param responseHandler response-processing function
     */
    public void setResponseHandler(
            Consumer<Response> responseHandler
    ) {
        this.responseHandler = responseHandler;
    }

    /**
     * Opens a connection to the HSTS server.
     *
     * @throws IOException if the connection fails
     */
    public void connectToServer() throws IOException {

        if (!isConnected()) {
            openConnection();
        }
    }

    /**
     * Sends a message to the HSTS server.
     *
     * @param message object to send
     * @throws IOException if the message cannot be sent
     */
    public void sendMessageToServer(Object message)
            throws IOException {

        if (!isConnected()) {
            throw new IOException(
                    "The client is not connected to the HSTS server."
            );
        }

        sendToServer(message);
    }

    /**
     * Closes the connection to the HSTS server.
     */
    public void disconnectFromServer() {

        if (isConnected()) {

            try {

                closeConnection();

            } catch (IOException exception) {

                System.err.println(
                        "Could not close the HSTS server connection: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Handles a message received from the server.
     *
     * @param message received message
     */
    @Override
    protected void handleMessageFromServer(Object message) {

        if (!(message instanceof Response response)) {

            System.err.println(
                    "Unsupported message received from the HSTS server."
            );

            return;
        }

        if (responseHandler != null) {
            responseHandler.accept(response);
        }
    }

    /**
     * Called after a connection is established.
     */
    @Override
    protected void connectionEstablished() {

        System.out.println(
                "Connected successfully to the HSTS server."
        );
    }

    /**
     * Called after the connection is closed.
     */
    @Override
    protected void connectionClosed() {

        System.out.println(
                "The connection to the HSTS server was closed."
        );
    }

    /**
     * Called when a connection error occurs.
     *
     * @param exception connection error
     */
    @Override
    protected void connectionException(
            Exception exception
    ) {

        System.err.println(
                "HSTS server connection error: "
                        + exception.getMessage()
        );
    }
}