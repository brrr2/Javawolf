/*
 * Import classes
 */
package Javawolf;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import org.jibble.pircbot.Colors;

/**
 * @author Reaper Eternal
 *
 */
public class WolfGame {
	// Main channel associated with this instance of the game.
	private String mainChannel = null;
	// Wolf channel associated with this instance of the game.
	private String wolfChannel = null;
	// Tavern channel associated with this instance of the game.
	private String tavernChannel = null;
	// Player list
	private WolfPlayer[] players = null;
	// Config list
	private static WolfConfig[] configs = new WolfConfig[64];
	private static int nCfgs = 0;
	// Votelist
	private int[] votes = null;
	// Maximum player count
	public static final int MAX_WOLFPLAYERS = 32;
	// Player count
	private int playernum = 0;
	
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
	public WolfGame(String mainChannel, String wolfChannel, String tavernChannel, String pConfig) {
		// set last game ending to now
		gameAllowStart = System.currentTimeMillis() + 60000;
		// sets the associated channels
		this.mainChannel = mainChannel;
		this.wolfChannel = wolfChannel;
		this.tavernChannel = tavernChannel;
		// creates the lists
		players = new WolfPlayer[MAX_WOLFPLAYERS];
		votes = new int[MAX_WOLFPLAYERS];
		// loads config
		loadPConfig(pConfig);
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
			if(args == null) return;
			if(args.length < 2) return;
			if(admincheck(host)) {
				deop(args[1], nick, user, host);
			} else {
				privmsg(nick, "You are not an admin.");
			}
		} else if(cmd.equals("cmdchar")) {
			// Admin command: Changes command character prefix.
			if(args == null) return;
			if(args.length < 2) return;
			if(admincheck(host)) {
				change_cmdchar(args[1], nick, user, host);
			} else {
				privmsg(nick, "You are not an admin.");
			}
		} else if(cmd.equals("ignore")) {
			// Admin command: Ignores a hostmask.
			if(args == null) return;
			if(args.length < 2) return;
			if(admincheck(host)) {
				ignoreHost(args[1], nick, user, host);
			} else {
				privmsg(nick, "You are not an admin.");
			}
		} else if(cmd.equals("unignore")) {
			// Admin command: Unignores a hostmask.
			if(args == null) return;
			if(args.length < 2) return;
			if(admincheck(host)) {
				unignoreHost(args[1], nick, user, host);
			} else {
				privmsg(nick, "You are not an admin.");
			}
		} else if(cmd.equals("loadconfig")) {
			// Admin command: Loads a new player configuration.
			if(args == null) return;
			if(args.length < 2) return;
			if(admincheck(host)) {
				loadPConfig(args[1]);
			} else {
				privmsg(nick, "You are not an admin.");
			}
		} else if(cmd.equals("stats")) {
			// Who is left in the game?
			stats(nick, user, host);
		} else {
			// Allows private team chat
			if(args == null) return;
			String s = args[0];
			int m = 1;
			while(m < args.length) {
				s = s + " " + args[m]; // concatenate
				m++;
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
		if(var.contentEquals("allowcursedseer")) {
			allowCursedSeer = Boolean.parseBoolean(val);
		} else if(var.contentEquals("gunnermisspct")) {
			try {
				gunnermisspct = Double.parseDouble(val);
			} catch(NumberFormatException e) {
				System.err.println("[CONSOLE] : Could not parse \"" + val + "\"!");
				return;
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
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
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
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// PM the player his role.
		String wolflist = "Players: ";
		String playerlist = "Players: ";
		int m = 0;
		while(m < playernum) {
			// Update wolf list
			if(players[m].roles[WolfPlayer.ROLE_WOLF]) wolflist = wolflist + "\u0002" + players[m].getNick() + "\u0002 (wolf)";
			else if(players[m].roles[WolfPlayer.ROLE_TRAITOR]) wolflist = wolflist + "\u0002" + players[m].getNick() + "\u0002 (traitor)";
			else if(players[m].roles[WolfPlayer.ROLE_WERECROW]) wolflist = wolflist + "\u0002" + players[m].getNick() + "\u0002 (werecrow)";
			else if(players[m].roles[WolfPlayer.ROLE_SORCERER]) wolflist = wolflist + "\u0002" + players[m].getNick() + "\u0002 (sorcerer)";
			else wolflist = wolflist + "\u0002" + players[m].getNick() + "\u0002";
			// Update player list
			playerlist = playerlist + "\u0002" + players[m].getNick() + "\u0002";
			if(m < (playernum - 2)) {
				// add a comma
				wolflist = wolflist + ", ";
				playerlist = playerlist + ", ";
			} else if(m == (playernum - 2)) {
				// second-to-last player added, add an "and"
				wolflist = wolflist + ", and ";
				playerlist = playerlist + ", and ";
			} else if(m == (playernum - 1)) {
				// last player, no need for comma
			}
			// Next player
			m++;
		}
		sendPlayerRole(plidx, playerlist, wolflist);
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
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a sorcerer?
		if(!players[plidx].roles[WolfPlayer.ROLE_SORCERER]) {
			privmsg(nick, "Only sorcerers can curse other players.");
			return;
		}
		// Is it night?
		if(!isNight) {
			privmsg(nick, "You may only curse people at night.");
			return;
		}
		// Has the player not yet cursed someone?
		if(players[plidx].cursed >= 0) {
			privmsg(nick, "You have already cursed someone this night.");
			return;
		}
		// Get the target
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!players[targidx].isAlive) {
			privmsg(nick, players[targidx].getNick() + " is already dead.");
			return;
		}
		// Is the target a wolf?
		if(players[targidx].roles[WolfPlayer.ROLE_WOLF] || players[targidx].roles[WolfPlayer.ROLE_WERECROW]) {
			privmsg(nick, "You don't see the point in cursing your wolf friends.");
			return;
		}
		// the curse falls
		players[plidx].cursed = targidx;
		players[targidx].roles[WolfPlayer.ROLE_CURSED] = true;
		// let you know
		privmsg(nick, "Your fingers move nimbly as you cast the dark enchantment. \u0002" +
			players[targidx].getNick() + "\u0002 has become cursed!");
		// chance of the target knowing his cursed status
		Random rand = new Random();
		if(rand.nextDouble() < sorcerervictimnoticecursepct) {
			privmsg(players[targidx].getNick(), "You feel the mark of Cain fall upon you....");
		}
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
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
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a medium?
		if(!players[plidx].roles[WolfPlayer.ROLE_MEDIUM]) {
			privmsg(nick, "Only mediums can raise other players.");
			return;
		}
		// Is it day?
		if(isNight) {
			privmsg(nick, "You may only raise players during the day.");
			return;
		}
		// Has the player not yet raised anyone?
		if(players[plidx].raised >= 0) {
			privmsg(nick, "You have already raised someone today.");
			return;
		}
		// Gets the target
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target dead? 
		if(players[targidx].isAlive) {
			privmsg(nick, players[targidx].getNick() + " is still alive and can be consulted normally.");
			return;
		}
		// raise him
		chanmsg(formatBold(players[plidx].getNick()) + " has cast a seance! The spirit of \u0002" +
			players[targidx].getNick() + "\u0002 is raised for the day.");
		voice(players[targidx].getNick()); // Make him able to chat again.
		players[plidx].raised = targidx;
		
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
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a guardian angel?
		if(!players[plidx].roles[WolfPlayer.ROLE_ANGEL]) {
			privmsg(nick, "Only guardian angels can guard other players.");
			return;
		}
		// Is it night?
		if(!isNight) {
			privmsg(nick, "You may only guard during the night.");
			return;
		}
		// Has the player not yet guarded?
		if(players[plidx].guarded >= 0) {
			privmsg(nick, "You are already guarding \u0002" + players[players[plidx].guarded].getNick() + "\u0002 tonight.");
			return;
		}
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!players[targidx].isAlive) {
			privmsg(nick, players[targidx].getNick() + " is already dead.");
			return;
		}
		// Guards the target.
		privmsg(nick, "You are guarding \u0002" + players[targidx].getNick() + "\u0002 tonight. Farewell.");
		privmsg(players[targidx].getNick(), "You can sleep well tonight, for a guardian angel is protecting you.");
		players[plidx].guarded = targidx;
		
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
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a werecrow?
		if(!players[plidx].roles[WolfPlayer.ROLE_WERECROW]) {
			privmsg(nick, "Only werecrows can observe other players.");
			return;
		}
		// Is it night?
		if(!isNight) {
			privmsg(nick, "You may only observe during the night.");
			return;
		}
		// Has the player not yet observed?
		if(players[plidx].observed >= 0) {
			privmsg(nick, "You are already observing \u0002" + players[players[plidx].observed].getNick() + "\u0002 tonight.");
			return;
		}
		// Get target
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!players[targidx].isAlive) {
			privmsg(nick, players[targidx].getNick() + " is already dead.");
			return;
		}
		// Observe the targetted player.
		privmsg(nick, "You change into a large black crow and fly off to see whether \u0002" + players[targidx].getNick() +
			"\u0002 remains in bed all night.");
		players[plidx].observed = targidx;
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
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
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a detective?
		if(!players[plidx].roles[WolfPlayer.ROLE_DETECTIVE]) {
			privmsg(nick, "Only detectives can id other players.");
		}
		// Is it day?
		if(isNight) {
			privmsg(nick, "You may only identify players during the day.");
			return;
		}
		// Has the player not yet ided anyone?
		if(players[plidx].ided >= 0) {
			privmsg(nick, "You have already identified someone today.");
			return;
		}
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!players[targidx].isAlive) {
			privmsg(nick, players[targidx].getNick() + " is already dead.");
			return;
		}
		// id his role
		privmsg(nick, "The results of your investigation return: \u0002" + players[targidx].getNick() +
			"\u0002 is a " + players[targidx].getIDedRole() + "!");
		players[plidx].ided = targidx;
		// drop papers?
		Random rand = new Random();
		if(rand.nextDouble() < detectivefumblepct) {
			// notify the wolves of the detective
			wolfmsg(formatBold(players[plidx].getNick()) + " drops a paper revealing s/he is a \u0002detective\u0002!");
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
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a seer?
		if(!players[plidx].roles[WolfPlayer.ROLE_GUNNER]) {
			privmsg(nick, "You don't have a gun.");
			return;
		}
		// Is it day?
		if(isNight) {
			privmsg(nick, "You can only shoot during the day.");
			return;
		}
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		} else if(targidx == plidx) {
			// lol, don't suicide
			privmsg(nick, "You're holding it the wrong way!");
			return;
		}
		// Is the target alive? 
		if(!players[targidx].isAlive) {
			privmsg(nick, players[targidx].getNick() + " is already dead.");
			return;
		}
		// Do you have any bullets left?
		if(players[plidx].numBullets == 0) {
			privmsg(nick, "You have no more bullets remaining.");
			return;
		}
		
