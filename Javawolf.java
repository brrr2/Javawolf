/*
 * Import our classes...
 */
package Javawolf;

import java.io.*;
import java.util.*;
//import org.jibble.*;
import org.jibble.pircbot.*;


/**
 * @author Reaper Eternal
 *
 */
public class Javawolf extends PircBot {
	// Object for sending events
	public static Javawolf wolfbot = null;
	// Channel to enter
	private String channel = null;
	private String wolfChannel = null;
	private String tavernChannel = null;
	// Server we're on
	private String server = null;
	// Login string
	private static String login_str = null;
	// Command character
	public static String cmdchar = "!";
	// Trusted players
	public static List<String> trustedHosts = null;
	// Ignored players
	public static List<String> ignoredHosts = null;
	// Our game
	private WolfGame game = null;
	// Whether to use the welcome message
	private static boolean useWelcomeMsg = true;
	// What player config to load as default
	private static String defaultPConfig = "default.cfg";
	// Timing of messages
	private static long msg_delay = 200;
	
	// logging
	private static final int LOG_CONSOLE = 0;
	private static final int LOG_PRIVMSG = 1;
	private static final int LOG_PUBMSG  = 2;
	private static final int LOG_NOTICE  = 3;
	private static final int LOG_GAME    = 4;
	
	/**
	 * Creates the wolfbot
	 * 
	 * @param server
	 * @param port
	 * @param channel_to_join
	 * @param username
	 * @param nick
	 */
	public Javawolf(String server, int port, String channel_to_join, String username, String nick, String wolfChan_to_join, String tavernChan_to_join) {
		// grr, stupid hack
		Javawolf.wolfbot = this;
		this.server = server;
		this.setMessageDelay(msg_delay);
		// connect
		boolean connected = false;
		while(!connected) {
			this.setName(nick);
			this.setLogin(username);
			try {
				System.out.println("[CONSOLE] : Connecting to " + server + ":" + port);
				this.connect(server, port);
				connected = true;
				System.out.println("[CONSOLE] : Launching game....");
			} catch(NickAlreadyInUseException e) {
				nick = nick + "_";
				connected = false;
			} catch(IrcException e) {
				connected = false;
			} catch(IOException e) {
				connected = false;
			}
			// wait
			try {
				Thread.sleep(1000);
			} catch(InterruptedException e) {
				
			}
		}
		// set up channels
		this.channel = channel_to_join;
		this.wolfChannel = wolfChan_to_join;
		this.tavernChannel = tavernChan_to_join;
		// log in
		this.sendRawLine("USER " + username + " 8 * :" + "Java Wolf Bot v0.2");
		this.sendMessage("NickServ", login_str);
	}

