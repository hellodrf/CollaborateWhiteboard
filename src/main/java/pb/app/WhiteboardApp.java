package pb.app;

import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.IOThread;
import pb.managers.PeerManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


/**
 * Initial code obtained from:
 * https://www.ssaurel.com/blog/learn-how-to-make-a-swing-painting-and-drawing-application/
 */
public class WhiteboardApp {

	private static final Logger log = Logger.getLogger(WhiteboardApp.class.getName());
	
	/**
	 * Emitted to another peer to subscribe to updates for the given board. Argument
	 * must have format "host:port:boardID
	 * ".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String listenBoard = "BOARD_LISTEN";

	/**
	 * Emitted to another peer to unsubscribe to updates for the given board.
	 * Argument must have format "host:port:boardID
	 * ".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unlistenBoard = "BOARD_UNLISTEN";

	/**
	 * Emitted to another peer to get the entire board data for a given board.
	 * Argument must have format "host:port:boardID
	 * ".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String getBoardData = "GET_BOARD_DATA";

	/**
	 * Emitted to another peer to give the entire board data for a given board.
	 * Argument must have format "host:port:boardID
	 * %version%PATHS".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardData = "BOARD_DATA";

	/**
	 * Emitted to another peer to override their current board (ignore version).
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardDataOverride = "BOARD_DATA_OVERRIDE";

	/**
	 * Emitted to another peer to add a path to a board managed by that peer.
	 * Argument must have format "host:port:boardID
	 * %version%PATH". The numeric value
	 * of version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathUpdate = "BOARD_PATH_UPDATE";

	/**
	 * Emitted to another peer to indicate a new path has been accepted. Argument
	 * must have format "host:port:boardID
	 * %version%PATH". The numeric value of
	 * version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathAccepted = "BOARD_PATH_ACCEPTED";

	/**
	 * Emitted to another peer to remove the last path on a board managed by that
	 * peer. Argument must have format "host:port:boardID
	 * %version%". The numeric
	 * value of version must be equal to the version of the board without the undo
	 * applied, i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoUpdate = "BOARD_UNDO_UPDATE";

	/**
	 * Emitted to another peer to indicate an undo has been accepted. Argument must
	 * have format "host:port:boardID
	 * %version%". The numeric value of version must
	 * be equal to the version of the board without the undo applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoAccepted = "BOARD_UNDO_ACCEPTED";

	/**
	 * Emitted to another peer to clear a board managed by that peer. Argument must
	 * have format "host:port:boardID
	 * %version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearUpdate = "BOARD_CLEAR_UPDATE";

	/**
	 * Emitted to another peer to indicate an clear has been accepted. Argument must
	 * have format "host:port:boardID
	 * %version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearAccepted = "BOARD_CLEAR_ACCEPTED";

	/**
	 * Emitted to another peer to indicate a board no longer exists and should be
	 * deleted. Argument must have format "host:port:boardID
	 * ".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardDeleted = "BOARD_DELETED";

	/**
	 * Emitted to another peer to indicate an error has occurred.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardError = "BOARD_ERROR";


	/**
	 * White board map from board name to board object 
	 */
	final Map<String, Whiteboard> whiteboards;
	
	/**
	 * The currently selected white board
	 */
	Whiteboard selectedBoard = null;
	
	/**
	 * The peer:port string of the peer. This is synonymous with IP:port, host:port,
	 * etc. where it may appear in comments.
	 */
	String peerPort;

	String indexServerHost;

	int indexServerPort;

	/**
	 * Peer manager, index server client manager and endpoint
	 */
	PeerManager peerManager = null;

	ClientManager serverClientManager = null;

	Endpoint serverEndpoint = null;

	/**
	 * Subscription map: board to endpoint, for subscriber broadcasts.
	 */
	Map<String, ArrayList<Endpoint>> subscriptionEndpointMap; // board name : <Endpoint>

	/**
	 * Endpoint id map: id to remote boards, for clean-ups upon endpoint closure.
	 */
	Map<String, ArrayList<String>> remoteBoardMap; // endpoint id : <board name>