		// He can now fire.
		chanmsg("\u0002" + players[plidx].getNick() + "\u0002 raises his/her gun and fires at \u0002" +
			players[targidx].getNick() + "\u0002!");
		players[plidx].numBullets--; // Uses a bullet.
		// Does he explode?
		Random rand = new Random();
		if(rand.nextDouble() < gunnerexplodepct) {
			// Boom! You lose.
			chanmsg("\u0002" + players[plidx].getNick() + "\u0002 should have cleaned his/her gun better. " +
				"The gun explodes and kills him/her!");
			chanmsg("It appears s/he was a \u0002" + players[plidx].getDeathDisplayedRole() + "\u0002.");
			playerDeath(plidx);
			return;
		}
		
		// Wolf teammates will deliberately miss other wolves.
		if((players[plidx].countWolfRoles() > 0) &&
				(players[targidx].roles[WolfPlayer.ROLE_WOLF] || players[targidx].roles[WolfPlayer.ROLE_WERECROW])) {
			chanmsg("\u0002" + players[plidx].getNick() + "\u0002 is a lousy shooter! S/he missed!");
			return;
		}
		// Missed target? (Drunks have 3x the miss chance.)
		if(rand.nextDouble() < (players[plidx].roles[WolfPlayer.ROLE_DRUNK] ? gunnermisspct*3 : gunnermisspct)) {
			chanmsg("\u0002" + players[plidx].getNick() + "\u0002 is a lousy shooter! S/he missed!");
			return;
		}
		// We hit the target.
		// Is the shot person a wolf?
		if(players[targidx].roles[WolfPlayer.ROLE_WOLF] || players[targidx].roles[WolfPlayer.ROLE_WERECROW]) {
			chanmsg("\u0002" + players[targidx].getNick() + "\u0002 is a " + 
				players[targidx].getDeathDisplayedRole() + " and is dying from the silver bullet!");
			playerDeath(targidx);
		} else {
			// Was it a headshot?
			if(rand.nextDouble() < gunnerheadshotpct) {
				chanmsg("\u0002" + players[targidx].getNick() + "\u0002 was not a wolf but was accidentally " +
					"fatally injured! It appears that s/he was a " + players[targidx].getDeathDisplayedRole() + ".");
				playerDeath(targidx);
			} else {
				// Injured a villager, but did not kill him.
				players[targidx].canVote = false;
				chanmsg("\u0002" + players[targidx].getNick() + "\u0002 was a villager and is injured by the " +
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
			chanmsg("There are " + formatBold(playernum+"") + " players waiting to begin the game.");
			return;
		}
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player alive?
		if(!players[plidx].isAlive) {
			privmsg(nick, "You have died already and thus cannot use the stats command.");
			return;
		}
		// List all the players.
		String plist = "Players: ";
		int nLivingPlayers = countLivingPlayers();
		// count them
		int count = 0;
		for (int m = 0; m < playernum; m++) {
			if(players[m].isAlive) {
				count++;
				if(count-nLivingPlayers <= -2) plist = plist + " \u0002" + players[m].getNick() + "\u0002, ";
				else if(count-nLivingPlayers == -1) plist = plist + " \u0002" + players[m].getNick() + "\u0002, and ";
				else if(count-nLivingPlayers == 0) plist = plist + " \u0002" + players[m].getNick() + "\u0002.";
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
		for (int m = 0; m < playernum; m++) {
			if(players[m].isAlive) {
				if(players[m].roles[WolfPlayer.ROLE_SEER]) seercount++;
				if(players[m].roles[WolfPlayer.ROLE_DRUNK]) drunkcount++;
				if(players[m].roles[WolfPlayer.ROLE_ANGEL]) angelcount++;
				if(players[m].roles[WolfPlayer.ROLE_HARLOT]) harlotcount++;
				if(players[m].roles[WolfPlayer.ROLE_DETECTIVE]) detectivecount++;
				if(players[m].roles[WolfPlayer.ROLE_GUNNER]) gunnercount++;
				if(players[m].roles[WolfPlayer.ROLE_WOLF]) wolfcount++;
				if(players[m].roles[WolfPlayer.ROLE_TRAITOR]) traitorcount++;
				if(players[m].roles[WolfPlayer.ROLE_WERECROW]) werecrowcount++;
				if(players[m].roles[WolfPlayer.ROLE_SORCERER]) sorcerercount++;
				livingcount++;
			}
		}
		// display results
		String str = "There are \u0002" + livingcount + " villagers\u0002 remaining.";
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
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a harlot?
		if(!players[plidx].roles[WolfPlayer.ROLE_HARLOT]) {
			privmsg(nick, "Only harlots can visit other players.");
			return;
		}
		// Is it night?
		if(!isNight) {
			privmsg(nick, "You may only visit other players during the night.");
			return;
		}
		// Has the player not yet visited?
		if(players[plidx].visited >= 0) {
			privmsg(nick, "You have already visited " + players[players[plidx].visited].getNick() + " tonight.");
			return;
		}
		// get target
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!players[targidx].isAlive) {
			privmsg(nick, "Eww! " + players[targidx].getNick() + " is already dead.");
		}
		// Visiting yourself?
		if(targidx == plidx) {
			// Notify harlot
			privmsg(nick, "You decide to stay home for the night.");
			players[plidx].visited = plidx;
		} else {
			// Notify both players
			players[plidx].visited = targidx;
			privmsg(players[plidx].getNick(), "You are spending the night with \u0002" + players[targidx].getNick() + 
				"\u0002. Have a good time!");
			privmsg(players[targidx].getNick(), "\u0002" + players[plidx].getNick() + "\u0002, a \u0002harlot\u0002, " +
				"has come to spend the night with you. Have a good time!");
		}
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
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
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a seer?
		if(!players[plidx].roles[WolfPlayer.ROLE_SEER]) {
			privmsg(nick, "Only seers can see other players.");
			return;
		}
		// Is it night?
		if(!isNight) {
			privmsg(nick, "Visions may only be had during the night.");
			return;
		}
		// Has the player not yet seen?
		if(players[plidx].seen >= 0) {
			privmsg(nick, "You have already had a vision this night.");
			return;
		}
		// get target
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!players[targidx].isAlive) {
			privmsg(nick, players[targidx].getNick() + " is already dead.");
			return;
		}
		// PM the vision
		privmsg(nick, "You have a vision; in this vision you see that " + formatBold(players[targidx].getNick()) +
			" is a " + players[targidx].getSeenRole() + "!");
		players[plidx].seen = targidx;
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
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
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a drunk?
		if(!players[plidx].roles[WolfPlayer.ROLE_DRUNK]) {
			privmsg(nick, "Only drunks can eject other players.");
			return;
		}
		// get target
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!players[targidx].isAlive) {
			privmsg(nick, players[targidx].getNick() + " is already dead.");
			return;
		}
		// Notifies tavern
		if(tavernChannel == null) {
			tavernmsg("\u0002" + players[targidx].getNick() + "\u0002 has been thrown out of the tavern.");
		} else {
			kickplayer(tavernChannel, players[targidx].getNick(), "You were thrown out of the tavern");
		}
		players[targidx].isInTavern = false;
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
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player a seer?
		if(!players[plidx].roles[WolfPlayer.ROLE_DRUNK]) {
			privmsg(nick, "Only drunks can invite other players.");
			return;
		}
		// get target
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!players[targidx].isAlive) {
			privmsg(nick, players[targidx].getNick() + " is already dead.");
			return;
		}
		if(tavernChannel == null) {
			// Notifies tavern
			tavernmsg("\u0002" + players[targidx].getNick() + "\u0002 has entered the tavern.");
			// Brings the player into the tavern.
			privmsg(players[targidx].getNick(), "\u0002" + nick + "\u0002, a \u0002village drunk\u0002, has brought you into the village tavern. " +
				"If you PM me, your messages will go to all other users in the tavern.");
			// Lets wolves know how to speak in tavern. They have to prefix their text with "t:" so their messages don't go via the wolf pm.
			if(players[targidx].countWolfRoles() > 0) {
				privmsg(players[targidx].getNick(), "Because you are a wolf, you will need to prefix your chat with \"T:\" to talk in the tavern. " +
					"This is to keep your messages to the wolves from accidentally going to the tavern and revealing you as a wolf.");
			}
		} else {
			// Send invite
			Javawolf.wolfbot.sendInvite(players[targidx].getNick(), tavernChannel);
		}
		players[targidx].isInTavern = true;
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
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Is the player alive?
		if(!players[plidx].isAlive) {
			privmsg(nick, "Dead players aren't going to be killing people anytime soon.");
			return;
		}
		// Is the player a wolf?
		if(!players[plidx].roles[WolfPlayer.ROLE_WOLF] && !players[plidx].roles[WolfPlayer.ROLE_WERECROW]) {
			privmsg(nick, "Only wolves can kill other players.");
			return;
		}
		// Is it night?
		if(!isNight) {
			privmsg(nick, "Killing may only be done during the night.");
			return;
		}
		// gets the target
		int targidx = getPlayer(who);
		// Is the target even playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// Is the target alive? 
		if(!players[targidx].isAlive) {
			privmsg(nick, players[targidx].getNick() + " is already dead.");
			return;
		}
		// Did you target yourself?
		if(plidx == targidx) {
			privmsg(nick, "Suicide is bad. Don't do it.");
			return;
		}
		// Is the target a wolf?
		if(players[targidx].roles[WolfPlayer.ROLE_WOLF] || players[targidx].roles[WolfPlayer.ROLE_WERECROW]) {
			privmsg(nick, "You may not target other wolves.");
			return;
		}
		// Add your vote.
		votes[targidx]++;
		if(players[plidx].voted >= 0) votes[players[plidx].voted]--;
		players[plidx].voted = targidx;
		// tells the wolves
		wolfmsg("\u0002" + players[plidx].getNick() + "\u0002 has selected \u0002" +
			players[targidx].getNick() + "\u0002 to be killed.");
		
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
		int plidx = getPlayer(nick, user, host);
		// Is the voter playing?
		if(plidx == -1) {
			privmsg(nick, "You aren't even playing.");
			return;
		}
		// Dead people may not vote
		if(!players[plidx].isAlive) {
			privmsg(nick, "Dead players may not vote.");
			return;
		}
		// Was this a retraction?
		if(who == null) {
			if(players[plidx].voted >= 0) {
				// retracts the vote
				votes[players[plidx].voted]--;
				players[plidx].voted = -1;
				chanmsg("\u0002" + nick + "\u0002 has retracted his/her vote.");
			} else {
				// nobody was voted for
				privmsg(nick, "You haven't voted for anybody.");
			}
			return;
		}
		
