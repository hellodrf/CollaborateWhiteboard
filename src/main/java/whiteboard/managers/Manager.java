package whiteboard.managers;


import whiteboard.managers.endpoint.Endpoint;
import whiteboard.managers.endpoint.IEndpointHandler;
import whiteboard.protocols.IProtocolHandler;
import whiteboard.protocols.Protocol;
import whiteboard.utils.Eventable;

/**
 * Manager base class. Methods must be overriden.
 * 
 * @see {@link whiteboard.managers.ServerManager}
 * @see {@link whiteboard.managers.ClientManager}
 * @author aaron
 *
 */
public class Manager extends Eventable implements IProtocolHandler, IEndpointHandler{
	
	/**
	 * Shut this manager down, closing all connections gracefully where possible.
	 */
	public void shutdown() {
		
	}
	
	
	/**
	 * The endpoint is ready to use.
	 * @param endpoint
	 */
	@Override
	public void endpointReady(Endpoint endpoint) {
		
	}
	
	/**
	 * The endpoint close() method has been called and completed.
	 * @param endpoint
	 */
	@Override
	public void endpointClosed(Endpoint endpoint) {
		
	}
	
	/**
	 * The endpoint has abruptly disconnected. It can no longer
	 * send or receive data.
	 * @param endpoint
	 */
	@Override
	public void endpointDisconnectedAbruptly(Endpoint endpoint) {
		
	}

	/**
	 * An invalid message was received over the endpoint.
	 * @param endpoint
	 */
	@Override
	public void endpointSentInvalidMessage(Endpoint endpoint) {
		
	}
	

	/**
	 * The protocol on the endpoint is not responding.
	 * @param endpoint
	 */
	@Override
	public void endpointTimedOut(Endpoint endpoint,Protocol protocol) {
		
	}

	/**
	 * The protocol on the endpoint has been violated.
	 * @param endpoint
	 */
	@Override
	public void protocolViolation(Endpoint endpoint,Protocol protocol) {
		
	}
	

	/**
	 * The endpoint has requested a protocol to start. If the protocol
	 * is allowed then the manager should tell the endpoint to handle it
	 * using {@link whiteboard.managers.endpoint.Endpoint#handleProtocol(Protocol)}
	 * before returning true.
	 * @param protocol
	 * @return true if the protocol was started, false if not (not allowed to run)
	 */
	@Override
	public boolean protocolRequested(Endpoint endpoint, Protocol protocol) {
		return false;
	}

	


}
