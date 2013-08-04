/*
 * Import classes
 */
package Javawolf;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import org.jibble.pircbot.Colors;

/**
 * @author Reaper Eternal
 *
 */
public class WolfGame {
    private Javawolf wolfBot;
	// Main channel associated with this instance of the game.
	private String mainChannel;
	// Wolf channel associated with this instance of the game.
	private String wolfChannel;
	// Tavern channel associated with this instance of the game.
	private String tavernChannel;
	// Player list
	private ArrayList<WolfPlayer> players;
	// Config list
	private static WolfConfig[] configs = new WolfConfig[64];
	private static int nCfgs = 0;
	// Maximum player count
	public static final int MAX_WOLFPLAYERS = 32;
	
	// Whether game is in progress
	private boolean isRunning = false;
	// Whether it is nighttime
	private boolean isNight = false;
	
	/* Guardian angel constants */
	// Chance the GA will die from guarding a wolf.
	private double dieguardingwolfpct = .50;
	// Chance the GA will die if guarding a victim.
	private double dieguardingvictimpct = .25;
	// Chance the GA will become a wolf if guarding the victim.
	private double infectguardingvictimpct = .10;
	// Chance the victim will die if guarded.
	private double guardedvictimdiepct = .25;
	// Chance the victim will become a wolf if guarded.
	private double guardedvictiminfectpct = .10;
	
	/* Detective constants */
	// Chance the detective will drop his papers.
	private double detectivefumblepct = .40;
	
	/* Gunner constants */
	// Chance the gunner will miss
	private double gunnermisspct = .20;
	// Chance the gunner will explode
	private double gunnerexplodepct = .1444;
	// Chance gunner will kill the target
	private double gunnerheadshotpct = .20;
	// Chance wolf will find a gun when killing the gunner
	private double wolffindgunpct = .3333;
	
	/* Sorcerer constants */
	// Chance victim will know he has been cursed
	private double sorcerervictimnoticecursepct = .33;
	
	/* General game constants */
	// Allow cursed seer?
	private boolean allowCursedSeer = false;
	
	// Time of start of day/night
	private long starttime = 0;
	// Time when the game can be started
	private long gameAllowStart = 0;
	// Time to wait when !wait is used (in milliseconds)
	private final long waitTime = 20*1000;
	// Maximum number of times to allow !wait
	private final int maxWaitTimes = 3;
	// Number of current waits executed
	private int nWaitTimes = 0;
	// Timer to end day/night if nobody acts
	private Timer timer = null;
	// Timer to kill idlers
	private Timer idler = null;
	// Time until idler is warned (in milliseconds)
	private final long idleWarnTime = 180*1000;
	// Time until idler is killed (in milliseconds)
	private final long idleKillTime = 300*1000;
	
	/**
	 * Creates the game
	 * 
	 * @param chan : Channel associated with this game
	 */
	public WolfGame(String mainChannel, String wolfChannel, String tavernChannel, String pConfig, Javawolf bot) {
		wolfBot = bot;
        // set last game ending to now
		gameAllowStart = System.currentTimeMillis() + 60000;
		// sets the associated channels
		this.mainChannel = mainChannel;
		this.wolfChannel = wolfChannel;
		this.tavernChannel = tavernChannel;
		// creates the lists
		players = new ArrayList<WolfPlayer>();
		// loads config
		loadPConfig(pConfig);
	}
	
    public WolfPlayer getPlayer(int n){
        return players.get(n);
    }
    public int getNumPlayers(){
        return players.size();
    }
    