	/**
	 * Main entry point
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		// initialize variables
		String cfgSrv = null, cfgChan = null, cfgWolfChan = null, cfgTavernChan = null, cfgUser = null, cfgNick = null, cfgLine = null, variable, value;
		int cfgPort = 0;
		BufferedReader cfg = null;
		WolfConfig wc = null;
		boolean isPlayerConfig = false;
		trustedHosts = new ArrayList<String>();
		ignoredHosts = new ArrayList<String>();
		// Reads and parses the configuration file
		try {
			cfg = new BufferedReader(new FileReader("Javawolf.ini"));
		} catch(FileNotFoundException e) {
			System.err.println("[STARTUP] : Could not load configuration file. Aborting.");
			System.err.println("[STARTUP] : " + e.getMessage());
			System.exit(1);
		}
		try {
			while((cfgLine = cfg.readLine()) != null) {
				cfgLine = cfgLine.trim(); // trim whitespace
				if(cfgLine.startsWith("#")) continue; // comments
				if(cfgLine == "") continue; // empty lines
				// Split along the first ':' character
				int charLoc = cfgLine.indexOf(":");
				if(charLoc != -1) {
					// retrieve what is being set
					variable = cfgLine.substring(0, charLoc).trim().toLowerCase();
					value = cfgLine.substring(charLoc+1).trim();
					// set general variables
					if(variable.compareTo("nick") == 0) {
						// nickname
						cfgNick = value;
					} else if(variable.compareTo("username") == 0) {
						// username
						cfgUser = value;
					} else if(variable.compareTo("login") == 0) {
						// string to PM to NickServ to identify
						login_str = value;
					} else if(variable.compareTo("server") == 0) {
						// server
						cfgSrv = value;
					} else if(variable.compareTo("port") == 0) {
						// port
						try {
							cfgPort = Integer.parseInt(value);
						} catch(NumberFormatException e) {
							System.err.println("[STARTUP] : Could not parse port: \"" + value + "\"!");
							System.exit(1);
						}
					} else if(variable.compareTo("channel") == 0) {
						// channel
						cfgChan = value;
					} else if(variable.compareTo("wolfchan") == 0) {
						// channel
						cfgWolfChan = value;
					} else if(variable.compareTo("tavernchan") == 0) {
						// channel
						cfgTavernChan = value;
					} else if(variable.compareTo("admin") == 0) {
						// bot admins
						trustedHosts.add(value);
					} else if(variable.compareTo("ignored") == 0) {
						// ignored users
						ignoredHosts.add(value);
					} else if(variable.compareTo("welcome") == 0) {
						// welcome on join?
						useWelcomeMsg = Boolean.parseBoolean(value);
					} else if(variable.compareTo("playerconfig") == 0) {
						// default player config
						System.out.println("[STARTUP] : Set configuration file to \"" + value + "\".");
						defaultPConfig = value;
					} else {
						// unknown variable
						System.out.println("[STARTUP] : Unknown variable \"" + variable + "\".");
					}
				}
			}
		} catch(IOException e) {
			System.err.println("[STARTUP] : Error processing configuration file. Aborting.");
			System.exit(1);
		}
		// create the wolfbot
		new Javawolf(cfgSrv, cfgPort, cfgChan, cfgUser, cfgNick, cfgWolfChan, cfgTavernChan);
	}
	
	@Override
	protected void onPrivateMessage(String sender, String login, String hostname, String message) {
		if(ignoredHosts.contains(hostname)) return;
		message = message.trim();
		String[] args = message.split(" ");
		String cmd = args[0];
		if(cmd.startsWith(cmdchar)) cmd = cmd.substring(cmdchar.length()); // remove command character
		game.parseCommand(cmd, args, sender, login, hostname);
	}
	
	@Override
	protected void onJoin(String channel, String sender, String login, String hostname) {
		// Only generate game when bot joins the main channel.
		if(sender.contentEquals(this.getNick())) {
			if(this.channel.contentEquals(channel)) {
				this.sendMessage(channel, "Welcome to javawolf! use " + cmdchar + "join to begin a game.");
				// create the game
				logEvent("Welcome sent. Generating game....", LOG_CONSOLE, null);
				game = new WolfGame(channel, null, null, defaultPConfig);
			}
		}
	}
	
	@Override
	protected void onMessage(String channel, String sender, String login, String hostname, String message) {
		if(message.startsWith(cmdchar)) {
			if(ignoredHosts.contains(hostname)) return;
			// now parse the command
			if(game != null) {
				message = message.trim();
				String[] args = message.split(" ");
				String cmd = args[0].substring(cmdchar.length());
				game.parseCommand(cmd, args, sender, login, hostname);
			} else {
				// WTH?
				System.err.println("[GAME STATE ERROR] : Could not pass command. Game set to null!");
			}
		}
		// Reset idlers
		if(game != null) game.resetidle(sender, login, hostname);
	}
	
	@Override
	protected void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String notice) {
		if(ignoredHosts.contains(sourceHostname)) return;
		if(sourceNick.contains("NickServ")) {
			if(notice.contains("You are now identified")) {
				// authenticated with NickServ; join
				System.out.println("[CONSOLE] : Joining " + channel);
				this.joinChannel(channel);
				this.sendMessage("ChanServ", "OP " + channel + " " + this.getNick());
			}
		} else if(game != null) {
			String message = notice.trim();
			String[] args = message.split(" ");
			String cmd = args[0];
			if(cmd.startsWith(cmdchar)) cmd = cmd.substring(cmdchar.length()); // remove command character
			game.parseCommand(cmd, args, sourceNick, sourceLogin, sourceHostname);
		}
	}
	
	@Override
	protected void onNickChange(String oldNick, String login, String hostname, String newNick) {
		// Player changed nick
		if(game != null) game.changeNick(oldNick, login, hostname, newNick);
	}
	
	@Override
	protected void onPart(String channel, String sender, String login, String hostname) {
		// Player possibly just left the game
		if(game != null) game.playerLeftChannel(sender, login, hostname);
	}
	
	@Override
	protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
		// Player possibly just left the game
		if(game != null) game.playerLeftChannel(sourceNick, sourceLogin, sourceHostname);
	}
	
	@Override
	protected void onKick(String channel, String kickerNick, String kickerLogin, String kickerHostname, String recipientNick, String reason) {
		// Player may have just been kicked out of the game
		if(recipientNick.equalsIgnoreCase(this.getNick())) {
			// We got kicked.
			game = null;
		} else if(game != null) {
			// Someone else did.
			game.playerKickedFromChannel(recipientNick);
		}
	}
	
	@Override
	protected void onVersion(String sourceNick, String sourceLogin, String sourceHostname, String target) {
		// Ignore version requests
	}
	
	/**
	 * Logs events
	 * @param event
	 * @param type
	 * @param who
	 */
	public static void logEvent(String event, int type, String who) {
		if(type == LOG_CONSOLE) {
			// console events
			System.out.println("[CONSOLE] : " + event);
		} else if(type == LOG_GAME) {
			// game events
			System.out.println("### " + who + " ### has " + event + ". ###");
		}
	}
}