	/**
	 * Initialize the white board app.
	 */
	public WhiteboardApp(int peerPort, String indexServerHost, int indexServerPort) {
		this.whiteboards = new ConcurrentHashMap<>();
		this.subscriptionEndpointMap = new ConcurrentHashMap<>();
		this.remoteBoardMap = new ConcurrentHashMap<>();
		this.indexServerHost = indexServerHost;
		this.indexServerPort = indexServerPort;
		startPeerServer(peerPort);
	}

	/**
	 * Start the server that receive peer connections and board requests.
	 * @param peerPort port
	 */
	public void startPeerServer(int peerPort) {
		this.peerManager = new PeerManager(peerPort);
		peerManager.on(PeerManager.peerServerManager, (args)->{
			ServerManager serverManager = (ServerManager)args[0];
			serverManager.on(IOThread.ioThread, (args2)->{
				this.peerPort = (String) args2[0];
				show(this.peerPort);
				// connect to index server
				try {
					connectToIndexServer(this.indexServerHost, this.indexServerPort);
				} catch (InterruptedException | UnknownHostException ignored) {
					// I looked up the source, these exceptions will never be thrown...
				}
				// start heartbeat
				startHeartbeat();
			});
		}).on(PeerManager.peerStarted, (args)-> {
			Endpoint endpoint = (Endpoint) args[0];
			log.info("Peer session started: " + endpoint.getOtherEndpointId());
			endpoint.on(listenBoard, (args1) -> {
				String board = (String) args1[0];
				if (whiteboards.containsKey(board)) {
					ArrayList<Endpoint> endpointList;
					if (subscriptionEndpointMap.containsKey(board)) {
						endpointList = subscriptionEndpointMap.get(board);
					} else {
						endpointList = new ArrayList<>();
					}
					endpointList.add(endpoint);
					subscriptionEndpointMap.put(board, endpointList);
					log.info("Subscriber listening on " + board + ": " + endpoint.getOtherEndpointId());
				}
			}).on(unlistenBoard, (args2) -> {
				String board = (String) args2[0];
				if (whiteboards.containsKey(board)) {
					if (subscriptionEndpointMap.containsKey(board)) {
						ArrayList<Endpoint> endpointList = subscriptionEndpointMap.get(board);
						endpointList.remove(endpoint);
						subscriptionEndpointMap.put(board, endpointList);
						log.info("Subscriber left " + board + ": " + endpoint.getOtherEndpointId());
					}
				}
			}).on(getBoardData, (args1) -> {
				String board = (String) args1[0];
				if (whiteboards.containsKey(board)) {
					endpoint.emit(boardData, whiteboards.get(board).toString());
					log.info("Board " + board + " sent to: " + endpoint.getOtherEndpointId());
				} else {
					endpoint.emit(boardError, "BOARD_NOT_FOUND");
					log.warning("Board " + board + " does not exist but queried");
				}
			}).on(boardPathUpdate, (args1) -> {
				String data = (String) args1[0];
				Whiteboard board = whiteboards.get(getBoardName(data));
				if (board == null) {
					endpoint.emit(boardError, "BOARD_NOT_FOUND");
					log.warning("Board " + data + " does not exist but queried");
				} else {
					if (pathCreatedRemotely(new WhiteboardPath(getBoardPaths(data)), board, getBoardVersion(data))) {
						endpoint.emit(boardPathAccepted, data);
					} else {
						endpoint.emit(boardError, "PATH_REJECTED");
						endpoint.emit(boardDataOverride, board.toString());
					}
				}
			}).on(boardUndoUpdate, (args1) -> {
				String data = (String) args1[0];
				Whiteboard board = whiteboards.get(getBoardName(data));
				if (board == null) {
					endpoint.emit(boardError, "BOARD_NOT_FOUND");
					log.warning("Board " + data + " does not exist but queried");
				} else {
					if (undoRemotely(board, getBoardVersion(data))) {
						endpoint.emit(boardUndoAccepted, data);
					} else {
						endpoint.emit(boardError, "UNDO_REJECTED");
						endpoint.emit(boardDataOverride, board.toString());
					}
				}
			}).on(boardClearUpdate, (args1) -> {
				String data = (String) args1[0];
				Whiteboard board = whiteboards.get(getBoardName(data));
				if (board == null) {
					endpoint.emit(boardError, "BOARD_NOT_FOUND");
					log.warning("Board " + data + " does not exist but queried");
				} else {
					if (clearedRemotely(board, getBoardVersion(data))) {
						endpoint.emit(boardClearAccepted, data);
					} else {
						endpoint.emit(boardError, "CLEAR_REJECTED");
						endpoint.emit(boardDataOverride, board.toString());
					}
				}
			});
		}).on(PeerManager.peerStopped, (args -> {
			Endpoint endpoint = (Endpoint) args[0];
			log.info("Connection to peer ended: " + endpoint.getOtherEndpointId());
			endpoint.close();
		})).on(PeerManager.peerError, (args -> {
			Endpoint endpoint = (Endpoint) args[0];
			log.severe("Connection to peer ended in error: " + endpoint.getOtherEndpointId());
			endpoint.close();
		}));
		peerManager.start();
	}