	/**
	 * Loads a player configuration file.
	 * 
	 * @param filepath
	 * @return
	 */
	private boolean loadPConfig(String filepath) {
		// Declare variables initially.
		String line = null, variable = null, value = null;
		BufferedReader cfg = null;
		WolfConfig wc = null;
		WolfConfig[] newConfigs = new WolfConfig[64];
		int nNewCfgs = 0;
		boolean isSettingRoles = false;
		// Load file.
		System.out.println("[PLAYERCONFIG] : Loading \"" + filepath + ".cfg\"....");
		try {
			cfg = new BufferedReader(new FileReader(filepath + ".cfg"));
		} catch(FileNotFoundException e) {
			System.err.println("[PLAYERCONFIG] : Could not find file \"" + filepath + "\"!");
			return false;
		}
		
		try {
			while(cfg.ready()) {
                line = cfg.readLine().trim(); // trim whitespace
				if(line.startsWith("#")) continue; // comments
				if(line.equals("")) continue; // empty lines
				// Split along the first ':' character
				int charLoc = line.indexOf(":");
				if(charLoc != -1) {
					// retrieve what is being set
					variable = line.substring(0, charLoc).trim().toLowerCase();
					value = line.substring(charLoc+1).trim();
					if(!isSettingRoles) {
						if(variable.compareTo("playerconfig") == 0) {
							// starts a new player role configuration
							wc = new WolfConfig();
							isSettingRoles = true;
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
										System.err.println("[PLAYERCONFIG] : Could not parse integers in player configuration: \"" + line + "\"!");
										cfg.close();
										return false;
									}
								} else {
									System.err.println("[PLAYERCONFIG] : Could not parse player configuration: \"" + line + "\"!");
									cfg.close();
									return false;
								}
							} else {
								System.err.println("[PLAYERCONFIG] : Could not parse player configuration: \"" + line + "\"!");
								cfg.close();
								return false;
							}
						} else {
							// unknown variable
							System.out.println("[PLAYERCONFIG] : Unknown variable \"" + variable + "\".");
						}
					} else {
						// set player configuration-specific variables
						// ------- VILLAGE PRIMARY ROLES -------
						if(variable.compareTo("seer") == 0) {
							// seer count
							try {
								wc.seercount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse seer count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else if(variable.compareTo("drunk") == 0) {
							// drunk count
							try {
								wc.drunkcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse drunk count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else if(variable.compareTo("harlot") == 0) {
							// harlot count
							try {
								wc.harlotcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse harlot count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else if(variable.compareTo("angel") == 0) {
							// angel count
							try {
								wc.angelcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse angel count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else if(variable.compareTo("detective") == 0) {
							// detective count
							try {
								wc.detectivecount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse detective count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else if(variable.compareTo("medium") == 0) {
							// medium count
							try {
								wc.mediumcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse medium count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} // ------- VILLAGE SECONDARY ROLES -------
						else if(variable.compareTo("gunner") == 0) {
							// gunner count
							try {
								wc.gunnercount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse gunner count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else if(variable.compareTo("cursed") == 0) {
							// cursed count
							try {
								wc.cursedcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse cursed count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else if(variable.compareTo("lover") == 0) {
							// lover pair count
							try {
								wc.loverpaircount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse lover count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} // ------- WOLF ROLES -------
						else if(variable.compareTo("wolf") == 0) {
							// wolf count
							try {
								wc.wolfcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse wolf count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else if(variable.compareTo("traitor") == 0) {
							// traitor count
							try {
								wc.traitorcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse traitor count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else if(variable.compareTo("werecrow") == 0) {
							// werecrow count
							try {
								wc.werecrowcount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse werecrow count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else if(variable.compareTo("sorcerer") == 0) {
							// seer count
							try {
								wc.sorcerercount = Integer.parseInt(value);
							} catch(NumberFormatException e) {
								System.err.println("[PLAYERCONFIG] : Could not parse sorcerer count: \"" + value + "\"!");
								cfg.close();
								return false;
							}
						} else {
							// unknown variable
							System.out.println("[PLAYERCONFIG] : Unknown player configuration variable \"" + variable + "\".");
						}
					}
				} else if(line.compareTo("}") == 0) {
					// End of one <WolfConfig> setup. Add to the list.
					newConfigs[nNewCfgs] = wc;
					nNewCfgs++;
					isSettingRoles = false;
				}
			}
		} catch(IOException e) {
			System.err.println("[PLAYERCONFIG] : Error processing player configuration file. Aborting.");
			return false;
		}
		
		// Kill the file loading handle.
		try {
			cfg.close();
		} catch(IOException e) {
			System.err.println("[PLAYERCONFIG] : Error when closing player configuration file. Resource leak has occurred.");
		}
		cfg = null;
		// successful completion
		configs = newConfigs;
		nCfgs = nNewCfgs;
		return true;
	}
	
	/**
	 * Parses commands to the game
	 * 
	 * @param cmd
	 */
	public void parseCommand(String cmd, String[] args, String nick, String user, String host) {
		// lowercase
		cmd = cmd.toLowerCase();
		
		if(cmd.equals("join")) {
			// Joins the game
			join(nick, user, host);
		} else if(cmd.equals("lynch") || cmd.equals("vote")) {
			// Used for lynching a player
			if (args != null && args.length > 1)
                lynch(args[1], nick, user, host);
		} else if(cmd.equals("retract")) {
			// Retracts your vote
			lynch(null, nick, user, host);
		} else if(cmd.equals("leave") || cmd.equals("quit")) {
			// Leaves the game
			leave(nick, user, host);
		} else if(cmd.equals("start")) {
			// Starts the game
			startgame(nick, user, host, false);
		} else if(cmd.equals("wait")) {
			// Starts the game
			waitstart(nick, user, host);
		} else if(cmd.equals("kill")) {
			// Used by wolves to kill players
			if (args != null && args.length > 1)
                kill(args[1], nick, user, host);
		} else if(cmd.equals("see")) {
			// Used by seers to see players
			if (args != null && args.length > 1)
                see(args[1], nick, user, host);
		} else if(cmd.equals("visit")) {
			// Used by harlots to visit players
			if (args != null && args.length > 1)
                visit(args[1], nick, user, host);
		} else if(cmd.equals("shoot")) {
			// Used by gunners to shoot players
			if (args != null && args.length > 1)
                shoot(args[1], nick, user, host);
		} else if(cmd.equals("id")) {
			// Used by detectives to id players
			if (args != null && args.length > 1)
                id(args[1], nick, user, host);
		} else if(cmd.equals("observe")) {
			// Used by werecrows to observe players
			if (args != null && args.length > 1)
                observe(args[1], nick, user, host);
		} else if(cmd.equals("guard")) {
			// Used by guardian angels to guard players
			if (args != null && args.length > 1)
                guard(args[1], nick, user, host);
		} else if(cmd.compareTo("raise") == 0) {
			// Used by mediums to raise players
			if (args != null && args.length > 1)
                raise(args[1], nick, user, host);
		} else if(cmd.compareTo("curse") == 0) {
			// Used by sorcerers to curse players
			if (args != null && args.length > 1)
                curse(args[1], nick, user, host);
		} else if(cmd.equals("invite")) {
			// Used by drunks to invite players to the tavern
			if (args != null && args.length > 1)
                tavernInvite(args[1], nick, user, host);
		} else if(cmd.equals("eject")) {
			// Used by drunks to kick players from the tavern
			if (args != null && args.length > 1)
                tavernKick(args[1], nick, user, host);
		} else if(cmd.equals("myrole")) {
			// PMs you your role
			myrole(nick, user, host);
		} else if(cmd.equals("fstart")) {
			// Mod command: Forces the game to start.
			if(admincheck(host)) {
				startgame(nick, user, host, true);
			} else {
				privmsg(nick, "You are not a moderator.");
			}
		} else if(cmd.compareTo("fendgame") == 0) {
			// Mod command: Forces the game to end.
			if(admincheck(host)) {
				fendgame(nick, user, host);
			} else {
				privmsg(nick, "You are not a moderator.");
			}
		} else if(cmd.equals("set")) {
			// Mod command: Sets a variable
			if(args != null && args.length > 2){
                if(admincheck(host)) {
                    setvar(args[1], args[2], nick, user, host);
                } else {
                    privmsg(nick, "You are not a moderator.");
                }
            }
		} else if(cmd.equals("fquit")) {
			// Admin command: Shuts down the bot.
			if(admincheck(host)) {
				fquit(nick, user, host);
			} else {
				privmsg(nick, "You are not an admin.");
			}
		} else if(cmd.equals("fleave")) {
			// Mod command: Forces a player to leave.
			if (args != null && args.length > 1){
                if(admincheck(host)) {
                    fleave(args[1], nick, user, host);
                } else {
                    privmsg(nick, "You are not a moderator.");
                }
            }
		} else if(cmd.equals("fjoin")) {
			// Mod command: Forces a player to join.
			if (args != null && args.length > 1){
                if(admincheck(host)) {
                    fjoin(args[1], nick, user, host);
                } else {
                    privmsg(nick, "You are not a moderator.");
                }
            }
		} else if(cmd.equals("op")) {
			// Admin command: Ops you.
			if(admincheck(host)) {
				op(nick, user, host);
			} else {
				privmsg(nick, "You are not an admin.");
			}
		} else if(cmd.equals("deop")) {
			// Admin command: Deops a player.
			if (args != null && args.length > 1){
                if(admincheck(host)) {
                    deop(args[1], nick, user, host);
                } else {
                    privmsg(nick, "You are not an admin.");
                }
            }
		} else if(cmd.equals("cmdchar")) {
			// Admin command: Changes command character prefix.
			if (args != null && args.length > 1){
                if(admincheck(host)) {
                    change_cmdchar(args[1], nick, user, host);
                } else {
                    privmsg(nick, "You are not an admin.");
                }
            }
		} else if(cmd.equals("ignore")) {
			// Admin command: Ignores a hostmask.
			if (args != null && args.length > 1){
                if(admincheck(host)) {
                    ignoreHost(args[1], nick, user, host);
                } else {
                    privmsg(nick, "You are not an admin.");
                }
            }
		} else if(cmd.equals("unignore")) {
			// Admin command: Unignores a hostmask.
			if (args != null && args.length > 1){
                if(admincheck(host)) {
                    unignoreHost(args[1], nick, user, host);
                } else {
                    privmsg(nick, "You are not an admin.");
                }
            }
		} else if(cmd.equals("loadconfig")) {
			// Admin command: Loads a new player configuration.
			if (args != null && args.length > 1){
                if(admincheck(host)) {
                    loadPConfig(args[1]);
                } else {
                    privmsg(nick, "You are not an admin.");
                }
            }
		} else if(cmd.equals("stats")) {
			// Who is left in the game?
			stats(nick, user, host);
		} else {
			// Allows private team chat
			if(args == null) return;
			String s = args[0];
			for (int m = 1; m < args.length; m++) {
				s += " " + args[m]; // concatenate
			}
			teammsg(s, nick, user, host);
		}
	}
	
	/**
	 * Sets a variable.
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void setvar(String var, String val, String nick, String user, String host) {
		var = var.toLowerCase();
		if(var.equals("allowcursedseer")) {
			allowCursedSeer = Boolean.parseBoolean(val);
		} else if(var.equals("gunnermisspct")) {
			try {
				gunnermisspct = Double.parseDouble(val);
			} catch(NumberFormatException e) {
				System.err.println("[CONSOLE] : Could not parse \"" + val + "\"!");
			}
		}
	}

	/**
	 * Increases wait time before starting the game.
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void waitstart(String nick, String user, String host) {
		// Is game running?
		if(isRunning) {
			privmsg(nick, "The game is already running.");
			return;
		}
		// Is the user playing?
		WolfPlayer p = getPlayer(nick, user, host);
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Enough !waits yet?
		if(nWaitTimes >= maxWaitTimes) {
			chanmsg("The wait limit has already been reached.");
			return;
		}
		// increase the wait time
		chanmsg(formatBold(nick) + " has increased the wait time by " + (waitTime / 1000) + " seconds.");
		long curTime = System.currentTimeMillis();
		if(gameAllowStart < curTime) gameAllowStart = curTime + waitTime;
		else gameAllowStart += waitTime;
		nWaitTimes++;
	}

	/**
	 * PMs you your role
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void myrole(String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the MYROLE command");
		// Is the player even playing?
		WolfPlayer p = getPlayer(nick, user, host);
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// PM the player his role.
		String wolflist = "Players: ";
		String playerlist = "Players: ";
        WolfPlayer temp;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			// Update wolf list
			if(temp.roles[WolfPlayer.ROLE_WOLF]) wolflist += temp.getNickBold() + " (wolf)";
			else if(temp.roles[WolfPlayer.ROLE_TRAITOR]) wolflist += temp.getNickBold() + " (traitor)";
			else if(temp.roles[WolfPlayer.ROLE_WERECROW]) wolflist += temp.getNickBold() + " (werecrow)";
			else if(temp.roles[WolfPlayer.ROLE_SORCERER]) wolflist += temp.getNickBold() + " (sorcerer)";
			else wolflist += temp.getNickBold();
			// Update player list
			playerlist += temp.getNickBold();
			if(m < (getNumPlayers() - 2)) {
				// add a comma
				wolflist = wolflist + ", ";
				playerlist = playerlist + ", ";
			} else if(m == (getNumPlayers() - 2)) {
				// second-to-last player added, add an "and"
				wolflist = wolflist + ", and ";
				playerlist = playerlist + ", and ";
			}
		}
		sendPlayerRole(p, playerlist, wolflist);
	}

	/**
	 * Curses a villager
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void curse(String who, String nick, String user, String host) {
		if(who == null) return; // Sanity check
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the CURSE command");
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Get player and target
		WolfPlayer p = getPlayer(nick, user, host);
		WolfPlayer target = getPlayer(who);
        // Is the player even playing?
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a sorcerer?
		if(!p.roles[WolfPlayer.ROLE_SORCERER]) {
			privmsg(nick, "Only sorcerers can curse other players.");
			return;
		}
		// Is it night?
		if(!isNight) {
			privmsg(nick, "You may only curse people at night.");
			return;
		}
		// Has the player not yet cursed someone?
		if(p.cursed != null) {
			privmsg(nick, "You have already cursed someone this night.");
			return;
		}
		
		// Is the target playing?
		if(target == null) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!target.isAlive) {
			privmsg(nick, target.getNick() + " is already dead.");
			return;
		}
		// Is the target a wolf?
		if(target.roles[WolfPlayer.ROLE_WOLF] || target.roles[WolfPlayer.ROLE_WERECROW]) {
			privmsg(nick, "You don't see the point in cursing your wolf friends.");
			return;
		}
		// the curse falls
		p.cursed = target;
		target.roles[WolfPlayer.ROLE_CURSED] = true;
		// let you know
		privmsg(nick, "Your fingers move nimbly as you cast the dark enchantment. \u0002" +
			target.getNick() + "\u0002 has become cursed!");
		// chance of the target knowing his cursed status
		Random rand = new Random();
		if(rand.nextDouble() < sorcerervictimnoticecursepct) {
			privmsg(target.getNick(), "You feel the mark of Cain fall upon you....");
		}
		
		// checks for the end of the night
		if(checkEndNight())
            endNight();
	}
	
	/**
	 * Used by mediums to raise dead players.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void raise(String who, String nick, String user, String host) {
		if(who == null) return; // Sanity check
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Get player and target
		WolfPlayer p = getPlayer(nick, user, host);
		WolfPlayer target = getPlayer(who);
        // Is the player even playing?
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a medium?
		if(!p.roles[WolfPlayer.ROLE_MEDIUM]) {
			privmsg(nick, "Only mediums can raise other players.");
			return;
		}
		// Is it day?
		if(isNight) {
			privmsg(nick, "You may only raise players during the day.");
			return;
		}
		// Has the player not yet raised anyone?
		if(p.raised != null) {
			privmsg(nick, "You have already raised someone today.");
			return;
		}
		
		// Is the target playing?
		if(target == null) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target dead? 
		if(target.isAlive) {
			privmsg(nick, target.getNick() + " is still alive and can be consulted normally.");
			return;
		}
		// raise him
		chanmsg(formatBold(p.getNick()) + " has cast a seance! The spirit of " +
			formatBold(target.getNick()) + " is raised for the day.");
		voice(target.getNick()); // Make him able to chat again.
		p.raised = target;
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
	}
	
	/**
	 * Guards a player from attacks by the wolves.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void guard(String who, String nick, String user, String host) {
		if(who == null) return; // Sanity check
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the GUARD command");
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Is the player even playing?
		WolfPlayer p = getPlayer(nick, user, host);
        WolfPlayer target = getPlayer(who);
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a guardian angel?
		if(!p.roles[WolfPlayer.ROLE_ANGEL]) {
			privmsg(nick, "Only guardian angels can guard other players.");
			return;
		}
		// Is it night?
		if(!isNight) {
			privmsg(nick, "You may only guard during the night.");
			return;
		}
		// Has the player not yet guarded?
		if(p.guarded != null) {
			privmsg(nick, "You are already guarding " + p.guarded.getNickBold() + " tonight.");
			return;
		}
		
		// Is the target playing?
		if(target == null) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!target.isAlive) {
			privmsg(nick, target.getNick() + " is already dead.");
			return;
		}
		// Guards the target.
		privmsg(nick, "You are guarding " + formatBold(target.getNick()) + " tonight. Farewell.");
		privmsg(target.getNick(), "You can sleep well tonight, for a guardian angel is protecting you.");
		p.guarded = target;
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
	}
	
	/**
	 * Observes a player to determine whether or not he leaves the house.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void observe(String who, String nick, String user, String host) {
		if(who == null) return; // Sanity check
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the OBSERVE command");
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Get player and target
		WolfPlayer p = getPlayer(nick, user, host);
        WolfPlayer target = getPlayer(who);
        // Is the player even playing?
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
        // Is the player a werecrow?
		} else if(!p.roles[WolfPlayer.ROLE_WERECROW]) {
			privmsg(nick, "Only werecrows can observe other players.");
        // Is it night?
		} else if(!isNight) {
			privmsg(nick, "You may only observe during the night.");
        // Has the player not yet observed?
		} else if(p.observed != null) {
			privmsg(nick, "You are already observing " + p.observed.getNickBold() + " tonight.");
		// Is the target playing?
        } else if(target == null) {
			privmsg(nick, who + " is not playing.");
        // Is the target alive? 
		} else if(!target.isAlive) {
			privmsg(nick, target.getNick() + " is already dead.");
		// Observe the targetted player.
        } else {
            privmsg(nick, "You change into a large black crow and fly off to see whether " + 
                    formatBold(target.getNick()) + " remains in bed all night.");
            p.observed = target;

            // checks for the end of the night
            if(checkEndNight()) endNight();
        }
	}
	
	/**
	 * Identifies a player's role.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void id(String who, String nick, String user, String host) {
		if(who == null) return; // Sanity check
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the ID command");
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
        // Get player and target
		WolfPlayer p = getPlayer(nick, user, host);
        WolfPlayer target = getPlayer(who);
		// Is the player even playing?
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a detective?
		if(!p.roles[WolfPlayer.ROLE_DETECTIVE]) {
			privmsg(nick, "Only detectives can id other players.");
		}
		// Is it day?
		if(isNight) {
			privmsg(nick, "You may only identify players during the day.");
			return;
		}
		// Has the player not yet ided anyone?
		if(p.ided != null) {
			privmsg(nick, "You have already identified someone today.");
			return;
		}
		// Is the target playing?
		if(target == null) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!target.isAlive) {
			privmsg(nick, target.getNick() + " is already dead.");
			return;
		}
		// id his role
		privmsg(nick, "The results of your investigation return: " + target.getNickBold() +
			" is a " + target.getIDedRole() + "!");
		p.ided = target;
		// drop papers?
		Random rand = new Random();
		if(rand.nextDouble() < detectivefumblepct) {
			// notify the wolves of the detective
			wolfmsg(formatBold(p.getNick()) + " drops a paper revealing s/he is a \u0002detective\u0002!");
		}
	}
	
	/**
	 * Shoots a player.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void shoot(String who, String nick, String user, String host) {
		if(who == null) return; // Sanity check
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the SHOOT command");
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Is the player even playing?
		WolfPlayer p = getPlayer(nick, user, host);
        WolfPlayer target = getPlayer(who);
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a seer?
		if(!p.roles[WolfPlayer.ROLE_GUNNER]) {
			privmsg(nick, "You don't have a gun.");
			return;
		}
		// Is it day?
		if(isNight) {
			privmsg(nick, "You can only shoot during the day.");
			return;
		}
		
		// Is the target playing?
		if(target == null) {
			privmsg(nick, who + " is not playing.");
			return;
		} else if(target == p) {
			// lol, don't suicide
			privmsg(nick, "You're holding it the wrong way!");
			return;
		}
		// Is the target alive? 
		if(!target.isAlive) {
			privmsg(nick, target.getNick() + " is already dead.");
			return;
		}
		// Do you have any bullets left?
		if(p.numBullets == 0) {
			privmsg(nick, "You have no more bullets remaining.");
			return;
		}
		
		// He can now fire.
		chanmsg(p.getNickBold() + " raises his/her gun and fires at " +
			target.getNickBold() + "!");
		p.numBullets--; // Uses a bullet.
		// Does he explode?
		Random rand = new Random();
		if(rand.nextDouble() < gunnerexplodepct) {
			// Boom! You lose.
			chanmsg(p.getNickBold() + " should have cleaned his/her gun better. " +
				"The gun explodes and kills him/her!");
			chanmsg("It appears s/he was a \u0002" + p.getDeathDisplayedRole() + "\u0002.");
			playerDeath(p);
			return;
		}
		
		// Wolf teammates will deliberately miss other wolves.
		if((p.countWolfRoles() > 0) &&
				(target.roles[WolfPlayer.ROLE_WOLF] || target.roles[WolfPlayer.ROLE_WERECROW])) {
			chanmsg(p.getNickBold() + " is a lousy shooter! S/he missed!");
			return;
		}
		// Missed target? (Drunks have 3x the miss chance.)
		if(rand.nextDouble() < (p.roles[WolfPlayer.ROLE_DRUNK] ? gunnermisspct*3 : gunnermisspct)) {
			chanmsg(p.getNickBold() + " is a lousy shooter! S/he missed!");
			return;
		}
		// We hit the target.
		// Is the shot person a wolf?
		if(target.roles[WolfPlayer.ROLE_WOLF] || target.roles[WolfPlayer.ROLE_WERECROW]) {
			chanmsg(target.getNickBold() + " is a " + 
				target.getDeathDisplayedRole() + " and is dying from the silver bullet!");
			playerDeath(target);
		} else {
			// Was it a headshot?
			if(rand.nextDouble() < gunnerheadshotpct) {
				chanmsg(target.getNickBold() + " was not a wolf but was accidentally " +
					"fatally injured! It appears that s/he was a " + target.getDeathDisplayedRole() + ".");
				playerDeath(target);
			} else {
				// Injured a villager, but did not kill him.
				target.canVote = false;
				chanmsg(target.getNickBold() + " was a villager and is injured by the " +
					"silver bullet. S/he will be resting in bed for the rest of the day but will recover fully.");
			}
		}
		// Does the game end as a result of the shooting?
		if(checkForEnding()) return;
		else checkForLynching(); // Does a lynching occur now?
	}
	
	/**
	 * Prints out the current stats of the game.
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void stats(String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the STATS command");
		
		// Is the game even running? If not, just give player count and return.
		if(!isRunning) {
			chanmsg("There are " + formatBold(getNumPlayers()+"") + " players waiting to begin the game.");
			return;
		}
		// Is the player even playing?
		WolfPlayer p = getPlayer(nick, user, host);
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player alive?
		if(!p.isAlive) {
			privmsg(nick, "You have died already and thus cannot use the stats command.");
			return;
		}
		// List all the players.
		String plist = "Players: ";
		int nLivingPlayers = countLivingPlayers();
		// count them
		int count = 0;
        WolfPlayer temp;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			if(temp.isAlive) {
				count++;
				if(count-nLivingPlayers <= -2) plist += " " + temp.getNickBold() + ", ";
				else if(count-nLivingPlayers == -1) plist += " " + temp.getNickBold() + ", and ";
				else if(count-nLivingPlayers == 0) plist += " " + temp.getNickBold() + ".";
			}
		}
		// send to channel
		chanmsg(plist);
		
		// counts
		int seercount = 0;
		int drunkcount = 0;
		int angelcount = 0;
		int harlotcount = 0;
		int detectivecount = 0;
		int gunnercount = 0;
		int wolfcount = 0;
		int traitorcount = 0;
		int werecrowcount = 0;
		int sorcerercount = 0;
		int livingcount = 0; // total living players
		// count them
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			if(temp.isAlive) {
				if(temp.roles[WolfPlayer.ROLE_SEER]) seercount++;
				if(temp.roles[WolfPlayer.ROLE_DRUNK]) drunkcount++;
				if(temp.roles[WolfPlayer.ROLE_ANGEL]) angelcount++;
				if(temp.roles[WolfPlayer.ROLE_HARLOT]) harlotcount++;
				if(temp.roles[WolfPlayer.ROLE_DETECTIVE]) detectivecount++;
				if(temp.roles[WolfPlayer.ROLE_GUNNER]) gunnercount++;
				if(temp.roles[WolfPlayer.ROLE_WOLF]) wolfcount++;
				if(temp.roles[WolfPlayer.ROLE_TRAITOR]) traitorcount++;
				if(temp.roles[WolfPlayer.ROLE_WERECROW]) werecrowcount++;
				if(temp.roles[WolfPlayer.ROLE_SORCERER]) sorcerercount++;
				livingcount++;
			}
		}
		// display results
		String str = "There are " + formatBold(livingcount + " villagers") + " remaining.";
		// wolves
		if(wolfcount == 1) str = str + " There is \u0002a wolf\u0002.";
		else if(wolfcount > 1) str = str + " There are \u0002" + wolfcount + " wolves\u0002.";
		if(traitorcount == 1) str = str + " There is \u0002a traitor\u0002.";
		else if(traitorcount > 1) str = str + " There are \u0002" + traitorcount + " traitors\u0002.";
		if(werecrowcount == 1) str = str + " There is \u0002a werecrow\u0002.";
		else if(werecrowcount > 1) str = str + " There are \u0002" + werecrowcount + " werecrows\u0002.";
		if(sorcerercount == 1) str = str + " There is \u0002a sorcerer\u0002.";
		else if(sorcerercount > 1) str = str + " There are \u0002" + sorcerercount + " sorcerers\u0002.";
		// villagers
		if(seercount == 1) str = str + " There is \u0002a seer\u0002.";
		else if(seercount > 1) str = str + " There are \u0002" + seercount + " seers\u0002.";
		if(drunkcount == 1) str = str + " There is \u0002a village drunk\u0002.";
		else if(drunkcount > 1) str = str + " There are \u0002" + drunkcount + " village drunks\u0002.";
		if(harlotcount == 1) str = str + " There is \u0002a  harlot\u0002.";
		else if(harlotcount > 1) str = str + " There are \u0002" + harlotcount + " harlots\u0002.";
		if(angelcount == 1) str = str + " There is \u0002a guardian angel\u0002.";
		else if(angelcount > 1) str = str + " There are \u0002" + angelcount + " guardian angels\u0002.";
		if(detectivecount == 1) str = str + " There is \u0002a detective\u0002.";
		else if(detectivecount > 1) str = str + " There are \u0002" + detectivecount + " detectives\u0002.";
		if(gunnercount == 1) str = str + " There is \u0002a gunner\u0002.";
		else if(gunnercount > 1) str = str + " There are \u0002" + gunnercount + " gunners\u0002.";
		// send to channel
		chanmsg(str);
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
	}
	
	/**
	 * Lists all the players.
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	/*private void playerlist(String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the STATS command");
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the game even running? If not, just give player count and return.
		if(!isRunning) {
			chanmsg("There are \u0002" + playernum + "\u0002 unknown players in the game right now.");
			return;
		}
		// Is the player alive?
		if(!players[plidx].isAlive) {
			privmsg(nick, "You have died already and thus cannot use the stats command.");
			return;
		}
		// counts
		String str = "Active players |";
		// count them
		int m = 0;
		while(m < playernum) {
			if(players[m].isAlive) {
				str = str + " \u0002" + players[m].getNick() + "\u0002 |";
			}
			m++;
		}
		// send to channel
		chanmsg(str);
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
	}*/
	
	/**
	 * Visits a player
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void visit(String who, String nick, String user, String host) {
		if(who == null) return; // Sanity check
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the VISIT command");
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// get player and target
		WolfPlayer p = getPlayer(nick, user, host);
		WolfPlayer target = getPlayer(who);
        // Is the player even playing?
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
        // Is the player a harlot?
        } else if(!p.roles[WolfPlayer.ROLE_HARLOT]) {
			privmsg(nick, "Only harlots can visit other players.");
        // Is it night?
        } else if(!isNight) {
			privmsg(nick, "You may only visit other players during the night.");
        // Has the player not yet visited?
        } else if(p.visited != null) {
			privmsg(nick, "You have already visited " + p.visited.getNick() + " tonight.");
        // Is the target playing?
        } else if(target == null) {
			privmsg(nick, who + " is not playing.");
        // Is the target alive?
        } else if(!target.isAlive) { 
			privmsg(nick, "Eww! " + target.getNick() + " is already dead.");
        // Visiting yourself?
        } else if(target == p) {
			// Notify harlot
			privmsg(nick, "You decide to stay home for the night.");
			p.visited = p;
		// Notify both players
        } else {
			p.visited = target;
			privmsg(p.getNick(), "You are spending the night with " + 
                    target.getNickBold() + ". Have a good time!");
			privmsg(target.getNick(), p.getNickBold() +
                    ", a \u0002harlot\u0002, has come to spend the night with you. Have a good time!");
            
            // checks for the end of the night
            if(checkEndNight()) endNight();
		}
	}
	
	/**
	 * Sees the player.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void see(String who, String nick, String user, String host) {
		if(who == null) return; // Sanity check
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the SEE command");
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// get player and target
		WolfPlayer p = getPlayer(nick, user, host);
		WolfPlayer target = getPlayer(who);
        // Is the player even playing?
		if (p == null) {
			privmsg(nick, "You aren't even playing.");
		// Is the player a seer?
        } else if(!p.roles[WolfPlayer.ROLE_SEER]) {
			privmsg(nick, "Only seers can see other players.");
		// Is it night?
        } else if(!isNight) {
			privmsg(nick, "Visions may only be had during the night.");
		// Has the player not yet seen?
        } else if(p.seen != null) {
			privmsg(nick, "You have already had a vision this night.");
		// Is the target playing?
        } else if(target == null) {
			privmsg(nick, who + " is not playing.");
		// Is the target alive? 
		} else if(!target.isAlive) {
			privmsg(nick, target.getNick() + " is already dead.");
		// PM the vision
        } else {
            privmsg(nick, "You have a vision; in this vision you see that " + formatBold(target.getNick()) +
			" is a " + target.getSeenRole() + "!");
            p.seen = target;

            // checks for the end of the night
            if(checkEndNight()) endNight();
        }
	}
	
	/**
	 * Ejects a player from the tavern.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void tavernKick(String who, String nick, String user, String host) {
		if(who == null) return; // Sanity check
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the SEE command");
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Is the player even playing?
		WolfPlayer p = getPlayer(nick, user, host);
		WolfPlayer target = getPlayer(who);
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a drunk?
		if(!p.roles[WolfPlayer.ROLE_DRUNK]) {
			privmsg(nick, "Only drunks can eject other players.");
			return;
		}
		// Is the target playing?
		if(target == null) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!target.isAlive) {
			privmsg(nick, target.getNick() + " is already dead.");
			return;
		}
		// Notifies tavern
		if(tavernChannel == null) {
			tavernmsg(target.getNickBold() + " has been thrown out of the tavern.");
		} else {
			kickplayer(tavernChannel, target.getNick(), "You were thrown out of the tavern");
		}
		target.isInTavern = false;
	}
	
	/**
	 * Invites a player to join the village tavern.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void tavernInvite(String who, String nick, String user, String host) {
		if(who == null) return; // Sanity check
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the SEE command");
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Is the player even playing?
		WolfPlayer p = getPlayer(nick, user, host);
        // get target
		WolfPlayer target = getPlayer(who);
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a seer?
		if(!p.roles[WolfPlayer.ROLE_DRUNK]) {
			privmsg(nick, "Only drunks can invite other players.");
			return;
		}
		
		// Is the target playing?
		if(target == null) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!target.isAlive) {
			privmsg(nick, target.getNick() + " is already dead.");
			return;
		}
		if(tavernChannel == null) {
			// Notifies tavern
			tavernmsg(target.getNickBold() + " has entered the tavern.");
			// Brings the player into the tavern.
			privmsg(target.getNick(), target.getNickBold() + ", a \u0002village drunk\u0002, has brought you into the village tavern. " +
				"If you PM me, your messages will go to all other users in the tavern.");
			// Lets wolves know how to speak in tavern. They have to prefix their text with "t:" so their messages don't go via the wolf pm.
			if(target.countWolfRoles() > 0) {
				privmsg(target.getNick(), "Because you are a wolf, you will need to prefix your chat with \"T:\" to talk in the tavern. " +
					"This is to keep your messages to the wolves from accidentally going to the tavern and revealing you as a wolf.");
			}
		} else {
			// Send invite
			Javawolf.wolfbot.sendInvite(target.getNick(), tavernChannel);
		}
		target.isInTavern = true;
	}
	
	/**
	 * Kills the specified player
	 * 
	 * @param who target
	 * @param nick issuer
	 * @param user
	 * @param host
	 */
	private void kill(String who, String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the KILL command");
		if(who == null) return; // Sanity check
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Is the player even playing?
		WolfPlayer p = getPlayer(nick, user, host);
        // gets the target
		WolfPlayer target = getPlayer(who);
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player alive?
		if(!p.isAlive) {
			privmsg(nick, "Dead players aren't going to be killing people anytime soon.");
			return;
		}
		// Is the player a wolf?
		if(!p.roles[WolfPlayer.ROLE_WOLF] && !p.roles[WolfPlayer.ROLE_WERECROW]) {
			privmsg(nick, "Only wolves can kill other players.");
			return;
		}
		// Is it night?
		if(!isNight) {
			privmsg(nick, "Killing may only be done during the night.");
			return;
		}
		
		// Is the target even playing?
		if(target == null) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!target.isAlive) {
			privmsg(nick, target.getNick() + " is already dead.");
			return;
		}
		// Did you target yourself?
		if(p == target) {
			privmsg(nick, "Suicide is bad. Don't do it.");
			return;
		}
		// Is the target a wolf?
		if(target.roles[WolfPlayer.ROLE_WOLF] || target.roles[WolfPlayer.ROLE_WERECROW]) {
			privmsg(nick, "You may not target other wolves.");
			return;
		}
		// Add your vote.
		target.votes++;
		if (p.voted != null) p.voted.votes--;
		p.voted = target;
		// tells the wolves
		wolfmsg(formatBold(p.getNick()) + " has selected \u0002" +
			target.getNick() + "\u0002 to be killed.");
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
	}
	
	/**
	 * Lynches the specified player
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void lynch(String who, String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the LYNCH / RETRACT command");
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Game is running, is it night?
		if(isNight) {
			privmsg(nick, "You can only lynch during the day.");
			return;
		}
		
		// gets the voter
		WolfPlayer p = getPlayer(nick, user, host);
        // gets the votee
		WolfPlayer target = getPlayer(who);
		// Is the voter playing?
		if(p == null) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Dead people may not vote
		if(!p.isAlive) {
			privmsg(nick, "Dead players may not vote.");
			return;
		}
		// Was this a retraction?
		if(who == null) {
			if(p.voted != null) {
				// retracts the vote
                p.voted.votes--;
				p.voted = null;
				chanmsg(formatBold(nick) + " has retracted his/her vote.");
			} else {
				// nobody was voted for
				privmsg(nick, "You haven't voted for anybody.");
			}
			return;
		}
		// Is the target playing?
		if(target == null) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// You also cannot vote for dead people
		if(!target.isAlive) {
			privmsg(nick, "He's already dead! Leave the body in its grave.");
			return;
		}
		// removes the old vote
		if(p.voted != null) {
			// retracts the vote
			p.voted.votes--;
		}
		// adds the new vote
		p.voted = target;
		target.votes++;
		chanmsg(formatBold(nick) + " has voted to lynch " + target.getNickBold() + "!");
		
		// Checks to see if a lynching occurs.
		checkForLynching();
	}
	
	/**
	 * Joins the game
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void join(String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the JOIN command");
		// Is the game already in progress?
		if(isRunning) {
			privmsg(nick, "The game is already in progress. Please wait patiently for it to end.");
			return;
		}
		
		// Joins the game.
		if(addPlayer(nick, user, host)) {
			chanmsg(formatBold(nick) + " has joined the game.");
			voice(nick);
		} else {
			privmsg(nick, "You cannot join the game at this time.");
		}
	}
	
	/**
	 * Leaves the game
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void leave(String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the LEAVE command");
		// get who this is
		WolfPlayer p = getPlayer(nick, user, host);
		if(p == null) {
			// not even in the game...
			privmsg(nick, "You aren't playing.");
			return;
		}
		// Is the game already in progress?
		if(isRunning) {
			if(p.isAlive) {
				p.isAlive = false;
				// kill him
				chanmsg(formatBold(nick) + " ate toxic berries and died. It appears s/he was a " +
					formatBold(p.getDeathDisplayedRole()) + "");
				playerDeath(p);
				// Does game end?
				if(checkForEnding()) return;
				if(isNight) {
					if(checkEndNight()) endNight(); // Does night end?
				} else {
					checkForLynching(); // Does day end?
				}
			} else {
				privmsg(nick, "You are already dead.");
			}
		} else {
			if(rmvPlayer(nick, user, host)) {
				chanmsg(formatBold(nick) + " left the game.");
				devoice(nick);
			} else {
				privmsg(nick, "You cannot leave the game at this time.");
			}
		}
	}
	
	/**
	 * Starts the game.
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 * @param forced
	 */
	private void startgame(String nick, String user, String host, boolean forced) {
		// Has the game already begun?
		if(isRunning) {
			privmsg(nick, "The game has already begun.");
			return;
		}
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the START command");
		// Is the starter even in the game?
		WolfPlayer p = getPlayer(nick, user, host);
		if(p == null) {
			// not even in the game...
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Waited long enough?
		long gamestart = System.currentTimeMillis();
		if((gamestart < gameAllowStart) && !forced) {
			long secondsleft = (gameAllowStart - gamestart) / 1000;
			chanmsg("Please wait another " + secondsleft + " seconds to begin.");
			return;
		}
		// 4+ people?
		if(getNumPlayers() < 4) {
			chanmsg("You need at least four players to begin.");
			return;
		}

		System.out.println("[CONSOLE] : Getting config....");
		// Gets the configuration
		WolfConfig wc = getConfig(getNumPlayers());
		if(wc == null) {
			chanmsg("Configuration error: " + getNumPlayers() + " players is not supported.");
			System.err.println("[CONSOLE] : " + getNumPlayers() + " is an unsupported player count.");
			return;
		}

		// game started; begin during night
		isRunning = true;
		isNight = true;
		
		// Welcome the players to the game & sets them alive.
		System.out.println("[CONSOLE] : Welcoming players....");
		String namelist = "";
        WolfPlayer temp;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			temp.isAlive = true;
			temp.addAction(gamestart); // set them all to having spoken now
			namelist = namelist + formatBold(temp.getNick());
			if(m < getNumPlayers() - 2) namelist = namelist + ", ";
			else if(m == getNumPlayers() - 2) namelist = namelist + ", and ";
		}
		chanmsg(namelist + ". Welcome to wolfgame as hosted by javawolf, a java implementation of the party game Mafia.");
		
		// Assign the roles
		System.out.println("[CONSOLE] : Assigning roles....");
		// Set up the number of each
		int wolfcount = wc.wolfcount;
		int traitorcount = wc.traitorcount;
		int werecrowcount = wc.werecrowcount;
		int sorcerercount = wc.sorcerercount;
		int seercount = wc.seercount;
		int harlotcount = wc.harlotcount;
		int drunkcount = wc.drunkcount;
		int angelcount = wc.angelcount;
		int detectivecount = wc.detectivecount;
		int mediumcount = wc.mediumcount;
		int cursedcount = wc.cursedcount;
		int gunnercount = wc.gunnercount;
		int lovercount = wc.loverpaircount;
		// randomly pick players to assign the wolf roles
		Random rand = new Random();
		int pidx = 0;
		while(wolfcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countMainRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_WOLF] = true;
				// If we have a dedicated wolf channel, invite the player.
				if(wolfChannel != null) Javawolf.wolfbot.sendInvite(temp.getNick(), wolfChannel);
				wolfcount--;
			}
		}
		while(traitorcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countMainRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_TRAITOR] = true;
				// If we have a dedicated wolf channel, invite the player.
				if(wolfChannel != null) Javawolf.wolfbot.sendInvite(temp.getNick(), wolfChannel);
				traitorcount--;
			}
		}
		while(werecrowcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countMainRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_WERECROW] = true;
				// If we have a dedicated wolf channel, invite the player.
				if(wolfChannel != null) Javawolf.wolfbot.sendInvite(temp.getNick(), wolfChannel);
				werecrowcount--;
			}
		}
		while(sorcerercount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countMainRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_SORCERER] = true;
				// If we have a dedicated wolf channel, invite the player.
				if(wolfChannel != null) Javawolf.wolfbot.sendInvite(temp.getNick(), wolfChannel);
				sorcerercount--;
			}
		}
		// now assign the main roles
		while(seercount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countMainRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_SEER] = true;
				seercount--;
			}
		}
		while(drunkcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countMainRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_DRUNK] = true;
				temp.isInTavern = true;
				if(tavernChannel != null) Javawolf.wolfbot.sendInvite(temp.getNick(), tavernChannel);
				drunkcount--;
			}
		}
		while(harlotcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countMainRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_HARLOT] = true;
				harlotcount--;
			}
		}
		while(angelcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countMainRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_ANGEL] = true;
				angelcount--;
			}
		}
		while(detectivecount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countMainRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_DETECTIVE] = true;
				detectivecount--;
			}
		}
		while(mediumcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countMainRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_MEDIUM] = true;
				mediumcount--;
			}
		}
		// now the secondary roles
		while(cursedcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(!temp.roles[WolfPlayer.ROLE_DRUNK] && !temp.roles[WolfPlayer.ROLE_WOLF] &&
					!temp.roles[WolfPlayer.ROLE_WERECROW]) {
				if((!allowCursedSeer && !temp.roles[WolfPlayer.ROLE_SEER]) || allowCursedSeer) {
					temp.roles[WolfPlayer.ROLE_CURSED] = true;
					cursedcount--;
				}
			}
		}
		while(gunnercount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			if(temp.countWolfRoles() == 0) {
				temp.roles[WolfPlayer.ROLE_GUNNER] = true;
				gunnercount--;
				temp.numBullets = (int)Math.floor(getNumPlayers() / 9) + 1;
				if(temp.roles[WolfPlayer.ROLE_DRUNK]) temp.numBullets *= 3; // drunk gets 3x normal bullet count
			}
		}
		// now for lovers
        WolfPlayer temp2;
		while(lovercount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp = getPlayer(pidx);
			int pidx2 = (int)Math.floor(rand.nextDouble()*getNumPlayers());
			while(pidx == pidx2) pidx2 = (int)Math.floor(rand.nextDouble()*getNumPlayers());
            temp2 = getPlayer(pidx);
			if((temp.lover == null) && (temp2.lover == null)) {
				temp.lover = temp2;
				temp2.lover = temp;
				lovercount--;
			}
		}
		// Start watching for idlers to kick.
		idler = new Timer();
		TimerTask idleWatcher = new TimerTask() {
			public void run() {
				checkForIdles();
			}
		};
		idler.schedule(idleWatcher, idleWarnTime);
		// Start the night.
		chanmute();
		startNight();
	}
	
	/**
	 * Checks to see who is idling and kicks them from the game if necessary.
	 */
	private void checkForIdles() {
		// Sanity check.
		if(!isRunning) {
			System.err.println("[CONSOLE] : ERROR : Idler check run when game is off.");
		}
		// retrieve current time
		long currentTime = System.currentTimeMillis();
		// Time until we need to re-run the check.
		long nextCheck = 1000000000;
		// Variables used in calculating next check time.
		long lWarn = -1, lKill = -1;
        WolfPlayer temp;
		// Go through the list of players, checking idle times.
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			// Is he even alive?
			if(!temp.isAlive) {
				continue;
			}
 			// Is he idle?
			if((currentTime - temp.getLastAction()) > idleKillTime) {
				// kill the player
				chanmsg(temp.getNickBold() + " didn't get out of bed for a very long time. " +
					"S/he is declared dead. It appears s/he was a \u0002" + temp.getDeathDisplayedRole() + "\u0002.");
				playerDeath(temp);
			} else if(((currentTime - temp.getLastAction()) > idleWarnTime) && !temp.isIdleWarned) {
				// Warn the player
				chanmsg(temp.getNickBold() + ", you have been idling for awhile. Please say " +
					"something soon or you will be declared dead.");
				temp.isIdleWarned = true;
			}
			// Calculate when this player next needs to be checked.
			lWarn = idleWarnTime - (currentTime - temp.getLastAction());
			lKill = idleKillTime - (currentTime - temp.getLastAction());
			if(lWarn < 0) {
				// Has he not just been killed?
				if(lKill >= 0) {
					if(nextCheck > lKill) nextCheck = lKill;
				}
			} else {
				if(nextCheck > lWarn) nextCheck = lWarn;
			}
		}
		// Check to see if the game ends.
		if(!checkForEnding()) {
			// Does day or night end?
			if(!isNight) checkForLynching();
			else checkEndNight();
			// schedule next task
			TimerTask idleWatcher = new TimerTask() {
				public void run() {
					checkForIdles();
				}
			};
			idler.schedule(idleWatcher, nextCheck + 200);
		}
	}
	
	/**
	 * Checks to see if a lynching occurs
	 */
	private void checkForLynching() {
		// tally the votes
		int[] voteresults = tallyvotes();
		int nVotes = voteresults[0];
		int ind = voteresults[1];
		int votercount = countVoters();
		
		// check to see if a lynching occurs
        WolfPlayer temp = getPlayer(ind);
		if((votercount - nVotes) < nVotes) {
			// Notify players
			chanmsg("Resigned to his/her fate, " + temp.getNickBold() + " is led to the gallows. After death, it is discovered " +
				"that s/he was a " + formatBold(temp.getDeathDisplayedRole()) + ".");
			playerDeath(temp);
			// end the day
			if(!checkForEnding()) endDay();
		}
	}
	
	/**
	 * Checks to see if the night ended.
	 * 
	 * @return
	 */
	private boolean checkEndNight() {
        WolfPlayer temp;
		// Check to see if all roles have acted.
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			// Dead players don't count
			if(temp.isAlive){
                if(temp.roles[WolfPlayer.ROLE_SEER] && (temp.seen == null)) return false; // Seer didn't see
                if(temp.roles[WolfPlayer.ROLE_HARLOT] && (temp.visited == null)) return false; // Harlot didn't visit
                if(temp.roles[WolfPlayer.ROLE_WOLF] && (temp.voted == null)) return false; // Wolf didn't kill
                if(temp.roles[WolfPlayer.ROLE_ANGEL] && (temp.guarded == null)) return false; // Angel hasn't guarded
                if(temp.roles[WolfPlayer.ROLE_WERECROW] && (temp.voted == null)) return false; // Crow hasn't killed
                if(temp.roles[WolfPlayer.ROLE_WERECROW] && (temp.observed == null)) return false; // Crow hasn't observed
                if(temp.roles[WolfPlayer.ROLE_SORCERER] && (temp.cursed == null)) return false; // Sorcerer hasn't cursed
            }
        }
		
		// Everyone has acted. Night ends.
		return true;
	}
	
	/**
	 * Checks to see if the villagers or wolves win.
	 * Returns true if the game ended; false otherwise.
	 * 
	 * @return
	 */
	private boolean checkForEnding() {
		int wolfteamcount = countWolfteam(); // number of wolf teammates
		int wolfcount = countWolves(); // number of wolves (turns traitors into wolves)
		int votingcount = countVoters(); // number of voting players
		WolfPlayer temp;
		// Is it a pair of lovers who win? The pair of lovers will not win independently if they are on the same team.
		if(votingcount == 2) {
			int[] voters = new int[2];
			int count = 0;
			// Find the voters.
			for (int m = 0; m < getNumPlayers(); m++) {
                temp = getPlayer(m);
				if(temp.isAlive && temp.canVote) {
					voters[count] = m;
					count++;
				}
			}
			// Are the voters lovers?
			if(getPlayer(voters[0]).lover == getPlayer(voters[1])) {
				// Are they on the opposite team? If they are on the same team, then they don't win independently of their team.
				if(((getPlayer(voters[0]).countWolfRoles() > 0) && (getPlayer(voters[1]).countWolfRoles() == 0)) ||
					((getPlayer(voters[0]).countWolfRoles() == 0) && (getPlayer(voters[1]).countWolfRoles() > 0))) {
					// Lover pair wins.
					chanmsg("Game over! The pair of lovers, " + getPlayer(voters[0]).getNickBold() + " and " +
						getPlayer(voters[1]).getNickBold() + ", are the last people alive in the village! They disappear into the " +
						"forest together and win!");
					// broadcast roles
					chanmsg(listRoles());
					// game is over
					channelCleanup();
					return true;
				}
			}
		}
		// Are there any wolves left?
		if(wolfteamcount == 0) {
			// Villagers win.
			chanmsg("Game over! All the wolves are dead! The villagers chop them up, barbeque them, and enjoy a hearty meal!");
			// broadcast roles
			chanmsg(listRoles());
			// game is over
			channelCleanup();
			return true;
		}
		// Do the wolves eat the players?
		if((votingcount - wolfteamcount) <= wolfteamcount) {
			// Wolves win.
			chanmsg("Game over! There are the same number of wolves as voting villagers! The wolves eat everyone and win.");
			// broadcast roles
			chanmsg(listRoles());
			// game is over
			channelCleanup();
			return true;
		}
		// Do we need to turn the traitors into wolves?
		// This happens if there are no wolves but still wolf teammates (traitors) left.
		if(wolfcount == 0) {
			for (int m = 0; m < getNumPlayers(); m++) {
                temp = getPlayer(m);
				if(temp.roles[WolfPlayer.ROLE_TRAITOR] || temp.roles[WolfPlayer.ROLE_SORCERER]) {
					// change status
					temp.roles[WolfPlayer.ROLE_WOLF] = true;
					temp.roles[WolfPlayer.ROLE_TRAITOR] = false;
					temp.roles[WolfPlayer.ROLE_SORCERER] = false;
					// Let them know in dramatic style :p
					privmsg(temp.getNick(), "HOOOWWWWWLLLLLLL! You have become...a wolf! It is up to you to avenge your fallen leaders!");
				}
			}
			// Let everyone know
			chanmsg(formatBold("The villagers, in the midst of their rejoicing, are terrified as a loud howl breaks out. The wolves are not gone!"));
		}
		// Game is still running
		return false;
	}
	
	/**
	 * Lists the roles.
	 * 
	 * @return
	 */
	private String listRoles() {
        WolfPlayer temp;
		String msg = "";
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			// append to string
			if(temp.countAllRoles() > 0) 
                msg += temp.getNickBold() + " was a " + temp.getEndGameDisplayedRole() + ". ";
			// Also show the lovers.
			if((temp.lover != null) && (temp.lover != null)) 
                msg += temp.getNickBold() +
				" and " + temp.lover.getNickBold() + " were "+formatBold("lovers")+". ";
		}
		// return it
		return msg;
	}
	
	/**
	 * Counts the number of players left on the wolfteam
	 * 
	 * @return
	 */
	private int countWolfteam() {
		int nWolves = 0;
		WolfPlayer temp;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			// only consider living wolves
			if(temp.isAlive && temp.countWolfRoles() > 0) {
				nWolves++;
			}
		}
		
		return nWolves;
	}
	
	/**
	 * Counts the number of wolves left.
	 * Used for turning traitors into wolves.
	 * 
	 * @return
	 */
	private int countWolves() {
		int nWolves = 0;
		WolfPlayer temp;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			// only consider living wolves
			if(temp.isAlive) {
				if(temp.roles[WolfPlayer.ROLE_WOLF] || temp.roles[WolfPlayer.ROLE_WERECROW]) 
                    nWolves++;
			}
		}
		
		return nWolves;
	}
	
	/**
	 * Counts the number of voting players left.
	 * 
	 * @return
	 */
	private int countVoters() {
		int nVoters = 0;
		WolfPlayer temp;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			// only consider living players
			if(temp.isAlive && temp.canVote) 
                nVoters++;
		}
		
		return nVoters;
	}
	
	/**
	 * Counts the total number of living players left.
	 * 
	 * @return
	 */
	private int countLivingPlayers() {
		int nLivingPlayers = 0;
		
        for (int m = 0; m < getNumPlayers(); m++) {
			// only consider living players
			if(getPlayer(m).isAlive) 
                nLivingPlayers++;
		}
		
		return nLivingPlayers;
	}
	
	/**
	 * Starts the night
	 */
	private void startNight() {
		System.out.println("[CONSOLE] : Starting the night.");
		// Start timing the night
		starttime = System.currentTimeMillis();
		// reset the votes
		resetVotes();
		// PM all the players their roles
		String wolflist = "Players: ";
		String playerlist = "Players: ";
        WolfPlayer temp;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			// Update wolf list
			if(temp.roles[WolfPlayer.ROLE_WOLF]) wolflist += temp.getNickBold() + " (wolf)";
			else if(temp.roles[WolfPlayer.ROLE_TRAITOR]) wolflist += temp.getNickBold() + " (traitor)";
			else if(temp.roles[WolfPlayer.ROLE_WERECROW]) wolflist += temp.getNickBold() + " (werecrow)";
			else if(temp.roles[WolfPlayer.ROLE_SORCERER]) wolflist += temp.getNickBold() + " (sorcerer)";
			else wolflist += temp.getNickBold();
			// Update player list
			playerlist += temp.getNickBold();
			if(m < (getNumPlayers() - 2)) {
				// add a comma
				wolflist = wolflist + ", ";
				playerlist = playerlist + ", ";
			} else if(m == (getNumPlayers() - 2)) {
				// second-to-last player added, add an "and"
				wolflist = wolflist + ", and ";
				playerlist = playerlist + ", and ";
			}
		}
		for (int m = 0; m < getNumPlayers(); m++) {
			// ignore dead people
			if(getPlayer(m).isAlive) {
				sendPlayerRole(getPlayer(m), playerlist, wolflist);
			}
		}
		// Announce the night to the players
		chanmsg("It is now night, and the villagers all retire to their beds. The full moon rises in the east, casting long, eerie " +
			"shadows across the village. A howl breaks out, as the wolves come out to slay someone.");
		chanmsg("Please check for private messages from me. If you have none, simply sit back and wait patiently for morning.");
		// Schedule a warning event.
		timer = new Timer();
		TimerTask task = new TimerTask() {
			public void run() {
				warnNightEnding();
			}
		};
		timer.schedule(task, 1000*90);
	}
	
	/**
	 * PMs the player his role.
	 * 
	 * @param m
	 * @param playerlist
	 * @param wolflist
	 */
	private void sendPlayerRole(WolfPlayer p, String playerlist, String wolflist) {
		// evil roles
		if(p.roles[WolfPlayer.ROLE_WOLF]) {
			privmsg(p.getNick(), "You are a "+formatBold("wolf")+". Use \"" + 
				Javawolf.cmdchar + "kill <name>\" to kill a villager once per night.");
			privmsg(p.getNick(), wolflist);
		}
		if(p.roles[WolfPlayer.ROLE_TRAITOR]) {
			privmsg(p.getNick(), "You are a "+formatBold("traitor")+". You are on the side of " +
				"the wolves, except that you are, for all intents and purposes, a villager. Only detectives can identify you. " +
				"If all the wolves die, you will become a wolf yourself.");
			privmsg(p.getNick(), wolflist);
		}
		if(p.roles[WolfPlayer.ROLE_WERECROW]) {
			privmsg(p.getNick(), "You are a \u0002werecrow\u0002. Use \"" + Javawolf.cmdchar + "kill <name>\" to kill a villager " +
				"once per night. You may also observe a player to see whether s/he stays in bed all night with \"" +
				Javawolf.cmdchar + "observe <name>\".");
			privmsg(p.getNick(), wolflist);
		}
		if(p.roles[WolfPlayer.ROLE_SORCERER]) {
			privmsg(p.getNick(), "You are a \u0002sorcerer\u0002. Use \"" + Javawolf.cmdchar + "curse <name>\" to curse a villager " +
				"each night. The seer will then see the cursed villager as a wolf! If all the wolves die, you will become a wolf yourself.");
			privmsg(p.getNick(), wolflist);
		}
		// good roles
		if(p.roles[WolfPlayer.ROLE_SEER]) { 
			privmsg(p.getNick(), "You are a \u0002seer\u0002. Use \"" + 
				Javawolf.cmdchar + "see <name>\" to see the role of a villager once per night. Be warned that traitors will " +
				"still appear as villagers, and cursed villagers will appear to be wolves. Use your judgment.");
			privmsg(p.getNick(), playerlist);
		}
		if(p.roles[WolfPlayer.ROLE_DRUNK]) {
			privmsg(p.getNick(), "You have been drinking too much! You are a \u0002village drunk\u0002! " +
				"You can bring other players into the village tavern. Use \"" + Javawolf.cmdchar + "invite <name>\" to bring someone in, " +
				"and use \"" + Javawolf.cmdchar + "eject <name>\" to throw someone out of the tavern.");
		}
		if(p.roles[WolfPlayer.ROLE_HARLOT]) {
			privmsg(p.getNick(), "You are a \u0002harlot\u0002. You may visit any player during the night. " +
				"If you visit a wolf or the victim of the wolves, you will die. If you are attacked while you are out visiting, " +
				"you will survive. Use \"" + Javawolf.cmdchar + "visit <name>\" to visit a player.");
			privmsg(p.getNick(), playerlist);
		}
		if(p.roles[WolfPlayer.ROLE_ANGEL]) {
			privmsg(p.getNick(), "You are a \u0002guardian angel\u0002. You may choose one player to guard " +
				"per night. If you guard the victim, s/he will likely live. If you guard a wolf, you may die. Use \"" + Javawolf.cmdchar +
				"guard <name>\" to guard someone.");
			privmsg(p.getNick(), playerlist);
		}
		if(p.roles[WolfPlayer.ROLE_DETECTIVE]) {
			privmsg(p.getNick(), "You are a \u0002detective\u0002. You act during the day, and you can even " +
				"identify traitors. Use \"" + Javawolf.cmdchar + "id <name>\" to identify someone. Be careful when iding, because " +
				"your identity might be revealed to the wolves.");
			privmsg(p.getNick(), playerlist);
		}
		if(p.roles[WolfPlayer.ROLE_MEDIUM]) {
			privmsg(p.getNick(), "You are a \u0002medium\u0002. Once per day, you can choose to raise a " +
				"player from the dead to consult with him or her. However, the spirit will be unable to use any powers. Use \"" +
				Javawolf.cmdchar + "raise <name>\" to raise a player.");
			privmsg(p.getNick(), playerlist);
		}
		if(p.roles[WolfPlayer.ROLE_GUNNER]) {
			privmsg(p.getNick(), "You hold a gun that shoots special silver bullets. If you shoot a wolf, s/he will die. " +
				"If you shoot a villager, s/he will most likely live. Use \"" + Javawolf.cmdchar + "shoot <name>\" to shoot.");
			privmsg(p.getNick(), playerlist);
			privmsg(p.getNick(), "You have " + p.numBullets + " bullets remaining.");
		}
		if(p.lover != null) {
			privmsg(p.getNick(), "You have a lover, " + p.lover.getNickBold() + ", who is a \u0002" +
				p.lover.getDeathDisplayedRole() + "\u0002. If your lover dies, you will die too. If you two are the last " +
				"two players alive, then you will both win regardless of your roles.");
		}
	}

	/**
	 * Warns the channel that the night will be ending soon.
	 */
	private void warnNightEnding() {
		chanmsg(formatBold("The sky to the east begins to lighten, and several villagers wake up, hearing the panting of " +
			"wolves in the village. The full moon begins to set, casting eerie, lengthening shadows towards the dawn. " +
			"The night is now almost spent, and the wolves will be forced to return to human form soon."));
		// force the night to end
		TimerTask task = new TimerTask() {
			public void run() {
				endNight();
			}
		};
		timer.schedule(task, 1000*30);
	}
	
	private void warnDayEnding() {
		chanmsg(formatBold("As the sun sinks inexorably towards the tops of the towering pine trees turning the forest trees " + 
			"to the west into a series of black silhouettes projected against the flaming sky, the villagers are reminded " +
			"that very little time remains for them to lynch someone. If they cannot reach a decision before the sun sets, " +
			"the majority will carry the vote."));
		// force the night to end
		TimerTask task = new TimerTask() {
			public void run() {
				chanmsg(formatBold("While the villagers continue their debating, the sun reaches the horizon, forcing them to " +
					"conclude their deliberations!"));
				endDay();
			}
		};
		timer.schedule(task, 1000*120);
	}
    
    private void resetVotes(){
        for (int ctr = 0; ctr < getNumPlayers(); ctr++){
            getPlayer(ctr).votes = 0;
        }
    }
	
	/**
	 * Ends the night
	 */
	private void endNight() {
		System.out.println("[CONSOLE] : Ending the night.");
		timer.cancel();
		
		Random rand = new Random();
		// Posts the time the night took
		long millis = System.currentTimeMillis() - starttime;
		int secs = (int)(millis / 1000);
		int mins = (int)(secs / 60);
		secs = secs % 60;
		chanmsg("Night lasted \u0002" + mins + ":" + (secs < 10 ? "0" + secs : secs) + "\u0002. Dawn breaks! The villagers wake up and look around.");
		// Make the wolf list
		String wolflist = "Players: ";
		String playerlist = "Players: ";
        WolfPlayer temp;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			// Update wolf list
			if(temp.roles[WolfPlayer.ROLE_WOLF]) wolflist += temp.getNickBold() + " (wolf)";
			else if(temp.roles[WolfPlayer.ROLE_TRAITOR]) wolflist += temp.getNickBold() + " (traitor)";
			else if(temp.roles[WolfPlayer.ROLE_WERECROW]) wolflist += temp.getNickBold() + " (werecrow)";
			else if(temp.roles[WolfPlayer.ROLE_SORCERER]) wolflist += temp.getNickBold() + " (sorcerer)";
			else wolflist += temp.getNickBold();
			// Update player list
			playerlist += temp.getNickBold();
			if(m < (getNumPlayers() - 2)) {
				// add a comma
				wolflist = wolflist + ", ";
				playerlist = playerlist + ", ";
			} else if(m == (getNumPlayers() - 2)) {
				// second-to-last player added, add an "and"
				wolflist = wolflist + ", and ";
				playerlist = playerlist + ", and ";
			}
		}
		// Tally up the votes and announce the kill
		int[] voteresults = tallyvotes();
		WolfPlayer voteKill = getPlayer(voteresults[1]);
		if(voteKill == null) {
			// LOL
			chanmsg("The wolves were unable to decide who to kill last night. As a result, all villagers have survived.");
		} else {
			// Wolves attack someone
			// Is the target protected by a guardian angel?
			boolean successfullyguarded = false;
			for (int m = 0; m < getNumPlayers(); m++) {
                temp = getPlayer(m);
				// Is the player a guardian angel?
				if(temp.roles[WolfPlayer.ROLE_ANGEL] && temp.isAlive) {
					// Did the player guard the victim?
					if(temp.guarded == voteKill) {
						// Does the angel die in the attempt?
						if(rand.nextDouble() < dieguardingvictimpct) {
							chanmsg(temp.getNickBold() + ", a " + formatBold(temp.getDeathDisplayedRole()) + 
									", tried to defend the victim, but died in the attempt.");
							playerDeath(temp);
						} else if(rand.nextDouble() < guardedvictimdiepct) {
							// Angel failed in the defense.
							chanmsg("A guardian angel tried to protect " + voteKill.getNickBold() + 
								", but s/he was unable to stop the attacks of the wolves and was forced to retreat.");
						} else {
							// successful guarding
							successfullyguarded = true;
							// Does the angel become a wolf though?
							if(rand.nextDouble() < infectguardingvictimpct) {
								privmsg(temp.getNick(), "While defending the victim, you were slightly injured in the fray. " +
									"You feel a change coming over you...you have become a \u0002wolf\u0002!");
								privmsg(temp.getNick(), wolflist);
							}
							// Does the victim turn into a wolf? Obviously, wolfteam won't change.
							if(voteKill.countWolfRoles() == 0) {
								if(rand.nextDouble() < guardedvictiminfectpct) {
									privmsg(voteKill.getNick(), "Although a guardian angel saved you, you were slightly injured in the fray. " +
										"You feel a change coming over you...you have become a \u0002wolf\u0002!");
									privmsg(voteKill.getNick(), wolflist);
								}
							}
						}
					}
				}
			}
			if(!successfullyguarded) {
				// Is the target a harlot who visited someone?
				if(voteKill.roles[WolfPlayer.ROLE_HARLOT] && (voteKill.visited != null) && (voteKill.visited != voteKill)) {
					// Harlot visited someone else
					chanmsg("The wolves' selected victim was a harlot, but she wasn't home.");
				} else {
					chanmsg("The dead body of " + voteKill.getNickBold() + ", a \u0002" + voteKill.getDeathDisplayedRole() + 
						"\u0002, is found. Those remaining mourn his/her death.");
					playerDeath(voteKill);
					// Wolves might find a gun
					if(voteKill.roles[WolfPlayer.ROLE_GUNNER] && (voteKill.numBullets > 0)) {
						if(rand.nextDouble() < wolffindgunpct) {
							// randomly pick a wolfteam member to give the gun to
							int[] wolfidxs = new int[MAX_WOLFPLAYERS];
							int nWolves = 0;
							for (int m = 0; m < getNumPlayers(); m++) {
								if(getPlayer(m).countWolfRoles() > 0) {
									wolfidxs[nWolves] = m;
									nWolves++;
								}
							}
							int pickedwolf = wolfidxs[(int)Math.floor(rand.nextDouble()*nWolves)];
							getPlayer(pickedwolf).roles[WolfPlayer.ROLE_GUNNER] = true;
							getPlayer(pickedwolf).numBullets = voteKill.numBullets; // give him however many bullets remained
							// notify him
							privmsg(getPlayer(pickedwolf).getNick(), "You find \u0002" + voteKill.getNick() +
								"'s\u0002 gun with " + voteKill.numBullets + " bullets! Use \"" + Javawolf.cmdchar + 
								"shoot <name>\" to shoot a player. You will deliberately miss other wolves.");
						}
					}
				}
				// Did any harlots visit the victim?
				for (int m = 0; m < getNumPlayers(); m++) {
                    temp = getPlayer(m);
					if(temp.roles[WolfPlayer.ROLE_HARLOT] && temp.isAlive) {
						if(temp.visited == voteKill) {
							// Harlot visited victim and died
							chanmsg(temp.getNickBold() + ", a harlot, made the unfortunate mistake of visiting the victim last night " +
								"and is now dead.");
							playerDeath(temp);
						}
					}
				}
			} else {
				chanmsg(voteKill.getNickBold() + " was attacked by the wolves last night, but luckily, a " +
					"guardian angel protected him/her.");
			}
		}
		// Did any harlots visit wolves?
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			if(temp.roles[WolfPlayer.ROLE_HARLOT] && temp.isAlive && (temp.visited != null) && !(temp.visited == temp)) {
				if((temp.visited.roles[WolfPlayer.ROLE_WOLF] || temp.visited.roles[WolfPlayer.ROLE_WERECROW]) &&
						!(temp.visited == temp.lover)) {
					// Harlot visited wolf and died
					chanmsg(temp.getNickBold() + ", a \u0002harlot\u0002, made the unfortunate mistake of " +
						"visiting a wolf last night and is now dead.");
					playerDeath(temp);
				}
			}
		}
		// Did any guardian angels guard wolves?
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			// Is the player a guardian angel?
			if(temp.roles[WolfPlayer.ROLE_ANGEL] && temp.isAlive) {
				if(temp.guarded.roles[WolfPlayer.ROLE_WOLF] || temp.guarded.roles[WolfPlayer.ROLE_WERECROW]) {
					// Guarded a wolf
					if(rand.nextDouble() < dieguardingwolfpct) {
						chanmsg(temp.getNickBold() + ", a \u0002guardian angel\u0002, made the unfortunate mistake of " +
							"guarding a wolf last night, tried to escape, but failed. S/he is found dead.");
						playerDeath(temp);
					}
				}
			}
		}
		// Werecrows' observations return now
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			if(temp.isAlive) {
				if(temp.roles[WolfPlayer.ROLE_WERECROW]) {
					if(temp.observed != null) {
						if(temp.observed.isAlive) {
							// Did the target stay in bed?
							if((temp.observed.roles[WolfPlayer.ROLE_SEER] && (temp.observed.seen != null)) ||
								(temp.observed.roles[WolfPlayer.ROLE_HARLOT] && (temp.observed.visited != null)) ||
								(temp.observed.roles[WolfPlayer.ROLE_ANGEL] && (temp.observed.guarded != null))) {
								privmsg(temp.getNick(), temp.observed.getNickBold() + " left his/her bed last night.");
							} else {
								privmsg(temp.getNick(), temp.observed.getNickBold() + " remained in bed all night.");
							}
							// Did anyone "visit" the target?
                            WolfPlayer temp2;
							for (int n = 0; n < getNumPlayers(); n++) {
                                temp2 = getPlayer(n);
								if(temp2.isAlive) {
									if(temp2.roles[WolfPlayer.ROLE_ANGEL]) {
										if(temp2.guarded == temp.observed)
											privmsg(temp.getNick(), temp2.getNickBold() +
												", a \u0002guardian angel\u0002, guarded " + 
												temp.observed.getNickBold() + " last night.");
									} else if(temp2.roles[WolfPlayer.ROLE_SEER]) {
										if(temp2.seen == temp.observed)
											privmsg(temp.getNick(), temp2.getNickBold() +
												", a \u0002seer\u0002, saw " + 
												temp.observed.getNickBold() + " last night.");
									} else if(temp2.roles[WolfPlayer.ROLE_HARLOT]) {
										if(temp2.visited == temp.observed)
											privmsg(temp.getNick(), temp2.getNickBold() +
												", a \u0002harlot\u0002, visited " + 
												temp.observed.getNickBold() + " last night.");
									}
								}
							}
						}
					}
				}
			}
		}
		// Reset the players' actions and votes
		resetVotes();
		for (int m = 0; m < getNumPlayers(); m++) {
			getPlayer(m).resetActions();
		}
		// Check to see if the game ended
		if(checkForEnding()) return; // no need to go on
		// Let players know they can now lynch people.
		chanmsg("The villagers must now decide who to lynch. Use \"" + 
                Javawolf.cmdchar + "lynch <name>\" to vote for someone.");
		isNight = false;
		// Start timing the day
		timer = new Timer();
		starttime = System.currentTimeMillis();
		TimerTask task = new TimerTask() {
			public void run() {
				warnDayEnding();
			}
		};
		timer.schedule(task, 600*1000);
	}
	
	/**
	 * Ends the day
	 */
	private void endDay() {
		if(isRunning && !isNight) {
			timer.cancel();
			// Posts the time the day took
			long millis = System.currentTimeMillis() - starttime;
			int secs = (int)(millis / 1000);
			int mins = (int)(secs / 60);
			secs %= 60;
			chanmsg("Day lasted " + formatBold(mins + ":" + (secs < 10 ? "0" + secs : secs)) + ".");
            WolfPlayer temp;
			// Kill any raised spirits again
			for (int m = 0; m < getNumPlayers(); m++) {
                temp = getPlayer(m);
				if(temp.roles[WolfPlayer.ROLE_MEDIUM] && (temp.raised != null)) {
					chanmsg("As dusk falls, the spirit of " + temp.raised.getNickBold() +
                            " returns to rest.");
					devoice(temp.raised.getNick());
				}
				// also reset actions taken
				temp.resetActions();
			}
			// Starts the night
			isNight = true;
			startNight();
		}
	}
	
	/**
	 * Tallies the votes. First value is the number of votes, second value is the person most
	 * voted for. The second value is -1 when a tie occurs. 
	 * 
	 * @return
	 */
	private int[] tallyvotes() {
		int nVotes = -1; // number of votes
		int indVoted = -1; // who has that number
		WolfPlayer p;
		for (int m = 0; m < getNumPlayers(); m++) {
            p = getPlayer(m);
			if(p.votes > nVotes) {
				nVotes = p.votes;
				indVoted = m;
			} else if(p.votes == nVotes) {
				indVoted = -1; // tie
			}
		}
		
		// return the values
		int[] retvals = { nVotes, indVoted };
		return retvals;
	}
	
	/**
	 * Changes the command character
	 * 
	 * @param cmdchar
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void change_cmdchar(String cmdchar, String nick, String user, String host) {
		// notify channel
		chanmsg(formatBold(nick) + " has changed the command prefix to: '" + cmdchar + "'.");
		Javawolf.cmdchar = cmdchar;
	}
	
	/**
	 * Kills the bot
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void fquit(String nick, String user, String host) {
		// notify channel
		chanmsg(formatBold(nick) + " has ended the game and shut down the bot.");
		// clean up channel
		channelCleanup();
		// shuts down
		quitirc("Requested by " + nick);
		System.out.println("[CONSOLE] : " + nick + "!" + user + "@" + host + " has shut down this bot.");
		System.exit(0);
	}
	
	/**
	 * Forces a player to join the game
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void fjoin(String who, String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the FJOIN command");
		// Is the game already in progress?
		if(isRunning) {
			privmsg(nick, "The game is already in progress. Please wait patiently for it to end.");
			return;
		}
		
        // Need a way to retrieve user and host for "who"
        
        /*
		// Joins the game.
		if(addPlayer(who, user, host)) {
			chanmsg(formatBold(nick) + " has joined the game.");
			voice(nick);
		} else {
			privmsg(nick, who + " cannot join the game at this time.");
		}*/
	}
	
	/**
	 * Forces a player to leave the game.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void fleave(String who, String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the FLEAVE command on " + who);
		// get who this is
		WolfPlayer p = getPlayer(who);
		if(p == null) {
			// not even in the game...
			privmsg(nick, formatBold(who) + " isn't playing.");
			return;
		}
		// Is the game already in progress?
		if(isRunning) {
			p.isAlive = false;
			// kill him
			chanmsg(p.getNickBold()+" was thrown out of the village and devoured by wild animals. " +
				"It appears s/he was a " + formatBold(p.getDeathDisplayedRole()) + ".");
			playerDeath(p);
			// Does game end?
			if(checkForEnding()) return;
			if(isNight) {
				if(checkEndNight()) endNight(); // Does night end?
			} else {
				checkForLynching(); // Does day end?
			}
		} else {
			String killedNick = p.getNick();
			if(rmvPlayer(p.getNick(), p.getUser(), p.getHost())) {
				chanmsg(formatBold(killedNick) + " was removed from the game.");
				devoice(killedNick);
				System.out.println("[CONSOLE] : " + killedNick + " was ejected from the game.");
			} else {
				privmsg(nick, "You cannot kick " + formatBold(killedNick) + " from the game at this time.");
			}
		}
	}
	
	
	/**
	 * Forces the game to end.
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void fendgame(String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the FENDGAME command");
		// notify channel
		chanmsg(formatBold(nick) + " has forced the game to end.");
		// clean up channel
		channelCleanup();
	}
	
	/**
	 * Cleans up the channel after a game finishes.
	 */
	private void channelCleanup() {
		// clear the timers
		if(timer != null) timer.cancel();
		if(idler != null) idler.cancel();
		// unmute the channel
		chanunmute();
		// devoice the players
		String devoicelist = "";
		int count = 0;
        WolfPlayer temp;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			if(isRunning && temp.isAlive) {
				devoicelist = devoicelist + temp.getNick() + " ";
				count++;
			} else if(!isRunning) {
				devoicelist = devoicelist + temp.getNick() + " ";
				count++;
			}
			// send at 4
			if(((count % 4) == 0) && (count > 0)) {
				Javawolf.wolfbot.sendRawLineViaQueue("MODE " + mainChannel + " -vvvv " + devoicelist);
				System.out.println("[CONSOLE] : MODE " + mainChannel + " -vvvv " + devoicelist);
				devoicelist = "";
				count -= 4;
			}
			// If in wolf channel, kick him out.
			if(wolfChannel != null) {
				if((temp.countWolfRoles() > 0) && temp.isAlive) {
					kickplayer(wolfChannel, temp.getNick(), "Game ended");
				}
			}
			if(tavernChannel != null) {
				if((temp.countWolfRoles() > 0) && temp.isAlive && temp.isInTavern) {
					kickplayer(tavernChannel, temp.getNick(), "Game ended");
				}
			}
		}
		// devoice last remaining players
		if(count > 0) {
			int n = 0;
			String modeset = "-";
			while(n < count) {
				modeset = modeset + "v";
				n++;
			}
			Javawolf.wolfbot.sendRawLineViaQueue("MODE " + mainChannel + " " + modeset + " " + devoicelist);
			System.out.println("[CONSOLE] : MODE " + mainChannel + " " + modeset + " " + devoicelist);
		}
		// make new players
		players.clear();
		resetVotes();
		isRunning = false;
		isNight = false;
		// new start time
		gameAllowStart = System.currentTimeMillis() + 60000;
		nWaitTimes = 0;
	}
	
	/**
	 * Handles player death code.
	 * 
	 * @param idx
	 */
	private void playerDeath(WolfPlayer p) {
		// sanity check
		if(!isRunning) {
			System.err.println("[CONSOLE] : ERROR : <Playerdeath> called while game is not running!");
			return;
		}
		// If in wolf channel, kick him out.
		if(wolfChannel != null && p.countWolfRoles() > 0) {
            kickplayer(wolfChannel, p.getNick(), "You have died");
		}
		if(tavernChannel != null && p.isInTavern) {
            kickplayer(tavernChannel, p.getNick(), "You have died");
		}
		// Now dead
		p.isAlive = false;
		// Remove vote
		if(p.voted != null) p.voted.votes--;
		// Remove all actions
		p.resetActions();
		// Devoice player
		devoice(p.getNick());
		// Kills the lover
		if(p.lover != null) {
			// Prevent recursively calling <playerDeath>
			if(p.lover.isAlive) {
				chanmsg(p.lover.getNickBold() + ", a " + 
                        p.lover.getDeathDisplayedRole() +
					" was " + p.getNick() + "'s lover and is dying of a broken heart!");
				// return whether the game ends following the lover's death (preventing multiple endings)
				playerDeath(p.lover);
			}
		}
	}
	
	/**
	 * Gets the index for which player matches the nick!user@host
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 * @return
	 */
	private WolfPlayer getPlayer(String nick, String user, String host) {
        WolfPlayer temp;
		for (int ctr = 0; ctr < getNumPlayers(); ctr++) {
            temp = getPlayer(ctr);
			if (temp.identmatch(nick, user, host)) {
				return temp; // found him
			}
		}
		// no such player
		return null;
	}
	
	/**
	 * Gets the player index who matches <nick>
	 * 
	 * @param nick
	 * @return
	 */
	private WolfPlayer getPlayer(String nick) {
        WolfPlayer p;
		WolfPlayer matched_player = null;
		for (int ctr = 0; ctr < getNumPlayers(); ctr++) {
            p = getPlayer(ctr);
			if(p.getNick().contentEquals(nick)) {
				return p; // exact match; return immediately
			} else if(p.getNick().toLowerCase().startsWith(nick.toLowerCase())) {
				if(matched_player == null)
                    matched_player = p; // partial match
				else {
                    matched_player = null; // multiple matches
                    break;
                }
			}
		}
		// return the player
		return matched_player;
	}
	
	/**
	 * A player changes his nick
	 * 
	 * @param oldNick
	 * @param user
	 * @param host
	 * @param newNick
	 */
	public void changeNick(String oldNick, String user, String host, String newNick) {
		// get him
		WolfPlayer p = getPlayer(oldNick, user, host);
		if(p != null) {
			p.setNick(newNick);
		}
	}
	
	/**
	 * A player left the channel
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	public void playerLeftChannel(String nick, String user, String host) {
		// get him
		WolfPlayer p = getPlayer(nick, user, host);
		if(p != null) {
			if(isRunning) {
				// Game is running, kill the player
				if(p.isAlive) {
					chanmsg(p.getNickBold() +
                            " died of an unknown disease. It appears s/he was a " +
						formatBold(p.getDeathDisplayedRole()) + ".");
					playerDeath(p);
				}
			} else {
				// No game; delete said player
				chanmsg(p.getNickBold() + " left the game.");
				rmvPlayer(nick, user, host);
			}
		}
	}
	
	/**
	 * Player kicked from channel
	 * 
	 * @param nick
	 */
	public void playerKickedFromChannel(String nick) {
		// get him
		WolfPlayer p = getPlayer(nick);
		if(p != null) {
			if(isRunning) {
				if(p.isAlive) {
					// Game is running, kill the player
					chanmsg(p.getNickBold() + " was kicked off a cliff. It appears s/he was a " +
						formatBold(p.getDeathDisplayedRole()) + ".");
					playerDeath(p);
				}
			} else {
				// No game; delete said player.
				chanmsg(p.getNickBold() + " was kicked from the game.");
				rmvPlayer(nick, p.getUser(), p.getHost());
			}
		}
	}
	
	/**
	 * Resets the idletime when somebody says something.
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	public void resetIdle(String nick, String user, String host) {
		WolfPlayer p = getPlayer(nick, user, host);
        if( p != null && p.addAction(System.currentTimeMillis())) {
            // Player is flooding. Kick him out of the channel.
            System.out.println("[CONSOLE] : Kicking " + nick + " for flooding.");
            kickplayer(mainChannel, p.getNick(), "Channel flooding is not acceptable.");
            // Ignore anything further that he does.
            Javawolf.ignoredHosts.add(host);
        }
	}
	
	/**
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 * @return
	 */
	private boolean addPlayer(String nick, String user, String host) {
		// Does this guy already exist?
		if(getPlayer(nick, user, host) != null) 
            return false;
		// only add the player if enough slots
		if(getNumPlayers() < MAX_WOLFPLAYERS) {
			players.add(new WolfPlayer(nick, user, host, System.currentTimeMillis()));
			return true;
		} else {
            chanmsg("Maximum number of players has been reached.");
        }
		// couldn't add
		return false;
	}
	
	/**
	 * Removes a player before the game begins.
	 * DO NOT CALL THIS WHILE THE GAME IS RUNNING!
	 * Simply kill said player instead.
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 * @return
	 */
	private boolean rmvPlayer(String nick, String user, String host) {
		// DO NOT DELETE PLAYERS DURING THE GAME!!!
		// If a player is removed during the game, the votes will be corrupted
		if(isRunning) {
			System.err.println("INTERNAL ERROR : <rmvPlayer> called while the game is running!");
			return false;
		}
		
		// removes the player
		WolfPlayer p = getPlayer(nick, user, host);
		if(p != null) {
			// rotate them down
			players.remove(p);
			// player is gone
			return true;
		} else {
			System.err.println("[CONSOLE] : Could not find \"" + nick + "!" + user + "@" + host + "\"!");
			return false;
		}
	}
	
	/**
	 * Broadcasts team messages.
	 * 
	 * @param msg
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void teammsg(String msg, String nick, String user, String host) {
		WolfPlayer p = getPlayer(nick, user, host);
		if (p == null) 
            return; // no such user
		// Don't let dead people chat
		if (!p.isAlive) 
            return;
		// Is it a wolf team or tavern message?
		boolean isWolf = p.countWolfRoles() > 0;
		boolean isTavern = p.isInTavern;
		if(isTavern && isWolf) {
			// Both in tavern and wolf. To which is this going?
			if(msg.toLowerCase().startsWith("t:")) 
                tavernmsg(formatBold(nick) + " says: " + msg.substring(2));
			else 
                wolfmsg(formatBold(nick) + " says: " + msg);
		} else if(isWolf) {
			// Send to wolf team.
			wolfmsg(formatBold(nick) + " says: " + msg);
		} else if(isTavern) {
			// Send to tavern.
			tavernmsg(formatBold(nick) + " says: " + msg);
		}
	}
	
	/**
	 * Broadcasts messages to the wolf team
	 * 
	 * @param msg
	 */
	private void wolfmsg(String msg) {
        WolfPlayer temp;
		// Wolf team has a dedicated channel.
		if(wolfChannel != null) return;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			if(temp.isAlive) {
				if(temp.countWolfRoles() > 0) privmsg(temp.getNick(), msg);
			}
		}
	}
	
	/**
	 * Broadcasts messages to the players in the village tavern
	 * 
	 * @param msg
	 */
	private void tavernmsg(String msg) {
        WolfPlayer temp;
		// Tavern has a dedicated channel.
		if (tavernChannel != null) return;
		for (int m = 0; m < getNumPlayers(); m++) {
            temp = getPlayer(m);
			if(temp.isInTavern && temp.isAlive) privmsg(temp.getNick(), msg);
		}
	}
	
	/**
	 * Messages the channel
	 * 
	 * @param msg the message
	 */
	private void chanmsg(String msg) {
		Javawolf.wolfbot.sendMessage(mainChannel, msg);
	}
	
	/**
	 * Checks to see if the given user is a bot admin
	 * 
	 * @param host
	 * @return
	 */
	private boolean admincheck(String host) {
		return Javawolf.trustedHosts.contains(host);
	}
	
	/**
	 * Messages a player
	 * 
	 * @param nick
	 * @param msg
	 */
	private void privmsg(String nick, String msg) {
		Javawolf.wolfbot.sendMessage(nick, msg);
	}
	
	/**
	 * Mutes the channel
	 */
	private void chanmute() {
		Javawolf.wolfbot.setMode(mainChannel, "+m");
	}
	
	/**
	 * Unmutes the channel
	 */
	private void chanunmute() {
		Javawolf.wolfbot.setMode(mainChannel, "-m");
	}
	
	/**
	 * Voices the given player
	 * 
	 * @param nick
	 */
	private void voice(String nick) {
		Javawolf.wolfbot.voice(mainChannel, nick);
	}
	
	/**
	 * Devoices the given player
	 * 
	 * @param nick
	 */
	private void devoice(String nick) {
		Javawolf.wolfbot.sendRawLineViaQueue("MODE " + mainChannel + " -v " + nick);
		//Javawolf.wolfbot.deVoice(mainChannel, nick);
	}
	
	/**
	 * Ops the speaker.
	 * 
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void op(String nick, String user, String host) {
		Javawolf.wolfbot.op(mainChannel, nick);
	}
	
	/**
	 * Deops the target.
	 * Deops you if target is null.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void deop(String who, String nick, String user, String host) {
		if(who == null) {
			Javawolf.wolfbot.deOp(mainChannel, nick);
		} else {
			Javawolf.wolfbot.deOp(mainChannel, who);
		}
	}
	
	/**
	 * Kicks a player from the channel for the given reason.
	 * 
	 * @param nick
	 * @param reason
	 */
	private void kickplayer(String channel, String nick, String reason) {
		Javawolf.wolfbot.kick(channel, nick, reason);
	}
	
	/**
	 * Leaves the server
	 * 
	 * @param reason
	 */
	private void quitirc(String reason) {
		Javawolf.wolfbot.quitServer(reason);
	}
	
	/**
	 * Ignores a hostmask.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void ignoreHost(String who, String nick, String user, String host) {
		System.out.println("[CONSOLE] : \"" + who + "\" was added to ignore list by " + nick + ".");
		Javawolf.ignoredHosts.add(who);
	}
	
	/**
	 * Removes an ignore on a hostmask.
	 * 
	 * @param who
	 * @param nick
	 * @param user
	 * @param host
	 */
	private void unignoreHost(String who, String nick, String user, String host) {
		System.out.println("[CONSOLE] : \"" + who + "\" was removed from the ignore list by " + nick + ".");
		if(!Javawolf.ignoredHosts.remove(who)) {
			// Couldn't remove target.
			System.err.println("[CONSOLE] : Could not find hostmask \"" + who + "\" in ignore list.");
			chanmsg("Could not find hostmask \"" + who + "\" in ignore list.");
		}
	}
	
	/**
	 * Adds a player configuration
	 * 
	 * @param wc
	 */
	/*public static void addconfig(WolfConfig wc) {
		configs[nCfgs] = wc;
		nCfgs++;
	}*/
	
	/**
	 * Gets the configuration for the given number of players.
	 * 
	 * @param num
	 * @return
	 */
	private WolfConfig getConfig(int num) {
		for (int m = 0; m < nCfgs; m++) {
			if(configs[m] == null) {
				// some bug
				System.err.println("[CONSOLE] : ERROR : Config #" + m + " is null!");
				return null;
			}
			if((num <= configs[m].high) && (num >= configs[m].low)) {
				return configs[m];
			}
		}
		// configuration error...no valid configs for this player count
		return null;
	}
    
    public static String formatBold(String str){
        return Colors.BOLD + str + Colors.BOLD;
    }
}

/**
 * Used for configuring the roles for the given number of players.
 * 
 * @author Reaper Eternal
 *
 */
class WolfConfig {
	// Minimum number of players needed to have this setup
	public int low = -1;
	// Maximum number of players needed to have this setup
	public int high = -1;
	// Wolf team roles
	public int wolfcount = 0;
	public int traitorcount = 0;
	public int werecrowcount = 0;
	public int sorcerercount = 0;
	// Village primary roles
	public int seercount = 0;
	public int harlotcount = 0;
	public int drunkcount = 0;
	public int angelcount = 0;
	public int detectivecount = 0;
	public int mediumcount = 0;
	// Village secondary roles
	public int cursedcount = 0;
	public int gunnercount = 0;
	public int loverpaircount = 0;
	
	// null constructor
	public WolfConfig() { }
}

