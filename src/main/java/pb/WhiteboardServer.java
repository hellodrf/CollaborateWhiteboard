package pb;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


import pb.app.Whiteboard;
import pb.app.WhiteboardApp;
import pb.managers.PeerManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

import static pb.managers.IOThread.ioThread;

/**
 * Simple whiteboard server to provide whiteboard peer notifications.
 * @author aaron
 *
 */
public class WhiteboardServer {
	private static final Logger log = Logger.getLogger(WhiteboardServer.class.getName());
	
	/**
	 * Emitted by a client to tell the server that a board is being shared. Argument
	 * must have the format "host:port:boardID".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String shareBoard = "SHARE_BOARD";

	/**
	 * Emitted by a client to tell the server that a board is no longer being
	 * shared. Argument must have the format "host:port:boardID".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unshareBoard = "UNSHARE_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is being shared</li>
	 * <li>to a newly connected client, it emits this event several times, for all
	 * boards that are currently known to be being shared</li>
	 * </ul>
	 * Argument has format "host:port:boardID"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String sharingBoard = "SHARING_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is no longer
	 * shared</li>
	 * </ul>
	 * Argument has format "host:port:boardID"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unsharingBoard = "UNSHARING_BOARD";

	/**
	 * Emitted by the server to a client to let it know that there was an error in a
	 * received argument to any of the events above. Argument is the error message.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String error = "ERROR";
	
	/**
	 * Default port number.
	 */
	private static int port = Utils.indexServerPort;


	private static final Map<String, String> whiteboards = new ConcurrentHashMap<>();

	private static final Map<String, Endpoint> peers = new ConcurrentHashMap<>();

	private static final Map<String, String> peersPortMap = new ConcurrentHashMap<>();

	
	private static void help(Options options){
		String header = "PB Whiteboard Server for Unimelb COMP90015\n\n";
		String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("pb.IndexServer", header, options, footer, true);
		System.exit(-1);
	}
	
	public static void main(String[] args) throws IOException, InterruptedException
    {
    	// set a nice log format
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] [%4$s] %2$s: %5$s%n");
        
    	// parse command line options
        Options options = new Options();
        options.addOption("port",true,"server port, an integer");
        options.addOption("password",true,"password for server");
        
       
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
			cmd = parser.parse(options, args);
		} catch (ParseException e1) {
			help(options);
		}

		assert cmd != null;
		if(cmd.hasOption("port")){
        	try{
        		port = Integer.parseInt(cmd.getOptionValue("port"));
			} catch (NumberFormatException e){
				System.out.println("-port requires a port number, parsed: "+cmd.getOptionValue("port"));
				help(options);
			}
        }

        // create a server manager and setup event handlers
        ServerManager serverManager;
        
        if(cmd.hasOption("password")) {
        	serverManager = new ServerManager(port, cmd.getOptionValue("password"));
        } else {
        	serverManager = new ServerManager(port);
        }

		serverManager.on(ioThread, (args1 -> {
			String peerPort = (String) args1[0];
			log.info("Server started on " + peerPort);
			log.info("Waiting for peers to connect...");
		})).on(ServerManager.sessionStarted, (args1 -> {
			Endpoint endpoint = (Endpoint) args1[0];
			peers.put(endpoint.getOtherEndpointId(), endpoint);
			System.out.println("Peer connected: " + endpoint.getOtherEndpointId());
			for (String board: whiteboards.values()) {
				endpoint.emit(sharingBoard, board);
			}
			endpoint.on(shareBoard, (args2 -> {
				String pp = (String)args2[0];
				String id = WhiteboardApp.parsePeer(pp)[2];
				String ppi = WhiteboardApp.parsePeer(pp)[0]+":" +WhiteboardApp.parsePeer(pp)[1];
				peersPortMap.put(endpoint.getOtherEndpointId(), ppi);
				System.out.println("New whiteboard: " + pp);
				if (!whiteboards.containsKey(id)) {
					whiteboards.put(id, pp);
					log.info("Received whiteboard: " + pp);
					for (Endpoint e: peers.values()) {
						if (!e.getOtherEndpointId().equals(endpoint.getOtherEndpointId())) {
							log.info("Sharing " + pp + " to " + e.getOtherEndpointId());
							e.emit(sharingBoard, pp);
						}
					}
				} else {
					log.warning("Duplicate sharing request: " + pp);
				}
			})).on(unshareBoard, (args2 -> {
				String pp = (String)args2[0];
				String id = WhiteboardApp.parsePeer(pp)[2];
				if (whiteboards.containsKey(id)) {
					whiteboards.remove(id);
					log.info("Received unsharing request: " + pp);
					for (Endpoint e: peers.values()) {
						if (!e.getOtherEndpointId().equals(endpoint.getOtherEndpointId())) {
							log.info("Unsharing" + pp + " from " + e.getOtherEndpointId());
							e.emit(unsharingBoard, pp);
						}
					}
				} else {
					log.warning("Whiteboard not exist but unshared: " + pp);
				}
			}));
		})).on(ServerManager.sessionStopped, (args1 -> {
			Endpoint endpoint = (Endpoint) args1[0];
			peers.remove(endpoint.getOtherEndpointId());
			log.info("Peer disconnected: " + endpoint.getOtherEndpointId());
			removeAllBoards(endpoint);
		})).on(ServerManager.sessionError, (args1 -> {
			Endpoint endpoint = (Endpoint) args1[0];
			peers.remove(endpoint.getOtherEndpointId());
			log.severe("Peer disconnected due to error: " + endpoint.getOtherEndpointId());
			removeAllBoards(endpoint);
		}));
        
        // start up the server
        log.info("Whiteboard Server starting up");
        serverManager.start();
        // nothing more for the main thread to do
        serverManager.join();
        Utils.getInstance().cleanUp();
    }

	private static void removeAllBoards(Endpoint endpoint) {
		for (String pp : whiteboards.values()) {
			String[] data = WhiteboardApp.parsePeer(pp);
			if ((data[0] + ":" + data[1]).equals(peersPortMap.get(endpoint.getOtherEndpointId()))) {
				whiteboards.remove(data[2]);
				log.info("Peer board dropped due to disconnection: " + pp);
				for (Endpoint e: peers.values()) {
					if (!e.getOtherEndpointId().equals(endpoint.getOtherEndpointId())) {
						e.emit(unsharingBoard, pp);
					}
				}
			}
		}
	}

}
