package com.utece.student.llpdetection.instrumentation.implant;

/**
 * Strategy interface for C2 channel communication.
 * Implementations provide different transport mechanisms
 * (raw TCP, encrypted, covert channels) while maintaining
 * a uniform API for the trampoline framework.
 *
 * For authorized security research and educational use only.
 */
public interface ImplantChannel {

    /**
     * Establish connection to the operator's listening post.
     *
     * @param host Remote host address
     * @param port Remote port number
     * @return true if connection was established successfully
     */
    boolean connect(String host, int port);

    /**
     * Send data through the established channel.
     *
     * @param data Byte array to transmit
     */
    void send(byte[] data);

    /**
     * Receive data from the channel.
     *
     * @return Received byte array, or empty array if no data available
     */
    byte[] receive();

    /**
     * Close the channel and release resources.
     */
    void disconnect();

    /**
     * Check whether the channel is currently connected.
     *
     * @return true if connected
     */
    boolean isConnected();
}
