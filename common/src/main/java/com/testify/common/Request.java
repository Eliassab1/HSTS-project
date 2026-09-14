package com.testify.common;

import java.io.Serializable;

/**
 * Represents a request sent from the client to the server.
 *
 * A request contains an action that describes what the client wants
 * the server to perform, and optional data associated with that action.
 */
public class Request implements Serializable {

    /**
     * Serialization version identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The action requested by the client.
     *
     * Examples:
     * GET_ALL_QUESTIONS
     * UPDATE_QUESTION
     */
    private String action;

    /**
     * Optional data sent together with the request.
     *
     * For example, when updating a question,
     * this field contains a Question object.
     */
    private Object data;

    /**
     * Creates a request with an action and optional data.
     *
     * @param action action that the server should perform
     * @param data data associated with the request
     */
    public Request(String action, Object data) {
        this.action = action;
        this.data = data;
    }

    /**
     * Returns the requested action.
     *
     * @return action name
     */
    public String getAction() {
        return action;
    }

    /**
     * Changes the requested action.
     *
     * @param action new action name
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * Returns the data attached to the request.
     *
     * @return request data
     */
    public Object getData() {
        return data;
    }

    /**
     * Changes the data attached to the request.
     *
     * @param data new request data
     */
    public void setData(Object data) {
        this.data = data;
    }

    /**
     * Returns a readable description of the request.
     *
     * @return textual representation of the request
     */
    @Override
    public String toString() {
        return "Request{" +
                "action='" + action + '\'' +
                ", data=" + data +
                '}';
    }
}