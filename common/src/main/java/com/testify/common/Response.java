package com.testify.common;

import java.io.Serializable;

/**
 * Represents a response sent from the server to the client.
 *
 * The response indicates whether the requested operation succeeded,
 * contains a message for the client and may also contain returned data.
 */
public class Response implements Serializable {

    /**
     * Serialization version identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Indicates whether the server operation succeeded.
     */
    private boolean success;

    /**
     * Message describing the result of the operation.
     */
    private String message;

    /**
     * Optional data returned by the server.
     *
     * Examples:
     * List<Question>
     * Question
     */
    private Object data;

    /**
     * The action this response answers, echoed back from the originating
     * {@link Request}.
     *
     * The client routes on this instead of inferring intent from the payload
     * type and the current window title. That matters for two cases the old
     * approach could not express: an empty list (which carries no element to
     * type-check) and two different actions that return the same type, such
     * as DELETE_QUESTION and DELETE_EXAM both returning an Integer.
     *
     * Stamped centrally by the server immediately before the response is
     * written, so individual handlers never set it. Null only for responses
     * produced before an action could be read, such as an unsupported
     * message type.
     */
    private String action;

    /**
     * Creates a new server response.
     *
     * @param success true if the operation succeeded
     * @param message message describing the result
     * @param data data returned by the server
     */
    public Response(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    /**
     * Creates a new server response with an explicit action.
     *
     * Rarely needed: the server stamps the action centrally on the way out.
     *
     * @param success true if the operation succeeded
     * @param message message describing the result
     * @param data data returned by the server
     * @param action action this response answers
     */
    public Response(boolean success, String message, Object data, String action) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.action = action;
    }

    /**
     * Returns whether the operation succeeded.
     *
     * @return true if successful, otherwise false
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Changes the success status.
     *
     * @param success new success status
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Returns the response message.
     *
     * @return response message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Changes the response message.
     *
     * @param message new response message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns the data returned by the server.
     *
     * @return response data
     */
    public Object getData() {
        return data;
    }

    /**
     * Changes the response data.
     *
     * @param data new response data
     */
    public void setData(Object data) {
        this.data = data;
    }

    /**
     * Returns the action this response answers.
     *
     * @return originating action name, or null when it could not be determined
     */
    public String getAction() {
        return action;
    }

    /**
     * Sets the action this response answers.
     *
     * @param action originating action name
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * Returns a readable description of the response.
     *
     * @return textual representation of the response
     */
    @Override
    public String toString() {
        return "Response{" +
                "action='" + action + '\'' +
                ", success=" + success +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