	/**
	 * Connect to the index server and keep-alive for updates.
	 * @param host server host
	 * @param port server port
	 */
	public void connectToIndexServer(String host, int port)
			throws InterruptedException, UnknownHostException {
        serverClientManager = peerManager.connect(port, host);
		serverClientManager.on(PeerManager.peerStarted, (args) -> {
            serverEndpoint = (Endpoint) args[0];
            System.out.println("Connected to index server: " + serverEndpoint.getOtherEndpointId());
            serverEndpoint.on(WhiteboardServer.sharingBoard, (args1 -> {
				String[] parts = parsePeer((String) args1[0]);
				String board = (String) args1[0];
				log.info("Received new board source: " + Arrays.toString(parts));
            	if (!whiteboards.containsKey(board)) {
					String pp = parts[0] + ":" + parts[1];
					if (!subscriptionEndpointMap.containsKey(pp)) {
						connectToPeer(parts[0], Integer.parseInt(parts[1]), (String) args1[0]);
					}
				}
            })).on(WhiteboardServer.unsharingBoard, (args1) -> {
                String boardName = (String) args1[0];
				if (whiteboards.containsKey(boardName)) {
					deleteBoard(boardName);
					log.info("Board removed by index server: " + boardName);
				} // since this call could (very likely) be a redundancy, we should tolerate this
            }).on(WhiteboardServer.error, (args1 -> {
            	log.warning("Error from index server: " + args1[0]);
			}));
			for (Whiteboard w : whiteboards.values()) {
				if (!w.isRemote() && w.isShared()) {
					setShareToServer(w, true);
				}
			}
	    }).on(PeerManager.peerStopped, (args) -> {
            Endpoint endpoint = (Endpoint) args[0];
            log.info("Connection to server ended: " + endpoint.getOtherEndpointId());
	    }).on(PeerManager.peerError, (args) -> {
            Endpoint endpoint = (Endpoint) args[0];
            log.severe("Connection to server ended in error: " + endpoint.getOtherEndpointId());
            endpoint.close();
        });
		serverClientManager.start();
	}