		// gets the votee
		int targidx = getPlayer(who);
		// Is the target playing?
		if(targidx == -1) {
			privmsg(nick, who + " is not playing.");
			return;
		}
		// You also cannot vote for dead people
		if(!players[targidx].isAlive) {
			privmsg(nick, "He's already dead! Leave the body in its grave.");
			return;
		}
		// removes the old vote
		if(players[plidx].voted >= 0) {
			// retracts the vote
			votes[players[plidx].voted]--;
		}
		// adds the new vote
		players[plidx].voted = targidx;
		votes[targidx]++;
		chanmsg("\u0002" + nick + "\u0002 has voted to lynch \u0002" + players[targidx].getNick() + "\u0002!");
		
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
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			// not even in the game...
			privmsg(nick, "You aren't playing.");
			return;
		}
		// Is the game already in progress?
		if(isRunning) {
			if(players[plidx].isAlive) {
				players[plidx].isAlive = false;
				// kill him
				chanmsg(formatBold(nick) + " ate toxic berries and died. It appears s/he was a " +
					formatBold(players[plidx].getDeathDisplayedRole()) + "");
				playerDeath(plidx);
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
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
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
		if(playernum < 4) {
			chanmsg("You need at least four players to begin.");
			return;
		}

		System.out.println("[CONSOLE] : Getting config....");
		// Gets the configuration
		WolfConfig wc = getConfig(playernum);
		if(wc == null) {
			chanmsg("Configuration error: " + playernum + " players is not supported.");
			System.err.println("[CONSOLE] : " + playernum + " is an unsupported player count.");
			return;
		}

		// game started; begin during night
		isRunning = true;
		isNight = true;
		
		// Welcome the players to the game & sets them alive.
		System.out.println("[CONSOLE] : Welcoming players....");
		String namelist = "";
		for (int m = 0; m < playernum; m++) {
			players[m].isAlive = true;
			players[m].addAction(gamestart); // set them all to having spoken now
			namelist = namelist + formatBold(players[m].getNick());
			if(m < playernum - 2) namelist = namelist + ", ";
			else if(m == playernum - 2) namelist = namelist + ", and ";
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
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_WOLF] = true;
				// If we have a dedicated wolf channel, invite the player.
				if(wolfChannel != null) Javawolf.wolfbot.sendInvite(players[pidx].getNick(), wolfChannel);
				wolfcount--;
			}
		}
		while(traitorcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_TRAITOR] = true;
				// If we have a dedicated wolf channel, invite the player.
				if(wolfChannel != null) Javawolf.wolfbot.sendInvite(players[pidx].getNick(), wolfChannel);
				traitorcount--;
			}
		}
		while(werecrowcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_WERECROW] = true;
				// If we have a dedicated wolf channel, invite the player.
				if(wolfChannel != null) Javawolf.wolfbot.sendInvite(players[pidx].getNick(), wolfChannel);
				werecrowcount--;
			}
		}
		while(sorcerercount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_SORCERER] = true;
				// If we have a dedicated wolf channel, invite the player.
				if(wolfChannel != null) Javawolf.wolfbot.sendInvite(players[pidx].getNick(), wolfChannel);
				sorcerercount--;
			}
		}
		// now assign the main roles
		while(seercount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_SEER] = true;
				seercount--;
			}
		}
		while(drunkcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_DRUNK] = true;
				players[pidx].isInTavern = true;
				if(tavernChannel != null) Javawolf.wolfbot.sendInvite(players[pidx].getNick(), tavernChannel);
				drunkcount--;
			}
		}
		while(harlotcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_HARLOT] = true;
				harlotcount--;
			}
		}
		while(angelcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_ANGEL] = true;
				angelcount--;
			}
		}
		while(detectivecount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_DETECTIVE] = true;
				detectivecount--;
			}
		}
		while(mediumcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_MEDIUM] = true;
				mediumcount--;
			}
		}
		// now the secondary roles
		while(cursedcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(!players[pidx].roles[WolfPlayer.ROLE_DRUNK] && !players[pidx].roles[WolfPlayer.ROLE_WOLF] &&
					!players[pidx].roles[WolfPlayer.ROLE_WERECROW]) {
				if((!allowCursedSeer && !players[pidx].roles[WolfPlayer.ROLE_SEER]) || allowCursedSeer) {
					players[pidx].roles[WolfPlayer.ROLE_CURSED] = true;
					cursedcount--;
				}
			}
		}
		while(gunnercount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countWolfRoles() == 0) {
				players[pidx].roles[WolfPlayer.ROLE_GUNNER] = true;
				gunnercount--;
				players[pidx].numBullets = (int)Math.floor(playernum / 9) + 1;
				if(players[pidx].roles[WolfPlayer.ROLE_DRUNK]) players[pidx].numBullets *= 3; // drunk gets 3x normal bullet count
			}
		}
		// now for lovers
		while(lovercount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			int pidx2 = (int)Math.floor(rand.nextDouble()*playernum);
			while(pidx == pidx2) pidx2 = (int)Math.floor(rand.nextDouble()*playernum);
			if((players[pidx].lover == -1) && (players[pidx2].lover == -1)) {
				players[pidx].lover = pidx2;
				players[pidx2].lover = pidx;
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
		// Go through the list of players, checking idle times.
		int m = 0;
		while(m < playernum) {
			// Is he even alive?
			if(!players[m].isAlive) {
				m++;
				continue;
			}
 			// Is he idle?
			if((currentTime - players[m].getLastAction()) > idleKillTime) {
				// kill the player
				chanmsg("\u0002" + players[m].getNick() + "\u0002 didn't get out of bed for a very long time. " +
					"S/he is declared dead. It appears s/he was a \u0002" + players[m].getDeathDisplayedRole() + "\u0002.");
				playerDeath(m);
			} else if(((currentTime - players[m].getLastAction()) > idleWarnTime) && !players[m].isIdleWarned) {
				// Warn the player
				chanmsg("\u0002" + players[m].getNick() + "\u0002, you have been idling for awhile. Please say " +
					"something soon or you will be declared dead.");
				players[m].isIdleWarned = true;
			}
			// Calculate when this player next needs to be checked.
			lWarn = idleWarnTime - (currentTime - players[m].getLastAction());
			lKill = idleKillTime - (currentTime - players[m].getLastAction());
			if(lWarn < 0) {
				// Has he not just been killed?
				if(lKill >= 0) {
					if(nextCheck > lKill) nextCheck = lKill;
				}
			} else {
				if(nextCheck > lWarn) nextCheck = lWarn;
			}
			// next player
			m++;
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
		if((votercount - nVotes) < nVotes) {
			// Notify players
			chanmsg("Resigned to his/her fate, " + formatBold(players[ind].getNick()) + " is led to the gallows. After death, it is discovered " +
				"that s/he was a " + formatBold(players[ind].getDeathDisplayedRole()) + ".");
			playerDeath(ind);
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
		// Check to see if all roles have acted.
		for (int m = 0; m < playernum; m++) {
			// Dead players don't count
			if(players[m].isAlive){
                if(players[m].roles[WolfPlayer.ROLE_SEER] && (players[m].seen == -1)) return false; // Seer didn't see
                if(players[m].roles[WolfPlayer.ROLE_HARLOT] && (players[m].visited == -1)) return false; // Harlot didn't visit
                if(players[m].roles[WolfPlayer.ROLE_WOLF] && (players[m].voted == -1)) return false; // Wolf didn't kill
                if(players[m].roles[WolfPlayer.ROLE_ANGEL] && (players[m].guarded == -1)) return false; // Angel hasn't guarded
                if(players[m].roles[WolfPlayer.ROLE_WERECROW] && (players[m].voted == -1)) return false; // Crow hasn't killed
                if(players[m].roles[WolfPlayer.ROLE_WERECROW] && (players[m].observed == -1)) return false; // Crow hasn't observed
                if(players[m].roles[WolfPlayer.ROLE_SORCERER] && (players[m].cursed == -1)) return false; // Sorcerer hasn't cursed
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
		
		// Is it a pair of lovers who win? The pair of lovers will not win independently if they are on the same team.
		if(votingcount == 2) {
			int[] voters = new int[2];
			int count = 0;
			// Find the voters.
			int m = 0;
			while(m < playernum) {
				if(players[m].isAlive && players[m].canVote) {
					voters[count] = m;
					count++;
				}
				m++;
			}
			// Are the voters lovers?
			if(players[voters[0]].lover == voters[1]) {
				// Are they on the opposite team? If they are on the same team, then they don't win independently of their team.
				if(((players[voters[0]].countWolfRoles() > 0) && (players[voters[1]].countWolfRoles() == 0)) ||
					((players[voters[0]].countWolfRoles() == 0) && (players[voters[1]].countWolfRoles() > 0))) {
					// Lover pair wins.
					chanmsg("Game over! The pair of lovers, \u0002" + players[voters[0]].getNick() + "\u0002 and \u0002" +
						players[voters[1]].getNick() + "\u0002, are the last people alive in the village! They disappear into the " +
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
			for (int m = 0; m < playernum; m++) {
				if(players[m].roles[WolfPlayer.ROLE_TRAITOR] || players[m].roles[WolfPlayer.ROLE_SORCERER]) {
					// change status
					players[m].roles[WolfPlayer.ROLE_WOLF] = true;
					players[m].roles[WolfPlayer.ROLE_TRAITOR] = false;
					players[m].roles[WolfPlayer.ROLE_SORCERER] = false;
					// Let them know in dramatic style :p
					privmsg(players[m].getNick(), "HOOOWWWWWLLLLLLL! You have become...a wolf! It is up to you to avenge your fallen leaders!");
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
		String msg = "";
		for (int m = 0; m < playernum; m++) {
			// append to string
			if(players[m].countAllRoles() > 0) msg = msg + formatBold(players[m].getNick()) + " was a " +
					players[m].getEndGameDisplayedRole() + ". ";
			// Also show the lovers.
			if((players[m].lover > -1) && (players[m].lover > m)) msg = msg + formatBold(players[m].getNick()) +
				" and " + formatBold(players[players[m].lover].getNick()) + " were "+formatBold("lovers")+". ";
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
		
		for (int m = 0; m < playernum; m++) {
			// only consider living wolves
			if(players[m].isAlive && players[m].countWolfRoles() > 0) {
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
		
		for (int m = 0; m < playernum; m++) {
			// only consider living wolves
			if(players[m].isAlive) {
				if(players[m].roles[WolfPlayer.ROLE_WOLF] || players[m].roles[WolfPlayer.ROLE_WERECROW]) nWolves++;
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
		
		for (int m = 0; m < playernum; m++) {
			// only consider living players
			if(players[m].isAlive && players[m].canVote) 
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
		
		for (int m = 0; m < playernum; m++) {
			// only consider living players
			if(players[m].isAlive) 
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
		votes = new int[MAX_WOLFPLAYERS];
		// PM all the players their roles
		String wolflist = "Players: ";
		String playerlist = "Players: ";
		int m = 0;
		while(m < playernum) {
			// Update wolf list
			if(players[m].roles[WolfPlayer.ROLE_WOLF]) wolflist = wolflist + formatBold(players[m].getNick()) + " (wolf)";
			else if(players[m].roles[WolfPlayer.ROLE_TRAITOR]) wolflist = wolflist + formatBold(players[m].getNick()) + " (traitor)";
			else if(players[m].roles[WolfPlayer.ROLE_WERECROW]) wolflist = wolflist + formatBold(players[m].getNick()) + " (werecrow)";
			else if(players[m].roles[WolfPlayer.ROLE_SORCERER]) wolflist = wolflist + formatBold(players[m].getNick()) + " (sorcerer)";
			else wolflist = wolflist + formatBold(players[m].getNick());
			// Update player list
			playerlist = playerlist + formatBold(players[m].getNick());
			if(m < (playernum - 2)) {
				// add a comma
				wolflist = wolflist + ", ";
				playerlist = playerlist + ", ";
			} else if(m == (playernum - 2)) {
				// second-to-last player added, add an "and"
				wolflist = wolflist + ", and ";
				playerlist = playerlist + ", and ";
			} else if(m == (playernum - 1)) {
				// last player, no need for comma
			}
			// Next player
			m++;
		}
		m = 0;
		while(m < playernum) {
			// ignore dead people
			if(players[m].isAlive) {
				sendPlayerRole(m, playerlist, wolflist);
			}
			// Next player
			m++;
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
	private void sendPlayerRole(int m, String playerlist, String wolflist) {
		// evil roles
		if(players[m].roles[WolfPlayer.ROLE_WOLF]) {
			privmsg(players[m].getNick(), "You are a "+formatBold("wolf")+". Use \"" + 
				Javawolf.cmdchar + "kill <name>\" to kill a villager once per night.");
			privmsg(players[m].getNick(), wolflist);
		}
		if(players[m].roles[WolfPlayer.ROLE_TRAITOR]) {
			privmsg(players[m].getNick(), "You are a "+formatBold("traitor")+". You are on the side of " +
				"the wolves, except that you are, for all intents and purposes, a villager. Only detectives can identify you. " +
				"If all the wolves die, you will become a wolf yourself.");
			privmsg(players[m].getNick(), wolflist);
		}
		if(players[m].roles[WolfPlayer.ROLE_WERECROW]) {
			privmsg(players[m].getNick(), "You are a \u0002werecrow\u0002. Use \"" + Javawolf.cmdchar + "kill <name>\" to kill a villager " +
				"once per night. You may also observe a player to see whether s/he stays in bed all night with \"" +
				Javawolf.cmdchar + "observe <name>\".");
			privmsg(players[m].getNick(), wolflist);
		}
		if(players[m].roles[WolfPlayer.ROLE_SORCERER]) {
			privmsg(players[m].getNick(), "You are a \u0002sorcerer\u0002. Use \"" + Javawolf.cmdchar + "curse <name>\" to curse a villager " +
				"each night. The seer will then see the cursed villager as a wolf! If all the wolves die, you will become a wolf yourself.");
			privmsg(players[m].getNick(), wolflist);
		}
		// good roles
		if(players[m].roles[WolfPlayer.ROLE_SEER]) { 
			privmsg(players[m].getNick(), "You are a \u0002seer\u0002. Use \"" + 
				Javawolf.cmdchar + "see <name>\" to see the role of a villager once per night. Be warned that traitors will " +
				"still appear as villagers, and cursed villagers will appear to be wolves. Use your judgment.");
			privmsg(players[m].getNick(), playerlist);
		}
		if(players[m].roles[WolfPlayer.ROLE_DRUNK]) {
			privmsg(players[m].getNick(), "You have been drinking too much! You are a \u0002village drunk\u0002! " +
				"You can bring other players into the village tavern. Use \"" + Javawolf.cmdchar + "invite <name>\" to bring someone in, " +
				"and use \"" + Javawolf.cmdchar + "eject <name>\" to throw someone out of the tavern.");
		}
		if(players[m].roles[WolfPlayer.ROLE_HARLOT]) {
			privmsg(players[m].getNick(), "You are a \u0002harlot\u0002. You may visit any player during the night. " +
				"If you visit a wolf or the victim of the wolves, you will die. If you are attacked while you are out visiting, " +
				"you will survive. Use \"" + Javawolf.cmdchar + "visit <name>\" to visit a player.");
			privmsg(players[m].getNick(), playerlist);
		}
		if(players[m].roles[WolfPlayer.ROLE_ANGEL]) {
			privmsg(players[m].getNick(), "You are a \u0002guardian angel\u0002. You may choose one player to guard " +
				"per night. If you guard the victim, s/he will likely live. If you guard a wolf, you may die. Use \"" + Javawolf.cmdchar +
				"guard <name>\" to guard someone.");
			privmsg(players[m].getNick(), playerlist);
		}
		if(players[m].roles[WolfPlayer.ROLE_DETECTIVE]) {
			privmsg(players[m].getNick(), "You are a \u0002detective\u0002. You act during the day, and you can even " +
				"identify traitors. Use \"" + Javawolf.cmdchar + "id <name>\" to identify someone. Be careful when iding, because " +
				"your identity might be revealed to the wolves.");
			privmsg(players[m].getNick(), playerlist);
		}
		if(players[m].roles[WolfPlayer.ROLE_MEDIUM]) {
			privmsg(players[m].getNick(), "You are a \u0002medium\u0002. Once per day, you can choose to raise a " +
				"player from the dead to consult with him or her. However, the spirit will be unable to use any powers. Use \"" +
				Javawolf.cmdchar + "raise <name>\" to raise a player.");
			privmsg(players[m].getNick(), playerlist);
		}
		if(players[m].roles[WolfPlayer.ROLE_GUNNER]) {
			privmsg(players[m].getNick(), "You hold a gun that shoots special silver bullets. If you shoot a wolf, s/he will die. " +
				"If you shoot a villager, s/he will most likely live. Use \"" + Javawolf.cmdchar + "shoot <name>\" to shoot.");
			privmsg(players[m].getNick(), playerlist);
			privmsg(players[m].getNick(), "You have " + players[m].numBullets + " bullets remaining.");
		}
		if(players[m].lover != -1) {
			privmsg(players[m].getNick(), "You have a lover, \u0002" + players[players[m].lover].getNick() + "\u0002, who is a \u0002" +
				players[players[m].lover].getDeathDisplayedRole() + "\u0002. If your lover dies, you will die too. If you two are the last " +
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
	
	/**
	 * Ends the night
	 */
	private void endNight() {
		System.out.println("[CONSOLE] : Ending the night.");
		timer.cancel();
		int m = 0;
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
		while(m < playernum) {
			// Update wolf list
			if(players[m].roles[WolfPlayer.ROLE_WOLF]) wolflist = wolflist + "\u0002" + players[m].getNick() + "\u0002 (wolf)";
			else if(players[m].roles[WolfPlayer.ROLE_TRAITOR]) wolflist = wolflist + "\u0002" + players[m].getNick() + "\u0002 (traitor)";
			else if(players[m].roles[WolfPlayer.ROLE_WERECROW]) wolflist = wolflist + "\u0002" + players[m].getNick() + "\u0002 (werecrow)";
			else if(players[m].roles[WolfPlayer.ROLE_SORCERER]) wolflist = wolflist + "\u0002" + players[m].getNick() + "\u0002 (sorcerer)";
			else wolflist = wolflist + "\u0002" + players[m].getNick() + "\u0002";
			// Update player list
			playerlist = playerlist + "\u0002" + players[m].getNick() + "\u0002";
			if(m < (playernum - 2)) {
				// add a comma
				wolflist = wolflist + ", ";
				playerlist = playerlist + ", ";
			} else if(m == (playernum - 2)) {
				// second-to-last player added, add an "and"
				wolflist = wolflist + ", and ";
				playerlist = playerlist + ", and ";
			} else if(m == (playernum - 1)) {
				// last player, no need for comma
			}
			// Next player
			m++;
		}
		// Tally up the votes and announce the kill
		int[] voteresults = tallyvotes();
		int ind = voteresults[1];
		if(ind == -1) {
			// LOL
			chanmsg("The wolves were unable to decide who to kill last night. As a result, all villagers have survived.");
		} else {
			// Wolves attack someone
			// Is the target protected by a guardian angel?
			boolean successfullyguarded = false;
			m = 0;
			while(m < playernum) {
				// Is the player a guardian angel?
				if(players[m].roles[WolfPlayer.ROLE_ANGEL] && players[m].isAlive) {
					// Did the player guard the victim?
					if(players[m].guarded == ind) {
						// Does the angel die in the attempt?
						if(rand.nextDouble() < dieguardingvictimpct) {
							chanmsg("\u0002" + players[m].getNick() + "\u0002, a \u0002" + players[m].getDeathDisplayedRole() + 
									"\u0002, tried to defend the victim, but died in the attempt.");
							playerDeath(m);
						} else if(rand.nextDouble() < guardedvictimdiepct) {
							// Angel failed in the defense.
							chanmsg("A guardian angel tried to protect \u0002" + players[ind].getNick() + 
								"\u0002, but s/he was unable to stop the attacks of the wolves and was forced to retreat.");
						} else {
							// successful guarding
							successfullyguarded = true;
							// Does the angel become a wolf though?
							if(rand.nextDouble() < infectguardingvictimpct) {
								privmsg(players[m].getNick(), "While defending the victim, you were slightly injured in the fray. " +
									"You feel a change coming over you...you have become a \u0002wolf\u0002!");
								privmsg(players[m].getNick(), wolflist);
							}
							// Does the victim turn into a wolf? Obviously, wolfteam won't change.
							if(players[ind].countWolfRoles() == 0) {
								if(rand.nextDouble() < guardedvictiminfectpct) {
									privmsg(players[ind].getNick(), "Although a guardian angel saved you, you were slightly injured in the fray. " +
										"You feel a change coming over you...you have become a \u0002wolf\u0002!");
									privmsg(players[ind].getNick(), wolflist);
								}
							}
						}
					}
				}
				m++;
			}
			if(!successfullyguarded) {
				// Is the target a harlot who visited someone?
				if(players[ind].roles[WolfPlayer.ROLE_HARLOT] && (players[ind].visited >= 0) && (players[ind].visited != ind)) {
					// Harlot visited someone else
					chanmsg("The wolves' selected victim was a harlot, but she wasn't home.");
				} else {
					chanmsg("The dead body of \u0002" + players[ind].getNick() + "\u0002, a \u0002" + players[ind].getDeathDisplayedRole() + 
						"\u0002, is found. Those remaining mourn his/her death.");
					playerDeath(ind);
					// Wolves might find a gun
					if(players[ind].roles[WolfPlayer.ROLE_GUNNER] && (players[ind].numBullets > 0)) {
						if(rand.nextDouble() < wolffindgunpct) {
							// randomly pick a wolfteam member to give the gun to
							int[] wolfidxs = new int[MAX_WOLFPLAYERS];
							int nWolves = 0;
							m = 0;
							while(m < playernum) {
								if(players[m].countWolfRoles() > 0) {
									wolfidxs[nWolves] = m;
									nWolves++;
								}
								m++;
							}
							int pickedwolf = wolfidxs[(int)Math.floor(rand.nextDouble()*nWolves)];
							players[pickedwolf].roles[WolfPlayer.ROLE_GUNNER] = true;
							players[pickedwolf].numBullets = players[ind].numBullets; // give him however many bullets remained
							// notify him
							privmsg(players[pickedwolf].getNick(), "You find \u0002" + players[ind].getNick() +
								"'s\u0002 gun with " + players[ind].numBullets + " bullets! Use \"" + Javawolf.cmdchar + 
								"shoot <name>\" to shoot a player. You will deliberately miss other wolves.");
						}
					}
				}
				// Did any harlots visit the victim?
				m = 0;
				while(m < playernum) {
					if(players[m].roles[WolfPlayer.ROLE_HARLOT] && players[m].isAlive) {
						if(players[m].visited == ind) {
							// Harlot visited victim and died
							chanmsg(players[m].getNick() + ", a harlot, made the unfortunate mistake of visiting the victim last night " +
								"and is now dead.");
							playerDeath(m);
						}
					}
					// next player
					m++;
				}
			} else {
				chanmsg("\u0002" + players[ind].getNick() + "\u0002 was attacked by the wolves last night, but luckily, a " +
					"guardian angel protected him/her.");
			}
		}
		// Did any harlots visit wolves?
		m = 0;
		while(m < playernum) {
			if(players[m].roles[WolfPlayer.ROLE_HARLOT] && players[m].isAlive && (players[m].visited >= 0) && !(players[m].visited == m)) {
				if((players[players[m].visited].roles[WolfPlayer.ROLE_WOLF] || players[players[m].visited].roles[WolfPlayer.ROLE_WERECROW]) &&
						!(players[m].visited == players[m].lover)) {
					// Harlot visited wolf and died
					chanmsg("\u0002" + players[m].getNick() + "\u0002, a \u0002harlot\u0002, made the unfortunate mistake of " +
						"visiting a wolf last night and is now dead.");
					playerDeath(m);
				}
			}
			// next player
			m++;
		}
		// Did any guardian angels guard wolves?
		m = 0;
		while(m < playernum) {
			// Is the player a guardian angel?
			if(players[m].roles[WolfPlayer.ROLE_ANGEL] && players[m].isAlive) {
				if(players[players[m].guarded].roles[WolfPlayer.ROLE_WOLF] || players[players[m].guarded].roles[WolfPlayer.ROLE_WERECROW]) {
					// Guarded a wolf
					if(rand.nextDouble() < dieguardingwolfpct) {
						chanmsg("\u0002" + players[m].getNick() + "\u0002, a \u0002guardian angel\u0002, made the unfortunate mistake of " +
							"guarding a wolf last night, tried to escape, but failed. S/he is found dead.");
						playerDeath(m);
					}
				}
			}
			m++;
		}
		// Werecrows' observations return now
		for (m = 0; m < playernum; m++) {
			if(players[m].isAlive) {
				if(players[m].roles[WolfPlayer.ROLE_WERECROW]) {
					if(players[m].observed != -1) {
						if(players[players[m].observed].isAlive) {
							// Did the target stay in bed?
							if((players[players[m].observed].roles[WolfPlayer.ROLE_SEER] && (players[players[m].observed].seen != -1)) ||
								(players[players[m].observed].roles[WolfPlayer.ROLE_HARLOT] && (players[players[m].observed].visited != -1)) ||
								(players[players[m].observed].roles[WolfPlayer.ROLE_ANGEL] && (players[players[m].observed].guarded != -1))) {
								privmsg(players[m].getNick(), "\u0002" + players[players[m].observed].getNick() + "\u0002 left his/her bed last night.");
							} else {
								privmsg(players[m].getNick(), "\u0002" + players[players[m].observed].getNick() + "\u0002 remained in bed all night.");
							}
							// Did anyone "visit" the target?
							int n = 0;
							while(n < playernum) {
								if(players[n].isAlive) {
									if(players[n].roles[WolfPlayer.ROLE_ANGEL]) {
										if(players[n].guarded == players[m].observed)
											privmsg(players[m].getNick(), "\u0002" + players[n].getNick() +
												"\u0002, a \u0002guardian angel\u0002, guarded \u0002" + 
												players[players[m].observed].getNick() + "\u0002 last night.");
									} else if(players[n].roles[WolfPlayer.ROLE_SEER]) {
										if(players[n].seen == players[m].observed)
											privmsg(players[m].getNick(), "\u0002" + players[n].getNick() +
												"\u0002, a \u0002seer\u0002, saw \u0002" + 
												players[players[m].observed].getNick() + "\u0002 last night.");
									} else if(players[n].roles[WolfPlayer.ROLE_HARLOT]) {
										if(players[n].visited == players[m].observed)
											privmsg(players[m].getNick(), "\u0002" + players[n].getNick() +
												"\u0002, a \u0002harlot\u0002, visited \u0002" + 
												players[players[m].observed].getNick() + "\u0002 last night.");
									}
								}
								n++;
							}
						}
					}
				}
			}
		}
		// Reset the players' actions and votes
		votes = new int[MAX_WOLFPLAYERS];
		for (m = 0; m < playernum; m++) {
			players[m].resetActions();
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
			secs = secs % 60;
			chanmsg("Day lasted " + formatBold(mins + ":" + (secs < 10 ? "0" + secs : secs)) + ".");
			// Kill any raised spirits again
			for (int m = 0; m < playernum; m++) {
				if(players[m].roles[WolfPlayer.ROLE_MEDIUM] && (players[m].raised != -1)) {
					chanmsg("As dusk falls, the spirit of " + formatBold(players[players[m].raised].getNick()) +
                            " returns to rest.");
					devoice(players[players[m].raised].getNick());
				}
				// also reset actions taken
				players[m].resetActions();
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
		
		for (int m = 0; m < playernum; m++) {
			if(votes[m] > nVotes) {
				nVotes = votes[m];
				indVoted = m;
			} else if(votes[m] == nVotes) {
				indVoted = -1; // tie
			}
		}
		
		// return the values
		int[] retvals = new int[2];
		retvals[0] = nVotes;
		retvals[1] = indVoted;
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
		// TODO : Implement this command
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
		int plidx = getPlayer(who);
		if(plidx == -1) {
			// not even in the game...
			privmsg(nick, formatBold(who) + " isn't playing.");
			return;
		}
		// Is the game already in progress?
		if(isRunning) {
			players[plidx].isAlive = false;
			// kill him
			chanmsg("\u0002" + players[plidx].getNick() + "\u0002 was thrown out of the village and devoured by wild animals. " +
				"It appears s/he was a \u0002" + players[plidx].getDeathDisplayedRole() + "\u0002.");
			playerDeath(plidx);
			// Does game end?
			if(checkForEnding()) return;
			if(isNight) {
				if(checkEndNight()) endNight(); // Does night end?
			} else {
				checkForLynching(); // Does day end?
			}
		} else {
			String killedNick = players[plidx].getNick();
			if(rmvPlayer(players[plidx].getNick(), players[plidx].getUser(), players[plidx].getHost())) {
				chanmsg("\u0002" + killedNick + "\u0002 was removed from the game.");
				devoice(killedNick);
				System.out.println("[CONSOLE] : " + killedNick + " was ejected from the game.");
			} else {
				privmsg(nick, "You cannot kick \u0002" + killedNick + "\u0002 from the game at this time.");
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
		int m = 0;
		int count = 0;
		while(m < playernum) {
			if(isRunning && players[m].isAlive) {
				devoicelist = devoicelist + players[m].getNick() + " ";
				count++;
			} else if(!isRunning) {
				devoicelist = devoicelist + players[m].getNick() + " ";
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
				if((players[m].countWolfRoles() > 0) && players[m].isAlive) {
					kickplayer(wolfChannel, players[m].getNick(), "Game ended");
				}
			}
			if(tavernChannel != null) {
				if((players[m].countWolfRoles() > 0) && players[m].isAlive && players[m].isInTavern) {
					kickplayer(tavernChannel, players[m].getNick(), "Game ended");
				}
			}
			// next player
			m++;
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
		players = new WolfPlayer[MAX_WOLFPLAYERS];
		votes = new int[MAX_WOLFPLAYERS];
		playernum = 0;
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
	private void playerDeath(int idx) {
		// sanity check
		if(!isRunning) {
			System.err.println("[CONSOLE] : ERROR : <Playerdeath> called while game is not running!");
			return;
		}
		// If in wolf channel, kick him out.
		if(wolfChannel != null && players[idx].countWolfRoles() > 0) {
            kickplayer(wolfChannel, players[idx].getNick(), "You have died");
		}
		if(tavernChannel != null && players[idx].isInTavern) {
            kickplayer(tavernChannel, players[idx].getNick(), "You have died");
		}
		// Now dead
		players[idx].isAlive = false;
		// Remove vote
		if(players[idx].voted != -1) votes[players[idx].voted]--;
		// Remove all actions
		players[idx].resetActions();
		// Devoice player
		devoice(players[idx].getNick());
		// Kills the lover
		if(players[idx].lover != -1) {
			// Prevent recursively calling <playerDeath>
			if(players[players[idx].lover].isAlive) {
				chanmsg(formatBold(players[players[idx].lover].getNick()) + ", a " + 
                        formatBold(players[players[idx].lover].getDeathDisplayedRole()) +
					" was " + players[idx].getNick() + "'s lover and is dying of a broken heart!");
				// return whether the game ends following the lover's death (preventing multiple endings)
				playerDeath(players[idx].lover);
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
	private int getPlayer(String nick, String user, String host) {
		for (int ctr = 0; ctr < playernum; ctr++) {
			if (players[ctr].identmatch(nick, user, host)) {
				return ctr; // found him
			}
		}
		// no such player
		return -1;
	}
	
	/**
	 * Gets the player index who matches <nick>
	 * 
	 * @param nick
	 * @return
	 */
	private int getPlayer(String nick) {
		int matched_player = -1;
		for (int ctr = 0; ctr < playernum; ctr++) {
			if(players[ctr].getNick().contentEquals(nick)) {
				return ctr; // exact match; return immediately
			} else if(players[ctr].getNick().toLowerCase().startsWith(nick.toLowerCase())) {
				if(matched_player == -1) matched_player = ctr; // partial match
				else matched_player = -2; // multiple matches
			}
		}
		// return the player
		if(matched_player == -2) return -1;
		else return matched_player;
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
		int plidx = getPlayer(oldNick, user, host);
		if(plidx != -1) {
			players[plidx].setNick(newNick);
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
		int plidx = getPlayer(nick, user, host);
		if(plidx != -1) {
			if(isRunning) {
				// Game is running, kill the player
				if(players[plidx].isAlive) {
					chanmsg(Colors.BOLD + players[plidx].getNick() + Colors.BOLD+
                            " died of an unknown disease. It appears s/he was a " +
						Colors.BOLD+ players[plidx].getDeathDisplayedRole() + Colors.BOLD+".");
					playerDeath(plidx);
				}
			} else {
				// No game; delete said player
				chanmsg(formatBold(players[plidx].getNick()) + " left the game.");
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
		int plidx = getPlayer(nick);
		if(plidx != -1) {
			if(isRunning) {
				if(players[plidx].isAlive) {
					// Game is running, kill the player
					chanmsg(formatBold(players[plidx].getNick()) + " was kicked off a cliff. It appears s/he was a " +
						formatBold(players[plidx].getDeathDisplayedRole()) + ".");
					playerDeath(plidx);
				}
			} else {
				// No game; delete said player.
				chanmsg(formatBold(players[plidx].getNick()) + " was kicked from the game.");
				rmvPlayer(nick, players[plidx].getUser(), players[plidx].getHost());
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
		int plidx = getPlayer(nick, user, host);
        if( plidx != -1 && players[plidx].addAction(System.currentTimeMillis())) {
            // Player is flooding. Kick him out of the channel.
            System.out.println("[CONSOLE] : Kicking " + nick + " for flooding.");
            kickplayer(mainChannel, players[plidx].getNick(), "Channel flooding is not acceptable.");
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
		if(getPlayer(nick, user, host) != -1) 
            return false;
		// only add the player if enough slots
		if(playernum < MAX_WOLFPLAYERS) {
			players[playernum++] = new WolfPlayer(nick, user, host, System.currentTimeMillis());
			return true;
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
		int plidx = getPlayer(nick, user, host);
		if(plidx >= 0) {
			// rotate them down
			for (int m = plidx; m < playernum-1; m++) {
				players[m] = players[m+1];
			}
			players[playernum-1] = null; // delete last entry
			playernum--;
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
		int plidx = getPlayer(nick, user, host);
		if (plidx == -1) 
            return; // no such user
		// Don't let dead people chat
		if (!players[plidx].isAlive) 
            return;
		// Is it a wolf team or tavern message?
		boolean isWolf = players[plidx].countWolfRoles() > 0;
		boolean isTavern = players[plidx].isInTavern;
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
		// Wolf team has a dedicated channel.
		if(wolfChannel != null) return;
		for (int m = 0; m < playernum; m++) {
			if(players[m].isAlive) {
				if(players[m].countWolfRoles() > 0) privmsg(players[m].getNick(), msg);
			}
		}
	}
	
	/**
	 * Broadcasts messages to the players in the village tavern
	 * 
	 * @param msg
	 */
	private void tavernmsg(String msg) {
		// Tavern has a dedicated channel.
		if (tavernChannel != null) return;
		for (int m = 0; m < playernum; m++) {
			if(players[m].isInTavern && players[m].isAlive) privmsg(players[m].getNick(), msg);
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
		int m = 0;
		while(m < nCfgs) {
			if(configs[m] == null) {
				// some bug
				System.err.println("[CONSOLE] : ERROR : Config #" + m + " is null!");
				return null;
			}
			if((num <= configs[m].high) && (num >= configs[m].low)) {
				return configs[m];
			}
			m++;
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

