/*
 * Import our classes...
 */
package Javawolf;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.ReplyConstants;
import org.jibble.pircbot.User;

/**
 * @author Reaper Eternal
 *
 */
public class Javawolf extends PircBot {
	// Initialization members read from config file
    private String server;
    private int port;
    private String username;
    private String nick;
	private String channel;
	private String wolfChannel;
	private String tavernChannel;
    private String password;
	
	// Command character
	public String cmdchar;
	// Trusted players
	public List<String> trustedHosts;
	// Ignored players
	public List<String> ignoredHosts;
	// Ignored players
	public List<String> cmdBans;
	// Our game
	private WolfGame game;
    // Whether or not to show welcome message
	private boolean useWelcomeMsg;
    // Default player configuration
	private String defaultPConfig;
    // Storage of the server reply from raw WHO command
	public String whoReply;
    
	// Static logging codes
    public static final int LOG_STARTUP = 0;
	public static final int LOG_CONSOLE = 1;
	public static final int LOG_CONNECT = 2;
	public static final int LOG_PLAYERCONFIG = 3;
	public static final int LOG_ADMIN = 4;
	public static final int LOG_GAME = 5;
	
	/**
	 * Creates an instance of Javawolf
	 * 
	 * @param configFile file path of configuration file
	 */
	public Javawolf(String configFile) {
		// Initialization and setting defaults
        cmdchar = "!";
        setMessageDelay(200);
        trustedHosts = new ArrayList<String>();
		ignoredHosts = new ArrayList<String>();
		cmdBans = new ArrayList<String>();
        game = null;
        useWelcomeMsg = true;
        // load default player config
        defaultPConfig = "sample.cfg";
        wolfChannel = null;
        tavernChannel = null;
        String line, variable=null, value=null;
		BufferedReader cfg = null;
        
		// Reads and parses the configuration file
        try {
            cfg = new BufferedReader(new FileReader(configFile));
			while(cfg.ready()) {
                line = cfg.readLine().trim(); // trim whitespace
				if(line.startsWith("#")) continue; // comments
				if(line.equals("")) continue; // empty lines
				// Split along the first ':' character
                StringTokenizer st = new StringTokenizer(line, ":");
                if (st.countTokens() == 2){
                    variable = st.nextToken().trim().toLowerCase();
					value = st.nextToken().trim();
                    if(variable.equals("nick")) {   // nickname
						nick = value;
					} else if(variable.equals("username")) {// username
						username = value;
					} else if(variable.equals("login")) {   // string to PM to NickServ to identify
						password = value;
					} else if(variable.equals("server")) {  // server
						server = value;
					} else if(variable.equals("port")) {    // port
						try {
							port = Integer.parseInt(value);
						} catch(NumberFormatException e) {
                            this.logEvent("Could not parse port: \"" + value + "\"!", LOG_STARTUP, true);
							System.exit(1);
						}
					} else if(variable.equals("channel")) { // channel
						channel = value;
					} else if(variable.equals("wolfchan")) {// channel
						wolfChannel = value;
					} else if(variable.equals("tavernchan")) {// channel
						tavernChannel = value;
					} else if(variable.equals("admin")) {   // bot admins
						trustedHosts.add(value);
					} else if(variable.compareTo("ignored") == 0) { // ignored users
						ignoredHosts.add(value);
					} else if(variable.compareTo("cmdban") == 0) {  // bot admins
						cmdBans.add(value.toLowerCase());
					} else if(variable.compareTo("welcome") == 0) { // welcome on join?
						useWelcomeMsg = Boolean.parseBoolean(value);
					} else if(variable.compareTo("playerconfig") == 0) {    // default player config
                        this.logEvent("Set configuration file to \"" + value + "\".", LOG_STARTUP, false);
						defaultPConfig = value;
					} else {
						// unknown variable
                        this.logEvent("Unknown variable \"" + variable + "\".", LOG_STARTUP, false);
					}
                }
                
			}
        } catch(FileNotFoundException e) {
			System.err.println("[STARTUP] : Could not load configuration file. Aborting.");
			System.err.println("[STARTUP] : " + e.getMessage());
			System.exit(1);
        } catch(IOException e) {
			System.err.println("[STARTUP] : Error processing configuration file. Aborting.");
			System.exit(1);
		}
        
        setName(nick);
        setLogin(username);
        setVersion("JavaWolf v0.2 with PircBot");
        setVerbose(false);
	}