	/**
	 * Connect to a peer server and keep-alive for board updates.
	 * @param host peer host
	 * @param port peer port
	 * @param firstBoard the first board to request
	 */
	public void connectToPeer(String host, int port, String firstBoard) {
		ClientManager clientManager;
		try {
			clientManager = peerManager.connect(port, host);
			clientManager.on(PeerManager.peerStarted, (args) -> {
				Endpoint endpoint = (Endpoint) args[0];
				log.info("Peer connected: " + endpoint.getOtherEndpointId());
				endpoint.on(boardData, (args1 -> acceptRemoteBoard((String)args1[0], endpoint, false))
				).on(boardDataOverride, (args1 -> acceptRemoteBoard((String)args1[0], endpoint, true))
				).on(boardPathAccepted, (args1) -> {
					String data = (String) args1[0];
					log.info("Modification accepted by " + endpoint.getOtherEndpointId()
							+ ": " + getBoardName(data) + " - " + getBoardPaths(data));
				}).on(boardUndoAccepted, (args1) -> {
					String data = (String) args1[0];
					log.info("Modification accepted by " + endpoint.getOtherEndpointId()
							+ ": " + getBoardName(data) + " - UNDO");
				}).on(boardClearAccepted, (args1) -> {
					String data = (String) args1[0];
					log.info("Modification accepted by " + endpoint.getOtherEndpointId()
							+ ": " + getBoardName(data) + " - CLEAR");
				}).on(boardError, (args1 -> {
					String message = (String) args1[0];
					log.info("Error from " + endpoint.getOtherEndpointId()
							+ ": " + message);
				})).on(boardDeleted, (args1 -> {
					String boardName = (String) args1[0];
					if (whiteboards.containsKey(boardName)) {
						deleteBoard(boardName);
						log.info("Board removed by remote peer: " + boardName);
					} // since this call could (very likely) be a redundancy, we should tolerate this
				}));
				endpoint.emit(listenBoard, firstBoard);
				endpoint.emit(getBoardData, firstBoard);
			}).on(PeerManager.peerStopped, (args) -> {
				Endpoint endpoint = (Endpoint) args[0];
				removeAllBoardsByEndpoint(endpoint);
				log.info("Peer disconnected: " + endpoint.getOtherEndpointId());
			}).on(PeerManager.peerError, (args) -> {
				Endpoint endpoint = (Endpoint) args[0];
				removeAllBoardsByEndpoint(endpoint);
				log.severe("Peer connection ended in error: " + endpoint.getOtherEndpointId());
				endpoint.close();
			});
			clientManager.start();
		} catch (UnknownHostException | InterruptedException ignored) {}
	}

	/*
	 * Methods called from events.
	 */

	// From whiteboard peer

	/**
	 * Basically push all boards to their subscribers every 5 seconds.
	 */
	private void startHeartbeat() {
		Utils.getInstance().setTimeout(() -> {
			if (!subscriptionEndpointMap.isEmpty()) {
				whiteboards.values().forEach(this::broadcastChanges);
				log.info("Heartbeat executed");
				startHeartbeat();
			}
		}, 5000);
	}

	private void broadcastChanges(Whiteboard whiteboard) {
		if (subscriptionEndpointMap.containsKey(whiteboard.getName())) {
			for (Endpoint e: subscriptionEndpointMap.get(whiteboard.getName())) {
				e.emit(boardData, whiteboard.toString());
				log.info("Board " + whiteboard.getName() + " sent to: " + e.getOtherEndpointId());
			}
		}
	}
	private void acceptRemoteBoard(String boardData, Endpoint endpoint, boolean override) {
		String name = getBoardName(boardData);
		String data = getBoardData(boardData);
		long version = getBoardVersion(boardData);
		if (!whiteboards.containsKey(name) || whiteboards.get(name).getVersion() < version || override) {
			Whiteboard whiteboard = new Whiteboard(name, true);
			whiteboard.whiteboardFromString(name, data);
			whiteboard.setRemoteSource(endpoint);
			addBoard(whiteboard, false);
			ArrayList<String> boardList;
			if (remoteBoardMap.containsKey(endpoint.getOtherEndpointId())) {
				boardList = remoteBoardMap.get(endpoint.getOtherEndpointId());
			} else {
				boardList = new ArrayList<>();
			}
			boardList.add(whiteboard.getName());
			remoteBoardMap.put(endpoint.getOtherEndpointId(), boardList);
			log.info((override?"Overriding board received: ":"Board received: ") + whiteboard.getName());
		}
	}

    private void setShareToServer(Whiteboard whiteboard, Boolean share) {
		if (serverEndpoint != null) {
			serverEndpoint.emit(share ? WhiteboardServer.shareBoard : WhiteboardServer.unshareBoard,
					whiteboard.getName());
			log.info("Setting " + whiteboard.getName() + " as " + (share?"shared.":"unshared."));
		}
	}

	private void removeAllBoardsByEndpoint(Endpoint endpoint) {
    	String eid = endpoint.getOtherEndpointId();
    	ArrayList<String> whiteboards = remoteBoardMap.get(eid);
    	whiteboards.forEach(this::deleteBoard);
	}

	private void unlistenToPeer(Whiteboard whiteboard) {
    	if (whiteboard.isRemote()) {
			Endpoint e = whiteboard.getRemoteSource();
			e.emit(unlistenBoard, whiteboard.getName());
			log.info("Unsubscribed board " + whiteboard.getName());
		}
	}

