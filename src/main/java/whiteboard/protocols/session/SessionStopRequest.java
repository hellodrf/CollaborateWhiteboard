package whiteboard.protocols.session;

import whiteboard.protocols.Document;
import whiteboard.protocols.InvalidMessage;
import whiteboard.protocols.Message;

/**
 * Message to request the session to stop.
 * @see {@link whiteboard.protocols.session.SessionProtocol}
 * @author aaron
 *
 */
public class SessionStopRequest extends Message {
	static final public String name = "SessionStopRequest";
	
	/**
	 * Initialiser when given message parameters explicitly. Note that
	 * in this message there are no additional parameters.
	 */
	public SessionStopRequest() {
		super(name,SessionProtocol.protocolName,Message.Type.Request);
	}
	
	/**
	 * Initialiser when given message parameters in a doc. Must throw
	 * InvalidMessag if any of the required parameters are not
	 * in the doc, including the appropriate msg parameter.
	 * @param doc with the message details
	 * @throws InvalidMessage when the doc does not contain all of the required parameters
	 */
	public SessionStopRequest(Document doc) throws InvalidMessage {
		super(name,SessionProtocol.protocolName,Message.Type.Request,doc); // really just testing the name, otherwise nothing more to test
		this.doc=doc;
	}
}
