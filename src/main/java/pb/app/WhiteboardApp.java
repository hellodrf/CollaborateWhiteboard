package pb.app;

import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.PeerManager;
import pb.managers.endpoint.Endpoint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;


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

	PeerManager peerManager = null;

	ClientManager serverClientManager = null;

	Map<String, Endpoint> peerEndpointMap = null;

	Endpoint serverEndpoint = null;
	

	/**
	 * Initialize the white board app.
	 */
	public WhiteboardApp(int peerPort, String whiteboardServerHost,
			int whiteboardServerPort) throws UnknownHostException, InterruptedException {
		this.whiteboards = new ConcurrentHashMap<>();
		this.peerEndpointMap = new ConcurrentHashMap<>();
		this.peerPort = String.valueOf(peerPort);
		this.peerManager = new PeerManager(peerPort);
		connectToServer(peerPort, whiteboardServerHost, whiteboardServerPort);
		show(this.peerPort);
	}

	private void connectToServer(int peerPort, String host, int port)
			throws InterruptedException, UnknownHostException {
        serverClientManager = peerManager.connect(port, host);
		serverClientManager.on(PeerManager.peerStarted, (args) -> {
            serverEndpoint = (Endpoint)args[0];
            System.out.println("Connected to server: " + serverEndpoint.getOtherEndpointId());
            serverEndpoint.on(WhiteboardServer.sharingBoard, (args1 -> {
				String[] parts = parsePeer((String) args1[0]);
            	if (!whiteboards.containsKey(parts[2])) {
					String pp = parts[0]+":"+parts[1];
					Endpoint endpoint;
					if (!peerEndpointMap.containsKey(pp)) {
						ClientManager clientManager = connectToPeer(parts[0], Integer.parseInt(parts[1]));
						clientManager.start();
					} else {
						endpoint = peerEndpointMap.get(pp);
					}

				}
            })).on(WhiteboardServer.unsharingBoard, args1 -> {
                String[] parts = parsePeer((String) args1[0]);
                removeBoard(parts[2]);
            });
	    }).on(PeerManager.peerStopped, (args) -> {
            Endpoint endpoint = (Endpoint) args[0];
            log.info("Connection to server ended: " + endpoint.getOtherEndpointId());
	    }).on(PeerManager.peerError, (args) -> {
            Endpoint endpoint = (Endpoint) args[0];
            log.severe("Connection to server ended in error: " + endpoint.getOtherEndpointId());
            endpoint.close();
        });
	}

	private ClientManager connectToPeer(String host, int port) {
		ClientManager clientManager = null;
		try {
			clientManager = peerManager.connect(port, host);
			clientManager.on(PeerManager.peerStarted, (args) -> {
				Endpoint endpoint = (Endpoint) args[0];
				log.info("Peer connected: " + endpoint.getOtherEndpointId());
				endpoint.on(boardData, (args1 -> {
					String name = getBoardName((String) args1[0]);
					String data = getBoardData((String) args1[0]);
					long version = getBoardVersion((String) args1[0]);
					if (!whiteboards.containsKey(name) || whiteboards.get(name).getVersion()<version) {
						Whiteboard whiteboard = new Whiteboard(name, true);
						whiteboard.whiteboardFromString(name, data);
						whiteboards.put(name, whiteboard);
					}
				}));
			}).on(PeerManager.peerStopped, (args) -> {
				Endpoint endpoint = (Endpoint) args[0];
				log.info("Peer disconnected: " + endpoint.getOtherEndpointId());
			}).on(PeerManager.peerError, (args) -> {
				Endpoint endpoint = (Endpoint) args[0];
				log.severe("Peer connection ended in error: " + endpoint.getOtherEndpointId());
				endpoint.close();
			});
		} catch (UnknownHostException | InterruptedException e) {
			e.printStackTrace();
		}
		return clientManager;
	}

	/*
	 * Methods called from events.
	 * TODO
	 */
	
	// From whiteboard server

	
	// From whiteboard peer

    private void acceptBoard() {

    }

    private void removeBoard(String boardID
	) {
	    whiteboards.remove(boardID
		);
    }

	private void shareBoard(String boardID
	) {
        serverEndpoint.emit(WhiteboardServer.shareBoard, peerPort + ":" + boardID
		);
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
	public void waitToFinish() {
		
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
		synchronized(whiteboards) {
			Whiteboard whiteboard = whiteboards.get(boardName);
			if(whiteboard!=null) {
				whiteboards.remove(boardName);
			}
		}
		updateComboBox(null);
	}
	
	/**
	 * Create a new local board with name peer:port:boardID
	 * .
	 * The boardID
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
			if(!selectedBoard.addPath(currentPath,selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard(); // just redraw the screen without the path
			} else {
				// was accepted locally, so do remote stuff if needed
				
			}
		} else {
			log.severe("path created without a selected board: "+currentPath);
		}
	}
	
	/**
	 * Clear the selected whiteboard.
	 */
	public void clearedLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.clear(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				// was accepted locally, so do remote stuff if needed
				
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("cleared without a selected board");
		}
	}
	
	/**
	 * Undo the last path of the selected whiteboard.
	 */
	public void undoLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.undo(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("undo without a selected board");
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
		if(selectedBoard != null) {
        	selectedBoard.setShared(share);
        } else {
        	log.severe("there is no selected board");
        }
	}
	
	/**
	 * Called by the gui when the user closes the app.
	 */
	public void guiShutdown() {
		// do some final cleanup
		HashSet<Whiteboard> existingBoards= new HashSet<>(whiteboards.values());
		existingBoards.forEach((board)-> deleteBoard(board.getName()));
    	whiteboards.values().forEach((whiteboard)->{
    		
    	});
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
		            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
		        {
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
					String boardname=boards.get(i);
					boardComboBox.addItem(boardname);
					if(select!=null && select.equals(boardname)) {
						anIndex=i;
					} else if(anIndex==-1 && selectedBoard!=null &&
							selectedBoard.getName().equals(boardname)) {
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