	/*
	 * Utility methods to extract fields from argument strings.
	 */

    public static String[] parsePeer(String data) {
        return data.split(":");
    }

	/**
	 *
	 * @param data = peer:port:boardID
	 *                %version%PATHS
	 * @return peer:port:boardID
	 *
	 */
	public static String getBoardName(String data) {
		String[] parts = data.split("%",2);
		return parts[0];
	}

	/**
	 *
	 * @param data = peer:port:boardID
	 *                %version%PATHS
	 * @return boardID
	 * %version%PATHS
	 */
	@SuppressWarnings("unused")
	public static String getBoardIDAndData(String data) {
		String[] parts=data.split(":");
		return parts[2];
	}

	/**
	 *
	 * @param data = peer:port:boardID
	 *                %version%PATHS
	 * @return version%PATHS
	 */
	public static String getBoardData(String data) {
		String[] parts=data.split("%",2);
		return parts[1];
	}

	/**
	 *
	 * @param data = peer:port:boardID
	 *                %version%PATHS
	 * @return version
	 */
	public static long getBoardVersion(String data) {
		String[] parts=data.split("%",3);
		return Long.parseLong(parts[1]);
	}

	/**
	 *
	 * @param data = peer:port:boardID
	 *                %version%PATHS
	 * @return PATHS
	 */
	public static String getBoardPaths(String data) {
		String[] parts = data.split("%",3);
		return parts[2];
	}

	/**
	 *
	 * @param data = peer:port:boardID
	 *                d%version%PATHS
	 * @return peer
	 */
	@SuppressWarnings("unused")
	public static String getIP(String data) {
		String[] parts=data.split(":");
		return parts[0];
	}

	/**
	 *
	 * @param data = peer:port:boardID
	 *                %version%PATHS
	 * @return port
	 */
	@SuppressWarnings("unused")
	public static int getPort(String data) {
		String[] parts=data.split(":");
		return Integer.parseInt(parts[1]);
	}
	
	/*
	 * 
	 * Methods to manipulate data locally. Distributed systems related code has been
	 * cut from these methods.
	 * 
	 */
	
	/**
	 * Wait for the peer manager to finish all threads.
	 */
	public void waitToFinish() throws InterruptedException {
		if (serverClientManager != null) {
			serverClientManager.join();
		}
		peerManager.joinWithClientManagers();
	}
	
	/**
	 * Add a board to the list that the user can select from. If select is
	 * true then also select this board.
	 * @param whiteboard board
	 * @param select boolean
	 */
	public void addBoard(Whiteboard whiteboard, boolean select) {
		synchronized(whiteboards) {
			whiteboards.put(whiteboard.getName(), whiteboard);
		}
		updateComboBox(select?whiteboard.getName():null);
	}
	
	/**
	 * Delete a board from the list.
	 * @param boardName must have the form peer:port:boardID
	 *
	 */
	public void deleteBoard(String boardName) {
		// since we switched to concurrentHashMap, synchronized is no longer required.
		Whiteboard whiteboard = whiteboards.get(boardName);
		if(whiteboard!=null) {
			whiteboards.remove(boardName);
			if (!whiteboard.isRemote() && whiteboard.isShared()) {
				setShareToServer(whiteboard, false);
			} else if (whiteboard.isRemote()) {
				unlistenToPeer(whiteboard);
			}
		}
		updateComboBox(null);
	}
	
	/**
	 * Create a new local board with name peer:port:boardID
	 * includes the time stamp that the board was created at.
	 */
	public void createBoard() {
		String name = peerPort +":board"+Instant.now().toEpochMilli();
		Whiteboard whiteboard = new Whiteboard(name,false);
		addBoard(whiteboard,true);
	}
	
	/**
	 * Add a path to the selected board. The path has already
	 * been drawn on the draw area; so if it can't be accepted then
	 * the board needs to be redrawn without it.
	 * @param currentPath path
	 */
	public void pathCreatedLocally(WhiteboardPath currentPath) {
		if(selectedBoard!=null) {
			if (selectedBoard.isRemote()) {
				selectedBoard.getRemoteSource().emit(boardPathUpdate, selectedBoard.getName() + "%"
						+ selectedBoard.getVersion() + "%" + currentPath.toString());
				log.info("Pushed path to remote board " + selectedBoard.getName()
						+ ": " + currentPath.toString());
			} else {
				if(!selectedBoard.addPath(currentPath, selectedBoard.getVersion())) {
					// some other peer modified the board in between
					drawSelectedWhiteboard(); // just redraw the screen without the path
				} else {
					// was accepted locally, so push to subscribers
					broadcastChanges(selectedBoard);
				}
			}
		} else {
			log.severe("Local path created without a selected board: "+currentPath);
		}
	}