	/**
	 * Main entry point
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
        // Create the wolfbot with the specified configuration file
        Javawolf wolfbot = new Javawolf("Javawolf.ini");;
        // Attempt to connect to server 3 times.
        boolean connected = false;
        for (int ctr = 0; ctr < 3; ctr++){
            try {
                wolfbot.logEvent("Connecting to " + wolfbot.server + ":" + wolfbot.port, LOG_CONSOLE, false);
                wolfbot.connect(wolfbot.server, wolfbot.port);
                connected = true;
                break;
            } catch(NickAlreadyInUseException e) { 
                wolfbot.logEvent(wolfbot.nick + " is in use. Trying " + wolfbot.nick + "_", LOG_CONSOLE, false);
                wolfbot.nick += "_";
                wolfbot.setName(wolfbot.nick);
            }    
            catch(IrcException e) { 
                wolfbot.logEvent(e.getMessage(), LOG_CONNECT, true);
            } 
            catch(IOException e) { 
                wolfbot.logEvent(e.getMessage(), LOG_CONNECT, true);
            }

            // Wait 1 second if connection fails
            try { Thread.sleep(1000); } catch(InterruptedException e) {}
        }
        if (!connected){
            wolfbot.logEvent("Unable to connect to server.", LOG_CONNECT, true);
        }
	}
    
    @Override
    protected void onServerResponse(int code, String response){
        if (code == ReplyConstants.RPL_WHOREPLY){
            whoReply = response;
        }
    }
    
    @Override
    protected void onConnect(){
        identify(password);
    }
    
	@Override
	protected void onPrivateMessage(String sender, String login, String hostname, String message) {
		if(ignoredHosts.contains(hostname)) return;
		message = message.trim();
		String[] args = message.split(" ");
		String cmd = args[0];
		if(cmd.startsWith(cmdchar) && cmd.length() > cmdchar.length()){
            cmd = cmd.substring(cmdchar.length()); // remove command character
            cmdToGame(cmd, args, sender, login, hostname);
        }
	}
	
	@Override
	protected void onJoin(String channel, String sender, String login, String hostname) {
		// Only generate game when bot joins the main channel.
		if(sender.equals(getNick())) {
			if(channel.equals(channel)) {
				// Joined the main channel.
                logEvent("Launching game....", LOG_CONSOLE, false);
				if(useWelcomeMsg) sendMessage(channel, "Welcome to javawolf! use " + cmdchar + "join to begin a game.");
				// create the game
				logEvent("Welcome sent. Generating game....", LOG_CONSOLE, false);
                game = new WolfGame(channel, wolfChannel, tavernChannel, defaultPConfig, this);
                logEvent("Ready.", LOG_CONSOLE, false);
			} else if(wolfChannel.equals(channel)) {
				// Joined the wolf channel.
			}
		}
	}
	
	@Override
	protected void onUserList(String channel, User[] users) {
		if(wolfChannel != null && wolfChannel.equals(channel)) {
			// Joined the wolf channel.
			// Kick everybody who isn't ourself or ChanServ.
			String tNick;
			for (int m = 0; m < users.length; m++) {
				// Retrieve his nick.
				tNick = users[m].getNick();
				if(tNick.equals("ChanServ") || tNick.equals(getNick()))
                    continue; // Don't kick ChanServ, don't kick ourself
				kick(wolfChannel, tNick, "Clearing wolf channel");
				//this.sendRawLineViaQueue("KICK " + wolfChannel + " " + nick + " :Clearing wolf channel.");
				// next guy
			}
		} else if(tavernChannel != null && tavernChannel.equals(channel)) {
			// Joined the wolf channel.
			// Kick everybody who isn't ourself or ChanServ.
			String tNick;
			for (int m = 0; m < users.length; m++) {
				// Retrieve his nick.
				tNick = users[m].getNick();
				if(tNick.equals("ChanServ") || tNick.equals(getNick()))
                    continue; // Don't kick ChanServ, don't kick ourself
				kick(tavernChannel, tNick, "Clearing tavern channel");
				//this.sendRawLineViaQueue("KICK " + wolfChannel + " " + nick + " :Clearing wolf channel.");
				// next guy
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
                String cmd = args[0];
                if(cmd.startsWith(cmdchar) && cmd.length() > cmdchar.length()){
                    cmd = cmd.substring(cmdchar.length());
                    cmdToGame(cmd, args, sender, login, hostname);
                }
			} else {
				// WTH?
				System.err.println("[GAME STATE ERROR] : Could not pass command. Game set to null!");
			}
		}
		// Reset idlers
		if(game != null) game.resetIdle(sender, login, hostname);
    }
    
	@Override
	protected void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String notice) {
		if(ignoredHosts.contains(sourceHostname)) return;
		if(sourceNick.contains("NickServ")) {
			if(notice.contains("You are now identified")) {
				// authenticated with NickServ; join
				this.logEvent("Joining " + channel, LOG_CONSOLE, false);
				joinChannel(channel);
				sendMessage("ChanServ", "OP " + channel + " " + getNick());
				if(wolfChannel != null) {
					joinChannel(wolfChannel);
					sendMessage("ChanServ", "OP " + wolfChannel + " " + getNick());
				}
				if(tavernChannel != null) {
					joinChannel(tavernChannel);
					sendMessage("ChanServ", "OP " + tavernChannel + " " + getNick());
				}
			}
        } else if (sourceNick.contains("freenode.net")){ 
            this.logEvent("\"" + notice + "\" sent by " + sourceNick + ".", LOG_CONSOLE, false);
		} else if(game != null) {
			String message = notice.trim();
			String[] args = message.split(" ");
			String cmd = args[0];
			if(cmd.startsWith(cmdchar) && cmd.length() > cmdchar.length()){
                cmd = cmd.substring(cmdchar.length()); // remove command character
                cmdToGame(cmd, args, sourceNick, sourceLogin, sourceHostname);
            }
        } else {
			// WTH?
			System.err.println("[GAME STATE ERROR] : Could not pass command. Game set to null! \"" + notice + "\" sent by " + sourceNick + ".");
		}
	}
	
	/**
	 * Sends a command to the game.
	 * 
	 * @param cmd
	 * @param args
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void cmdToGame(String cmd, String[] args, String nick, String user, String host) {
		// Is the command banned?
		if(cmdBans.contains(cmd.toLowerCase())) {
			sendMessage(nick, "The command \"" + cmdchar + cmd + "\" has been banned from use.");
			return;
		}
		// Sends the lowercase command to the game.
		game.parseCommand(cmd.toLowerCase(), args, nick, user, host);
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
	
	/**
	 * Logs events
	 * @param event
	 * @param type
	 * @param who
	 */
	public void logEvent(String event, int type, boolean error) {
        String logStr = "";
        // Set the log type
        switch (type) {
            case LOG_STARTUP:
                logStr += "[STARTUP] : ";
                break;
            case LOG_CONSOLE:
                logStr += "[CONSOLE] : ";
                break;
            case LOG_CONNECT:
                logStr += "[CONNECT] : ";
                break;
            case LOG_PLAYERCONFIG:
                logStr += "[PLAYERCONFIG] : ";
                break;
            case LOG_ADMIN:
                logStr += "[ADMIN] : ";
                break;
            case LOG_GAME:
                logStr += "[GAME] : ";
                break;
        }
        
        // Output error or normal message to console
        if (error)
            System.err.println(logStr + event);
        else
            System.out.println(logStr + event);
	}
}

