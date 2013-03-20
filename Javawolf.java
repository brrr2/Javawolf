/*
 * Import our classes...
 */
package Javawolf;

import java.io.*;
import java.util.*;
import org.jibble.*;
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
	// Server we're on
	private String server = null;
	// Login string
	private static String login_str = null;
	// Command character
	public static String cmdchar = "!";
	// Trusted players
	public static List<String> trustedHosts = null;
	// Our game
	private WolfGame game = null;
	// Whether to use the welcome message
	private static boolean useWelcomeMsg = true;
	
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
	public Javawolf(String server, int port, String channel_to_join, String username, String nick) {
		// grr, stupid hack
		Javawolf.wolfbot = this;
		this.server = server;
		// connect
		boolean connected = false;
		while(!connected) {
			this.setName(nick);
			this.setLogin(username);
			try {
				this.connect(server, port);
				connected = true;
				System.out.println("[CONSOLE] : Launching game....");
			} catch(NickAlreadyInUseException e) {
				nick = nick + "_";
				connected = false;
			} catch(IrcException e) {
				
			} catch(IOException e) {
				
			}
			// wait
			try {
				Thread.sleep(1000);
			} catch(InterruptedException e) {
				
			}
		}
		// log in
		this.sendMessage("NickServ", login_str);
		
		channel = channel_to_join;
	}

	/**
	 * Main entry point
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		// initialize variables
		String cfgSrv = null, cfgChan = null, cfgUser = null, cfgNick = null, cfgLine = null, variable, value;
		int cfgPort = 0;
		BufferedReader cfg = null;
		WolfConfig wc = null;
		boolean isPlayerConfig = false;
		trustedHosts = new ArrayList<String>();
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
					if(!isPlayerConfig) {
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
						} else if(variable.compareTo("admin") == 0) {
							// bot admins
							trustedHosts.add(value);
						} else if(variable.compareTo("welcome") == 0) {
							// welcome on join?
							useWelcomeMsg = Boolean.parseBoolean(value);
						} else if(variable.compareTo("playerconfig") == 0) {
							// starts a player configuration setup
							wc = new WolfConfig();
							isPlayerConfig = true;
							int braceLoc = value.indexOf("{");
							if(braceLoc != -1) {
								// retrieve what is being set
								String playerbounds = value.substring(0, braceLoc).trim();
								int splitLoc = playerbounds.indexOf("-");
								if(splitLoc != -1) {
									try {
										wc.low = Integer.parseInt(playerbounds.substring(0, splitLoc).trim());
										wc.high = Integer.parseInt(playerbounds.substring(splitLoc+1).trim());
									} catch(NumberFormatException e) {
										System.err.println("[STARTUP] : Could not parse integers in player configuration: \"" + cfgLine + "\"!");
										System.exit(1);
									}
								} else {
									System.err.println("[STARTUP] : Could not parse player configuration: \"" + cfgLine + "\"!");
									System.exit(1);
								}
							} else {
								System.err.println("[STARTUP] : Could not parse player configuration: \"" + cfgLine + "\"!");
								System.exit(1);
							}
						} else {
							// unknown variable
							System.out.println("[STARTUP] : Unknown variable \"" + variable + "\".");
						}
					} else {
						// set player configuration-specifc variables
						// ------- VILLAGE PRIMARY ROLES -------
						if(variable.compareTo("seer") == 0) {
							// seer count
							try {
								wc.seercount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse seer count: \"" + value + "\"!");
								System.exit(1);
							}
						} else if(variable.compareTo("drunk") == 0) {
							// drunk count
							try {
								wc.drunkcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse drunk count: \"" + value + "\"!");
								System.exit(1);
							}
						} else if(variable.compareTo("harlot") == 0) {
							// harlot count
							try {
								wc.harlotcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse harlot count: \"" + value + "\"!");
								System.exit(1);
							}
						} else if(variable.compareTo("angel") == 0) {
							// angel count
							try {
								wc.angelcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse angel count: \"" + value + "\"!");
								System.exit(1);
							}
						} else if(variable.compareTo("detective") == 0) {
							// detective count
							try {
								wc.detectivecount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse detective count: \"" + value + "\"!");
								System.exit(1);
							}
						} else if(variable.compareTo("medium") == 0) {
							// medium count
							try {
								wc.mediumcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse medium count: \"" + value + "\"!");
								System.exit(1);
							}
						} // ------- VILLAGE SECONDARY ROLES -------
						else if(variable.compareTo("gunner") == 0) {
							// gunner count
							try {
								wc.gunnercount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse gunner count: \"" + value + "\"!");
								System.exit(1);
							}
						} else if(variable.compareTo("cursed") == 0) {
							// cursed count
							try {
								wc.cursedcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse cursed count: \"" + value + "\"!");
								System.exit(1);
							}
						} // ------- WOLF ROLES -------
						else if(variable.compareTo("wolf") == 0) {
							// wolf count
							try {
								wc.wolfcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse wolf count: \"" + value + "\"!");
								System.exit(1);
							}
						} else if(variable.compareTo("traitor") == 0) {
							// traitor count
							try {
								wc.traitorcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse traitor count: \"" + value + "\"!");
								System.exit(1);
							}
						} else if(variable.compareTo("werecrow") == 0) {
							// werecrow count
							try {
								wc.werecrowcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse werecrow count: \"" + value + "\"!");
								System.exit(1);
							}
						} else if(variable.compareTo("sorcerer") == 0) {
							// seer count
							try {
								wc.sorcerercount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[STARTUP] : Could not parse sorcerer count: \"" + value + "\"!");
								System.exit(1);
							}
						} else {
							// unknown variable
							System.out.println("[STARTUP] : Unknown player configuration variable \"" + variable + "\".");
						}
					}
				} else if(cfgLine.compareTo("}") == 0) {
					// End of one <WolfConfig> setup. Add to the list.
					WolfGame.addconfig(wc);
					isPlayerConfig = false;
				}
			}
		} catch(IOException e) {
			System.err.println("[STARTUP] : Error processing configuration file. Aborting.");
			System.exit(1);
		}
		// create the wolfbot
		new Javawolf(cfgSrv, cfgPort, cfgChan, cfgUser, cfgNick);
	}
	
	@Override
	protected void onPrivateMessage(String sender, String login, String hostname, String message) {
		message = message.trim();
		String[] args = message.split(" ");
		String cmd = args[0];
		game.parseCommand(cmd, args, sender, login, hostname);
	}
	
	@Override
	protected void onJoin(String channel, String sender, String login, String hostname) {
		if(sender.contentEquals("Javawolf")) {
			this.sendMessage(channel, "Welcome to javawolf! use " + cmdchar + "join to begin a game.");
			// create the game
			logEvent("Generating game....", LOG_CONSOLE, null);
			game = new WolfGame(channel, server);
		}
	}
	
	@Override
	protected void onMessage(String channel, String sender, String login, String hostname, String message) {
		if(message.startsWith(cmdchar)) {
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
		game.resetidle(sender, login, hostname);
	}
	
	@Override
	protected void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String notice) {
		if(sourceNick.contains("NickServ")) {
			if(notice.contains("You are now identified")) {
				// authenticated with NickServ; join
				System.out.println("[CONSOLE] : Joining " + channel);
				this.joinChannel(channel);
				this.sendMessage("ChanServ", "OP " + channel + " " + this.getNick());
			}
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
		} else if(game != null) game.playerKickedFromChannel(recipientNick); // Someone else did.
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