	/**
	 * Invoked by remote path creating request. Try to accept and push to subscribers.
	 * @param path path
	 * @param board board
	 * @param remoteVersion version
	 * @return success or not
	 */
	public boolean pathCreatedRemotely(WhiteboardPath path, Whiteboard board, long remoteVersion) {
		if(board != null) {
			// well you cannot remotely change a remote board...
			if(board.isRemote() || !board.addPath(path, remoteVersion)) {
				log.info("Remote path rejected on " + board.getName() + ": " + path);
				return false;
			} else {
				// was accepted locally, so push to subscribers
				drawSelectedWhiteboard();
				log.info("Remote path accepted: " + path);
				broadcastChanges(board);
				return true;
			}
		} else {
			log.severe("Remote path targeted for a non-existent board: " + path);
			return false;
		}
	}
	
	/**
	 * Clear the selected whiteboard.
	 */
	public void clearedLocally() {
		if(selectedBoard!=null) {
			if (selectedBoard.isRemote()) {
				selectedBoard.getRemoteSource().emit(boardClearUpdate, selectedBoard.getName() + "%"
						+ selectedBoard.getVersion() + "%");
				log.info("Pushed clear to remote board " + selectedBoard.getName());
			} else {
				if(!selectedBoard.clear(selectedBoard.getVersion())) {
					// some other peer modified the board in between
					drawSelectedWhiteboard();
				} else {
					// was accepted locally, so push to subscribers
					drawSelectedWhiteboard();
					broadcastChanges(selectedBoard);
				}
			}
		} else {
			log.severe("Local cleared without a selected board");
		}
	}

	/**
	 * Invoked by remote clear request. Try to accept and push to subscribers.
	 * @param board board
	 * @param remoteVersion version
	 * @return success or not
	 */
	public boolean clearedRemotely(Whiteboard board, long remoteVersion) {
		if(board!=null) {
			// well you cannot remotely change a remote board...
			if(board.isRemote() || !board.clear(remoteVersion)) {
				// some other peer modified the board in between
				log.info("Remote clear rejected on " + board.getName());
				drawSelectedWhiteboard();
				return false;
			} else {
				// was accepted locally, so push to subscribers
				drawSelectedWhiteboard();
				broadcastChanges(board);
				log.info("Remote clear accepted on " + board.getName());
				return true;
			}
		} else {
			log.severe("Remote clear targeted for a non-existent board");
			return false;
		}
	}
	
	/**
	 * Undo the last path of the selected whiteboard.
	 */
	public void undoLocally() {
		if(selectedBoard!=null) {
			if (selectedBoard.isRemote()) {
				selectedBoard.getRemoteSource().emit(boardUndoUpdate, selectedBoard.getName() + "%"
						+ selectedBoard.getVersion() + "%");
				log.info("Pushed undo to remote board " + selectedBoard.getName());
			} else {
				if(!selectedBoard.undo(selectedBoard.getVersion())) {
					// some other peer modified the board in between
					drawSelectedWhiteboard();
				} else {
					// was accepted locally, so push to subscribers
					drawSelectedWhiteboard();
					broadcastChanges(selectedBoard);
				}
			}
		} else {
			log.severe("Local undo without a selected board");
		}
	}

	/**
	 * Invoked by remote undo request. Try to accept and push to subscribers.
	 * @param board board
	 * @param remoteVersion version
	 * @return success or not
	 */
	public boolean undoRemotely(Whiteboard board, long remoteVersion) {
		if(board!=null) {
			// well you cannot remotely change a remote board...
			if(board.isRemote() || !board.undo(remoteVersion)) {
				// some other peer modified the board in between
				log.info("Remote undo rejected on " + board.getName());
				return false;
			} else {
				// was accepted locally, so push to subscribers
				drawSelectedWhiteboard();
				broadcastChanges(board);
				log.info("Remote undo accepted on " + board.getName());
				return true;
			}
		} else {
			log.severe("Remote undo targeted for a non-existent board");
			return false;
		}
	}

	/**
	 * The variable selectedBoard has been set.
	 */
	public void selectedABoard() {
		drawSelectedWhiteboard();
		log.info("selected board: "+selectedBoard.getName());
	}
	
	/**
	 * Set the share status on the selected board.
	 */
	public void setShare(boolean share) {
		if(selectedBoard != null && !selectedBoard.isRemote()) {
        	selectedBoard.setShared(share);
        	setShareToServer(selectedBoard, share);
        } else {
        	log.severe("there is no selected board");
        }
	}
	
	/**
	 * Called by the gui when the user closes the app.
	 */
	public void guiShutdown() {
		System.out.println("GUI application terminated, exiting...");
		log.info("GUI application terminated, cleaning up...");

		// do some final cleanup
		HashSet<Whiteboard> existingBoards = new HashSet<>(whiteboards.values());
		existingBoards.forEach((board)-> deleteBoard(board.getName()));
    	peerManager.getServerManager().forceShutdown();
    	peerManager.shutdown();
		log.info("Clean up finished, terminating...");
	}

	/*
	 * 
	 * GUI methods and callbacks from GUI for user actions.
	 * You probably do not need to modify anything below here.
	 * 
	 */

	/*
	 * GUI objects, you probably don't need to modify these things... you don't
	 * need to modify these things... don't modify these things [LOTR reference?].
	 */

	JButton clearBtn, blackBtn, redBtn, createBoardBtn, deleteBoardBtn, undoBtn;
	JCheckBox sharedCheckbox;
	DrawArea drawArea;
	JComboBox<String> boardComboBox;
	boolean modifyingComboBox = false;
	boolean modifyingCheckBox = false;


	/**
	 * Redraw the screen with the selected board
	 */
	public void drawSelectedWhiteboard() {
		drawArea.clear();
		if(selectedBoard!=null) {
			selectedBoard.draw(drawArea);
		}
	}
	
	/**
	 * Setup the Swing components and start the Swing thread, given the
	 * peer's specific information, i.e. peer:port string.
	 */
	public void show(String peerPort) {
		// create main frame
		JFrame frame = new JFrame("Whiteboard Peer: "+peerPort);
		Container content = frame.getContentPane();
		// set layout on content pane
		content.setLayout(new BorderLayout());
		// create draw area
		drawArea = new DrawArea(this);

		// add to content pane
		content.add(drawArea, BorderLayout.CENTER);

		// create controls to apply colors and call clear feature
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

		/*
		 * Action listener is called by the GUI thread.
		 */
		ActionListener actionListener = e -> {
			if (e.getSource() == clearBtn) {
				clearedLocally();
			} else if (e.getSource() == blackBtn) {
				drawArea.setColor(Color.black);
			} else if (e.getSource() == redBtn) {
				drawArea.setColor(Color.red);
			} else if (e.getSource() == boardComboBox) {
				if(modifyingComboBox) return;
				if(boardComboBox.getSelectedIndex()==-1) return;
				String selectedBoardName=(String) boardComboBox.getSelectedItem();
				if(whiteboards.get(selectedBoardName)==null) {
					log.severe("selected a board that does not exist: "+selectedBoardName);
					return;
				}
				selectedBoard = whiteboards.get(selectedBoardName);
				// remote boards can't have their shared status modified
				if(selectedBoard.isRemote()) {
					sharedCheckbox.setEnabled(false);
					sharedCheckbox.setVisible(false);
				} else {
					modifyingCheckBox=true;
					sharedCheckbox.setSelected(selectedBoard.isShared());
					modifyingCheckBox=false;
					sharedCheckbox.setEnabled(true);
					sharedCheckbox.setVisible(true);
				}
				selectedABoard();
			} else if (e.getSource() == createBoardBtn) {
				createBoard();
			} else if (e.getSource() == undoBtn) {
				if(selectedBoard==null) {
					log.severe("there is no selected board to undo");
					return;
				}
				undoLocally();
			} else if (e.getSource() == deleteBoardBtn) {
				if(selectedBoard==null) {
					log.severe("there is no selected board to delete");
					return;
				}
				deleteBoard(selectedBoard.getName());
			}
		};
		
		clearBtn = new JButton("Clear Board");
		clearBtn.addActionListener(actionListener);
		clearBtn.setToolTipText("Clear the current board - clears remote copies as well");
		clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		blackBtn = new JButton("Black");
		blackBtn.addActionListener(actionListener);
		blackBtn.setToolTipText("Draw with black pen");
		blackBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		redBtn = new JButton("Red");
		redBtn.addActionListener(actionListener);
		redBtn.setToolTipText("Draw with red pen");
		redBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		deleteBoardBtn = new JButton("Delete Board");
		deleteBoardBtn.addActionListener(actionListener);
		deleteBoardBtn.setToolTipText("Delete the current board - only deletes the board locally");
		deleteBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		createBoardBtn = new JButton("New Board");
		createBoardBtn.addActionListener(actionListener);
		createBoardBtn.setToolTipText("Create a new board - creates it locally and not shared by default");
		createBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		undoBtn = new JButton("Undo");
		undoBtn.addActionListener(actionListener);
		undoBtn.setToolTipText("Remove the last path drawn on the board - triggers an undo on remote copies as well");
		undoBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		sharedCheckbox = new JCheckBox("Shared");
		sharedCheckbox.addItemListener(e -> {
		   if(!modifyingCheckBox) setShare(e.getStateChange()== ItemEvent.SELECTED);
		});
		sharedCheckbox.setToolTipText("Toggle whether the board is shared or not - tells the whiteboard server");
		sharedCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);
		

		// create a drop list for boards to select from
		JPanel controlsNorth = new JPanel();
		boardComboBox = new JComboBox<>();
		boardComboBox.addActionListener(actionListener);
		
		
		// add to panel
		controlsNorth.add(boardComboBox);
		controls.add(sharedCheckbox);
		controls.add(createBoardBtn);
		controls.add(deleteBoardBtn);
		controls.add(blackBtn);
		controls.add(redBtn);
		controls.add(undoBtn);
		controls.add(clearBtn);

		// add to content pane
		content.add(controls, BorderLayout.WEST);
		content.add(controlsNorth,BorderLayout.NORTH);

		frame.setSize(600, 600);
		
		// create an initial board
		createBoard();
		
		// closing the application
		frame.addWindowListener(new WindowAdapter() {
		    @Override
		    public void windowClosing(WindowEvent windowEvent) {
		        if (JOptionPane.showConfirmDialog(frame, 
		            "Are you sure you want to close this window?", "Close Window?", 
		            JOptionPane.YES_NO_OPTION,
		            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
		        	guiShutdown();
		            frame.dispose();
		        }
		    }
		});
		
		// show the swing paint result
		frame.setVisible(true);
		
	}
	
	/**
	 * Update the GUI's list of boards. Note that this method needs to update data
	 * that the GUI is using, which should only be done on the GUI's thread, which
	 * is why invoke later is used.
	 * 
	 * @param select, board to select when list is modified or null for default
	 *                selection
	 */
	private void updateComboBox(String select) {
		SwingUtilities.invokeLater(() -> {
			modifyingComboBox=true;
			boardComboBox.removeAllItems();
			int anIndex=-1;
			synchronized(whiteboards) {
				ArrayList<String> boards = new ArrayList<>(whiteboards.keySet());
				Collections.sort(boards);
				for(int i=0;i<boards.size();i++) {
					String boardName=boards.get(i);
					boardComboBox.addItem(boardName);
					if(select!=null && select.equals(boardName)) {
						anIndex=i;
					} else if(anIndex==-1 && selectedBoard!=null &&
							selectedBoard.getName().equals(boardName)) {
						anIndex=i;
					}
				}
			}
			modifyingComboBox=false;
			if(anIndex!=-1) {
				boardComboBox.setSelectedIndex(anIndex);
			} else {
				if(whiteboards.size()>0) {
					boardComboBox.setSelectedIndex(0);
				} else {
					drawArea.clear();
					createBoard();
				}
			}

		});
	}
	
}
