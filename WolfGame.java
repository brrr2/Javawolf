/*
 * Import classes
 */

import java.sql.Time;
import java.util.Random;
import java.lang.*;
import jerklib.Channel;

/**
 * @author Reaper Eternal
 *
 */
public class WolfGame {
	// Channel and server associated with this instance of the game
	private String assocChannel = null;
	private String assocServer = null;
	// Player list
	private WolfPlayer[] players = null;
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
	private double dieguardingwolfpct = .5;
	// Chance the GA will die if guarding a victim.
	private double dieguardingvictimpct = .25;
	// Chance the GA will become a wolf if guarding the victim.
	private double infectguardingvictimpct = 0;
	// Chance the victim will die if guarded.
	private double guardedvictimdiepct = .25;
	// Chance the victim will become a wolf if guarded.
	private double guardedvictiminfectpct = 0;
	
	/* Detective constants */
	// Chance the detective will drop his papers.
	private double detectivefumblepct = .4;
	
	/* Gunner constants */
	// Chance the gunner will miss
	private double gunnermisspct = 1 / 5;
	// Chance the gunner will explode
	private double gunnerexplodepct = 1 / 7;
	// Chance gunner will kill the target
	private double gunnerheadshotpct = 1 / 5;
	// Chance wolf will find a gun when killing the gunner
	private double wolffindgun = 1 / 3;
	
	// Time of start of day/night
	private long starttime = 0;
	
	/**
	 * Creates the game
	 * 
	 * @param chan : Channel associated with this game
	 */
	public WolfGame(String chan, String server) {
		// sets the associated channel and server
		assocChannel = chan;
		assocServer = server;
		// creates the player list
		players = new WolfPlayer[MAX_WOLFPLAYERS];
		votes = new int[MAX_WOLFPLAYERS];
	}
	
	/**
	 * Parses commands to the game
	 * 
	 * @param cmd
	 */
	public void parseCommand(String cmd, String[] args, String nick, String user, String host) {
		// lowercase
		cmd = cmd.toLowerCase();
		
		if(cmd.compareTo("join") == 0) {
			// Joins the game
			join(nick, user, host);
		} else if((cmd.compareTo("lynch") == 0) || (cmd.compareTo("vote") == 0)) {
			// Used for lynching a player
			if(args == null) return;
			if(args.length < 1) return;
			lynch(args[1], nick, user, host);
		} else if(cmd.compareTo("retract") == 0) {
			// Retracts your vote
			lynch(null, nick, user, host);
		} else if((cmd.compareTo("leave") == 0) || (cmd.compareTo("quit") == 0)) {
			// Leaves the game
			leave(nick, user, host);
		} else if(cmd.compareTo("start") == 0) {
			// Starts the game
			startgame(nick, user, host);
		} else if(cmd.compareTo("kill") == 0) {
			// Used by wolves to kill players
			if(args == null) return;
			if(args.length < 1) return;
			kill(args[1], nick, user, host);
		} else if(cmd.compareTo("see") == 0) {
			// Used by seers to see players
			if(args == null) return;
			if(args.length < 1) return;
			see(args[1], nick, user, host);
		} else if(cmd.compareTo("visit") == 0) {
			// Used by seers to see players
			if(args == null) return;
			if(args.length < 1) return;
			visit(args[1], nick, user, host);
		} else if(cmd.compareTo("shoot") == 0) {
			// Used by gunners to shoot players
			if(args == null) return;
			if(args.length < 1) return;
			shoot(args[1], nick, user, host);
		} else if(cmd.compareTo("id") == 0) {
			// Used by detectives to id players
			if(args == null) return;
			if(args.length < 1) return;
			id(args[1], nick, user, host);
		} else if(cmd.compareTo("stats") == 0) {
			// Who is left in the game?
			stats(nick, user, host);
		} else {
			// Allows the wolf team to chat privately
			int plidx = getPlayer(nick, user, host);
			if(plidx != -1) {
				// Don't let dead people chat
				if(players[plidx].isAlive) {
					if(players[plidx].isWolf || players[plidx].isTraitor || players[plidx].isWerecrow) {
						String s = "";
						int m = 0;
						while(m < args.length) {
							s = s + " " + args[m]; // concatenate
							m++;
						}
						wolfmsg(nick + " says: \"" + s + "\"");
					}
				}
			}
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
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx != -1) {
			// Is the player a detective?
			if(players[plidx].isDetective) {
				// Is it day?
				if(!isNight) {
					// Has the player not yet ided anyone?
					if(players[plidx].ided == -1) {
						int targidx = getPlayer(who);
						// Is the target playing?
						if(targidx == -1) {
							privmsg(nick, who + " is not playing.");
							return;
						}
						// Is the target alive? 
						if(players[targidx].isAlive) {
							// id his role
							privmsg(nick, "The results of your investigation return: \u0002" + players[targidx].getNick() +
								"\u0002 is a \u0002" + players[targidx].getDisplayedRole() + "\u0002!");
							players[plidx].ided = targidx;
							// drop papers?
							Random rand = new Random();
							if(rand.nextDouble() < detectivefumblepct) {
								// notify the wolves of the detective
								wolfmsg("\u0002" + players[plidx].getNick() + "\u0002 drops a paper revealing s/he is a \u0002detective\u0002!");
							}
						} else {
							privmsg(nick, players[targidx].getNick() + " is already dead.");
						}
					} else {
						privmsg(nick, "You have already identified someone today.");
					}
				} else {
					privmsg(nick, "You may only identify players during the day.");
				}
			} else {
				privmsg(nick, "Only detectives can id other players.");
			}
		} else {
			privmsg(nick, "You aren't even playing.");
		}
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
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
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx != -1) {
			// Is the player a seer?
			if(players[plidx].isGunner) {
				// Is it day?
				if(!isNight) {
					int targidx = getPlayer(who);
					// Is the target playing?
					if(targidx == -1) {
						privmsg(nick, who + " is not playing.");
						return;
					}
					// Is the target alive? 
					if(players[targidx].isAlive) {
						// Do you have any bullets left?
						if(players[plidx].numBullets > 0) {
							chanmsg("\u0002" + players[plidx].getNick() + "\u0002 raises his/her gun and fires at \u0002" +
								players[targidx].getNick() + "\u0002!");
							players[plidx].numBullets--; // Uses a bullet.
							Random rand = new Random();
							if(rand.nextDouble() < gunnerexplodepct) {
								// Boom! You lose.
								players[plidx].isAlive = false;
								chanmsg("\u0002" + players[plidx].getNick() + "\u0002 should have cleaned his/her gun better. " +
									"The gun explodes and kills him/her!");
								chanmsg("It appears s/he was a \u0002" + players[plidx].getDisplayedRole() + "\u0002.");
								devoice(nick);
								if(checkForEnding()) return; // Oops. Game over. Shouldn't have blown up.
								else checkForLynching(); // Does a lynching occur now?
							} else {
								// Wolf teammates will deliberately miss other wolves.
								if((players[plidx].isWolf || players[plidx].isTraitor || players[plidx].isWerecrow) &&
									(players[targidx].isWolf || players[targidx].isWerecrow)) {
									chanmsg("\u0002" + players[plidx].getNick() + "\u0002 is a lousy shot! S/he missed!");
								} else {
									// Missed target?
									if(rand.nextDouble() < gunnermisspct) {
										chanmsg("\u0002" + players[plidx].getNick() + "\u0002 is a lousy shot! S/he missed!");
									} else {
										// We hit the target.
										// Is the shot person a wolf?
										if(players[targidx].isWolf || players[targidx].isWerecrow) {
											players[targidx].isAlive = false;
											chanmsg("\u0002" + players[targidx].getNick() + "\u0002 is a \u0002" + 
												players[targidx].getDisplayedRole() + "\u0002 and is dying from the silver bullet!");
											devoice(players[targidx].getNick());
										} else {
											// Was it a headshot?
											if(rand.nextDouble() < gunnerheadshotpct) {
												players[targidx].isAlive = false;
												chanmsg("\u0002" + players[targidx].getNick() + "\u0002 was not a wolf but was accidentally " +
													"fatally injured! It appears that s/he was a \u0002" + players[targidx].getDisplayedRole() +
													"\u0002.");
												devoice(players[targidx].getNick());
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
								}
							}
						} else {
							privmsg(nick, "You have no more bullets remaining.");
						}
					} else {
						privmsg(nick, players[targidx].getNick() + " is already dead.");
					}
				} else {
					privmsg(nick, "You can only shoot during the day.");
				}
			} else {
				privmsg(nick, "You don't have a gun.");
			}
		} else {
			privmsg(nick, "You aren't even playing.");
		}
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
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
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx != -1) {
			// Is the player alive?
			if(players[plidx].isAlive) {
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
				int livingcount = 0; // total living players
				// count them
				int m = 0;
				while(m < playernum) {
					if(players[m].isAlive) {
						if(players[m].isSeer) seercount++;
						if(players[m].isDrunk) drunkcount++;
						if(players[m].isAngel) angelcount++;
						if(players[m].isHarlot) harlotcount++;
						if(players[m].isDetective) detectivecount++;
						if(players[m].isGunner) gunnercount++;
						if(players[m].isWolf) wolfcount++;
						if(players[m].isTraitor) traitorcount++;
						if(players[m].isWerecrow) werecrowcount++;
						livingcount++;
					}
					m++;
				}
				// display results
				String str = "There are " + livingcount + " villagers remaining.";
				if(wolfcount == 1) str = str + " There is a wolf.";
				else if(wolfcount > 1) str = str + " There are " + wolfcount + " wolves.";
				if(traitorcount == 1) str = str + " There is a traitor.";
				else if(traitorcount > 1) str = str + " There are " + traitorcount + " traitors.";
				if(werecrowcount == 1) str = str + " There is a werecrow.";
				else if(werecrowcount > 1) str = str + " There are " + werecrowcount + " werecrows.";
				if(seercount == 1) str = str + " There is a seer.";
				else if(seercount > 1) str = str + " There are " + seercount + " seers.";
				if(drunkcount == 1) str = str + " There is a village drunk.";
				else if(drunkcount > 1) str = str + " There are " + drunkcount + " village drunks.";
				if(harlotcount == 1) str = str + " There is a  harlot.";
				else if(harlotcount > 1) str = str + " There are " + harlotcount + " harlots.";
				if(angelcount == 1) str = str + " There is a guardian angel.";
				else if(angelcount > 1) str = str + " There are " + angelcount + " guardian angels.";
				if(detectivecount == 1) str = str + " There is a detective.";
				else if(detectivecount > 1) str = str + " There are " + detectivecount + " detectives.";
				if(gunnercount == 1) str = str + " There is a gunner.";
				else if(gunnercount > 1) str = str + " There are " + gunnercount + " gunners.";
				// send to channel
				chanmsg(str);
			} else {
				privmsg(nick, "You have died already and thus cannot use the stats command.");
			}
		} else {
			privmsg(nick, "You aren't even playing.");
		}
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
	}
	
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
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx != -1) {
			// Is the player a harlot?
			if(players[plidx].isHarlot) {
				// Is it night?
				if(isNight) {
					// Has the player not yet visited?
					if(players[plidx].visited == -1) {
						int targidx = getPlayer(who);
						// Is the target playing?
						if(targidx == -1) {
							privmsg(nick, who + " is not playing.");
							return;
						}
						// Visiting yourself?
						if(targidx == plidx) {
							privmsg(nick, "You decide to stay home for the night.");
							players[plidx].visited = plidx;
							return;
						}
						// Is the target alive? 
						if(players[targidx].isAlive) {
							// Notify both players
							players[plidx].visited = targidx;
							privmsg(players[plidx].getNick(), "You are spending the night with \u0002" + players[targidx].getNick() + 
								"\u0002. Have a good time!");
							privmsg(players[targidx].getNick(), "\u0002" + players[plidx].getNick() + "\u0002, a \u0002harlot\u0002, " +
								"has come to spend the night with you. Have a good time!");
						} else {
							privmsg(nick, "Eww! " + players[targidx].getNick() + " is already dead.");
						}
					} else {
						privmsg(nick, "You have already visited " + players[players[plidx].visited].getNick() + " tonight.");
					}
				} else {
					privmsg(nick, "You may only visit other players during the night.");
				}
			} else {
				privmsg(nick, "Only harlots can visit other players.");
			}
		} else {
			privmsg(nick, "You aren't even playing.");
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
		// Is the game even running?
		if(!isRunning) {
			privmsg(nick, "No game is running.");
			return;
		}
		
		// Is the player even playing?
		int plidx = getPlayer(nick, user, host);
		if(plidx != -1) {
			// Is the player a seer?
			if(players[plidx].isSeer) {
				// Is it night?
				if(isNight) {
					// Has the player not yet seen?
					if(players[plidx].seen == -1) {
						int targidx = getPlayer(who);
						// Is the target playing?
						if(targidx == -1) {
							privmsg(nick, who + " is not playing.");
							return;
						}
						// Is the target alive? 
						if(players[targidx].isAlive) {
							// Is the target cursed?
							if(players[targidx].isCursed) {
								// Cursed check comes before traitor check because cursed traitors can happen. Sucks for traitor.
								privmsg(nick, "You have a vision; in this vision you see that \u0002" + players[targidx].getNick() +
									"\u0002 is a \u0002wolf\u0002!");
								players[plidx].seen = targidx;
							} else if(players[targidx].isTraitor) {
								privmsg(nick, "You have a vision; in this vision you see that \u0002" + players[targidx].getNick() +
									"\u0002 is a \u0002villager\u0002!");
								players[plidx].seen = targidx;
							} else {
								privmsg(nick, "You have a vision; in this vision you see that \u0002" + players[targidx].getNick() +
									"\u0002 is a \u0002" + players[targidx].getDisplayedRole() + "\u0002!");
								players[plidx].seen = targidx;
							}
						} else {
							privmsg(nick, players[targidx].getNick() + " is already dead.");
						}
					} else {
						privmsg(nick, "You have already had a vision this night.");
					}
				} else {
					privmsg(nick, "Visions may only be had during the night.");
				}
			} else {
				privmsg(nick, "Only seers can see other players.");
			}
		} else {
			privmsg(nick, "You aren't even playing.");
		}
		
		// checks for the end of the night
		if(checkEndNight()) endNight();
	}
	
	/**
	 * Kills the specified player
	 * 
	 * @param who
	 * @param nick
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
		if(plidx != -1) {
			// Is the player alive?
			if(players[plidx].isAlive) {
				// Is the player a wolf?
				if(players[plidx].isWolf || players[plidx].isWerecrow) {
					// Is it night?
					if(isNight) {
						int targidx = getPlayer(who);
						// Is the target even playing?
						if(targidx == -1) {
							privmsg(nick, who + " is not playing.");
							return;
						}
						// Is the target alive? 
						if(players[targidx].isAlive) {
							// Is the target a wolf?
							if(players[targidx].isWolf || players[targidx].isWerecrow) {
								privmsg(nick, "You may not target other wolves.");
							} else {
								// Did you target yourself?
								if(plidx == targidx) {
									privmsg(nick, "Suicide is bad. Don't do it.");
								} else {
									// Add your vote.
									votes[targidx]++;
									if(players[plidx].voted >= 0) votes[players[plidx].voted]--;
									players[plidx].voted = targidx;
									// tells the wolves
									wolfmsg("\u0002" + players[plidx].getNick() + "\u0002 has selected \u0002" +
										players[targidx].getNick() + "\u0002 to be killed.");
								}
							}
						} else {
							privmsg(nick, players[targidx].getNick() + " is already dead.");
						}
					} else {
						privmsg(nick, "Killing may only be done during the night.");
					}
				} else {
					privmsg(nick, "Only wolves can kill other players.");
				}
			} else {
				privmsg(nick, "Dead players aren't going to be killing people anytime soon.");
			}
		} else {
			privmsg(nick, "You aren't even playing.");
		}
		
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
		
		// Was this a retraction?
		if(who == null) {
			// gets the player
			int plidx = getPlayer(nick, user, host);
			if(plidx != -1) {
				// Dead people may not vote
				if(players[plidx].isAlive) {
					if(players[plidx].voted >= 0) {
						// retracts the vote
						votes[players[plidx].voted]--;
						players[plidx].voted = -1;
						chanmsg("\u0002" + nick + "\u0002 has retracted his/her vote.");
					} else {
						// nobody was voted for
						privmsg(nick, "You haven't voted for anybody.");
					}
				} else {
					privmsg(nick, "Dead players may not vote.");
				}
			} else {
				// talker isn't playing
				privmsg(nick, "You aren't even playing.");
			}
			return;
		}
		
		// Cast the vote for the player.
		int targidx = getPlayer(who);
		if(targidx >= 0) {
			// gets the voter
			int plidx = getPlayer(nick, user, host);
			if(plidx != -1) {
				// Dead people may not vote
				if(players[plidx].isAlive) {
					// You also cannot vote for dead people
					if(players[targidx].isAlive) {
						// removes the old vote
						if(players[plidx].voted >= 0) {
							// retracts the vote
							votes[players[plidx].voted]--;
						}
						// adds the new vote
						players[plidx].voted = targidx;
						votes[targidx]++;
						chanmsg("\u0002" + nick + "\u0002 has voted to lynch \u0002" + players[targidx].getNick() + "\u0002!");
					} else {
						privmsg(nick, "He's already dead! Leave the body in its grave.");
					}
				} else {
					privmsg(nick, "Dead players may not vote.");
				}
			} else {
				// talker isn't playing
				privmsg(nick, "You aren't even playing.");
			}
		} else {
			privmsg(nick, who + " is not playing.");
		}
		
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
			chanmsg("\u0002" + nick + "\u0002 has joined the game.");
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
			players[plidx].isAlive = false;
			// kill him
			chanmsg(nick + " ate toxic berries and died. It appears s/he was a " + players[plidx].getDisplayedRole() + ".");
			devoice(nick);
			// Does a something occur now that there is one less player?
			if(players[plidx].voted >= 0) votes[players[plidx].voted]--; // remove his vote
			players[plidx].resetActions(); // reset any actions he's taken
			if(checkForEnding()) return; // Does game end?
			if(isNight) {
				if(checkEndNight()) endNight(); // Does night end?
			} else {
				checkForLynching(); // Does day end?
			}
		} else {
			if(rmvPlayer(nick, user, host)) {
				chanmsg(nick + " left the game.");
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
	 */
	private void startgame(String nick, String user, String host) {
		// Logs the command
		System.out.println("[CONSOLE] : " + nick + " issued the START command");
		// Is the starter even in the game?
		int plidx = getPlayer(nick, user, host);
		if(plidx == -1) {
			// not even in the game...
			privmsg(nick, "You aren't even playing.");
			return;
		}
		
		// 4+ people?
		if(playernum < 4) {
			chanmsg("You need at least four players to begin.");
			return;
		}
		
		// Welcome the players to the game & sets them alive.
		String namelist = "";
		int m = 0;
		while(m < playernum) {
			players[m].isAlive = true;
			namelist = namelist + players[m].getNick();
			if(m < playernum - 2) namelist = namelist + ", ";
			else if(m == playernum - 2) namelist = namelist + ", and ";
			m++;
		}
		chanmsg(namelist + ". Welcome to wolfgame as hosted by javawolf, a java implementation of the party game Mafia.");
		
		// Assign the roles
		// Set up the number of each
		int wolfcount = 0;
		int traitorcount = 0;
		int werecrowcount = 0;
		int seercount = 0;
		int harlotcount = 0;
		int drunkcount = 0;
		int angelcount = 0;
		int detectivecount = 0;
		int cursedcount = 0;
		int gunnercount = 0;
		if(playernum <= 5) {
			// 4 or 5 players
			wolfcount = 1;
			detectivecount = 1;
			gunnercount = 1;
		} else if(playernum <= 7) {
			// 6 or 7 players
			seercount = 1;
			wolfcount = 1;
			cursedcount = 1;
			drunkcount = 1;
		} else if(playernum <= 9) {
			// 8 or 9 players
			wolfcount = 2;
			seercount = 1;
			cursedcount = 1;
			drunkcount = 1;
			harlotcount = 1;
		} else if(playernum <= 14) {
			// 10 to 14 players
			wolfcount = 2;
			traitorcount = 1;
			seercount = 1;
			cursedcount = 1;
			drunkcount = 1;
			gunnercount = 1;
		} else {
			// 15+ people
			wolfcount = 2;
			werecrowcount = 1;
			traitorcount = 1;
			seercount = 1;
			cursedcount = 1;
			drunkcount = 1;
			gunnercount = 1;
			detectivecount = 1;
		}
		// randomly pick players to assign the wolf roles
		Random rand = new Random();
		int pidx = 0;
		while(wolfcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].isWolf = true;
				wolfcount--;
			}
		}
		while(traitorcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].isTraitor = true;
				traitorcount--;
			}
		}
		while(werecrowcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].isWerecrow = true;
				werecrowcount--;
			}
		}
		// now assign the main roles
		while(seercount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].isSeer = true;
				seercount--;
			}
		}
		while(drunkcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].isDrunk = true;
				drunkcount--;
			}
		}
		while(harlotcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].isHarlot = true;
				harlotcount--;
			}
		}
		while(angelcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].isAngel = true;
				angelcount--;
			}
		}
		while(detectivecount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(players[pidx].countMainRoles() == 0) {
				players[pidx].isDetective = true;
				detectivecount--;
			}
		}
		// now the secondary roles
		while(cursedcount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(!players[pidx].isDrunk && !players[pidx].isSeer && !players[pidx].isWolf) {
				players[pidx].isCursed = true;
				cursedcount--;
			}
		}
		while(gunnercount > 0) {
			pidx = (int)Math.floor(rand.nextDouble()*playernum);
			if(!players[pidx].isTraitor && !players[pidx].isWerecrow && !players[pidx].isWolf) {
				players[pidx].isGunner = true;
				gunnercount--;
				players[pidx].numBullets = (int)Math.floor(playernum / 9) + 1;
				if(players[pidx].isDrunk) players[pidx].numBullets *= 3; // drunk gets 3x normal bullet count
			}
		}
		
		// game started
		isRunning = true;
		isNight = true;
		// Start the night.
		chanmute();
		startNight();
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
			chanmsg("Resigned to his/her fate, \u0002" + players[ind].getNick() + "\u0002 is led to the gallows. After death, it is discovered " +
				"that s/he was a \u0002" + players[ind].getDisplayedRole() + "\u0002.");
			players[ind].isAlive = false; // kill lynched player
			devoice(players[ind].getNick());
			// Now that a lynching has occurred, end the day if the game is still running
			boolean ended = checkForEnding();
			if(!ended) endDay();
		}
	}
	
	/**
	 * Checks to see if the night ended.
	 * 
	 * @return
	 */
	private boolean checkEndNight() {
		// Check to see if all roles have acted.
		int m = 0;
		while(m < playernum) {
			// Dead players don't count
			if(!players[m].isAlive) {
				m++;
				continue;
			}
			if(players[m].isSeer && (players[m].seen == -1)) return false; // Seer didn't see
			if(players[m].isHarlot && (players[m].visited == -1)) return false; // Harlot didn't visit
			if(players[m].isWolf && (players[m].voted == -1)) return false; // Wolf didn't kill
			if(players[m].isAngel && (players[m].guarded == -1)) return false; // Angel hasn't guarded
			if(players[m].isWerecrow && (players[m].voted == -1)) return false; // Crow hasn't killed
			if(players[m].isWerecrow && (players[m].observed == -1)) return false; // Crow hasn't observed
			m++;
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
		
		// Are there any wolves left?
		if(wolfteamcount == 0) {
			chanmsg("Game over! All the wolves are dead! The villagers chop them up, barbeque them, and enjoy a hearty meal!");
			// broadcast roles
			String msg = "";
			int m = 0;
			while(m < playernum) {
				// append to string
				if(players[m].isSeer) msg = msg + players[m].getNick() + " was a seer. ";
				if(players[m].isDrunk) msg = msg + players[m].getNick() + " was a village drunk. ";
				if(players[m].isHarlot) msg = msg + players[m].getNick() + " was a harlot. ";
				if(players[m].isAngel) msg = msg + players[m].getNick() + " was a guardian angel. ";
				if(players[m].isDetective) msg = msg + players[m].getNick() + " was a detective. ";
				if(players[m].isGunner) msg = msg + players[m].getNick() + " was a gunner. ";
				if(players[m].isCursed) msg = msg + players[m].getNick() + " was a cursed villager. ";
				if(players[m].isWolf) msg = msg + players[m].getNick() + " was a wolf. ";
				if(players[m].isTraitor) msg = msg + players[m].getNick() + " was a traitor. ";
				if(players[m].isWerecrow) msg = msg + players[m].getNick() + " was a werecrow. ";
				
				// devoice any living people
				if(players[m].isAlive) devoice(players[m].getNick());
				
				m++;
			}
			chanmsg(msg);
			// game is over
			chanunmute();
			players = new WolfPlayer[MAX_WOLFPLAYERS];
			playernum = 0;
			isRunning = false;
			isNight = false;
			return true;
		}
		// Do the wolves eat the players?
		if((votingcount - wolfteamcount) <= wolfteamcount) {
			chanmsg("Game over! There are the same number of wolves as voting villagers! The wolves eat everyone and win.");
			// broadcast roles
			String msg = "";
			int m = 0;
			while(m < playernum) {
				// append to string
				if(players[m].isSeer) msg = msg + "\u0002" + players[m].getNick() + "\u0002 was a \u0002seer\u0002. ";
				if(players[m].isDrunk) msg = msg + "\u0002" + players[m].getNick() + "\u0002 was a \u0002drunk\u0002. ";
				if(players[m].isHarlot) msg = msg + "\u0002" + players[m].getNick() + "\u0002 was a \u0002harlot\u0002. ";
				if(players[m].isAngel) msg = msg + "\u0002" + players[m].getNick() + "\u0002 was a \u0002guardian angel\u0002. ";
				if(players[m].isDetective) msg = msg + "\u0002" + players[m].getNick() + "\u0002 was a \u0002detective\u0002. ";
				if(players[m].isGunner) msg = msg + "\u0002" + players[m].getNick() + "\u0002 was a \u0002gunner\u0002. ";
				if(players[m].isCursed) msg = msg + "\u0002" + players[m].getNick() + "\u0002 was a \u0002cursed villager\u0002. ";
				if(players[m].isWolf) msg = msg + "\u0002" + players[m].getNick() + "\u0002 was a \u0002wolf\u0002. ";
				if(players[m].isTraitor) msg = msg + "\u0002" + players[m].getNick() + "\u0002 was a \u0002traitor\u0002. ";
				if(players[m].isWerecrow) msg = msg + "\u0002" + players[m].getNick() + "\u0002 was a \u0002werecrow\u0002. ";
				
				// devoice any living people
				if(players[m].isAlive) devoice(players[m].getNick());
				
				m++;
			}
			chanmsg(msg);
			// game is over
			chanunmute();
			players = new WolfPlayer[MAX_WOLFPLAYERS];
			playernum = 0;
			isRunning = false;
			isNight = false;
			return true;
		}
		// Do we need to turn the traitors into wolves?
		// This happens if there are no wolves but still wolf teammates (traitors) left.
		if(wolfcount == 0) {
			int m = 0;
			while(m < playernum) {
				if(players[m].isTraitor) {
					players[m].isWolf = true;
					players[m].isTraitor = false;
					// Let them know in dramatic style :p
					privmsg(players[m].getNick(), "HOOOWWWWWLLLLLLL! You have become...a wolf! It is up to you to avenge your fallen leaders!");
				}
				m++;
			}
			// Let everyone know
			chanmsg("\u0002The villagers, in the midst of their rejoicing, are terrified as a loud howl breaks out. The wolves are not gone!\u0002");
		}
		// Game is still running
		return false;
	}
	
	/**
	 * Counts the number of players left on the wolfteam
	 * 
	 * @return
	 */
	private int countWolfteam() {
		int nWolves = 0;
		
		int m = 0;
		while(m < playernum) {
			// only consider living wolves
			if(players[m].isAlive) {
				if(players[m].isWolf || players[m].isTraitor || players[m].isWerecrow) nWolves++;
			}
			m++;
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
		
		int m = 0;
		while(m < playernum) {
			// only consider living wolves
			if(players[m].isAlive) {
				if(players[m].isWolf || players[m].isWerecrow) nWolves++;
			}
			m++;
		}
		
		return nWolves;
	}
	
	/**
	 * Counts the number of voting players left
	 * 
	 * @return
	 */
	private int countVoters() {
		int nVoters = 0;
		
		int m = 0;
		while(m < playernum) {
			// only consider living players
			if(players[m].isAlive && players[m].canVote) nVoters++;
			m++;
		}
		
		return nVoters;
	}
	
	/**
	 * Starts the night
	 */
	private void startNight() {
		// Start timing the night
		starttime = System.currentTimeMillis();
		// reset the votes
		votes = new int[MAX_WOLFPLAYERS];
		// PM all the players their roles
		String wolflist = "| ";
		int m = 0;
		while(m < playernum) {
			if(players[m].isWolf) wolflist = wolflist + players[m].getNick() + " (wolf) |";
			if(players[m].isTraitor) wolflist = wolflist + players[m].getNick() + " (traitor) |";
			if(players[m].isWerecrow) wolflist = wolflist + players[m].getNick() + " (werecrow) |";
			m++;
		}
		m = 0;
		while(m < playernum) {
			// evil roles
			if(players[m].isWolf) {
				privmsg(players[m].getNick(), "You are a \u0002wolf\u0002. Use \"" + 
					Javawolf.cmdchar + "kill <name>\" to kill a villager once per night.");
				privmsg(players[m].getNick(), "Wolf list " + wolflist);
			}
			if(players[m].isTraitor) {
				privmsg(players[m].getNick(), "You are a \u0002traitor\u0002. You are on the side of " +
					"the wolves, except that you are, for all intents and purposes, a villager. Only detectives can identify you. " +
					"If all the wolves die, you will become a wolf yourself.");
				privmsg(players[m].getNick(), "Wolf list " + wolflist);
			}
			if(players[m].isWerecrow) {
				privmsg(players[m].getNick(), "You are a \u0002werecrow\u0002. Use \"" + Javawolf.cmdchar + "kill <name>\" to kill a villager " +
					"once per night. You may also observe a player to see whether s/he stays in bed all night with \"" +
					Javawolf.cmdchar + "observe <name>\".");
				privmsg(players[m].getNick(), "Wolf list " + wolflist);
			}
			// good roles
			if(players[m].isSeer) privmsg(players[m].getNick(), "You are a \u0002seer\u0002. Use \"" + 
					Javawolf.cmdchar + "see <name>\" to see the role of a villager once per night. Be warned that traitors will " +
					"still appear as villagers, and cursed villagers will appear to be wolves. Use your judgment.");
			if(players[m].isDrunk) privmsg(players[m].getNick(), "You have been drinking too much! You are a \u0002village drunk\u0002! " +
				"You don't do anything special, but people are more likely to believe that you are not a wolf.");
			if(players[m].isHarlot) privmsg(players[m].getNick(), "You are a \u0002harlot\u0002. You may visit any player during the night. " +
				"If you visit a wolf or the victim of the wolves, you will die. If you are attacked while you are out visiting, " +
				"you will survive. Use \"" + Javawolf.cmdchar + "visit <name>\" to visit a player.");
			if(players[m].isAngel) privmsg(players[m].getNick(), "You are a \u0002guardian angel\u0002. You may choose one player to guard " +
				"per night. If you guard the victim, s/he will likely live. If you guard a wolf, you may die. Use \"" + Javawolf.cmdchar +
				"guard <name>\" to guard someone.");
			if(players[m].isDetective) privmsg(players[m].getNick(), "You are a \u0002detective\u0002. You act during the day, and you can even " +
				"identify traitors. Use \"" + Javawolf.cmdchar + "id <name>\" to identify someone. Be careful when iding, because " +
				"your identity might be revealed to the wolves.");
			if(players[m].isGunner) {
				privmsg(players[m].getNick(), "You hold a gun that shoots special silver bullets. If you shoot a wolf, s/he will die. " +
					"If you shoot a villager, s/he will most likely live. Use \"" + Javawolf.cmdchar + "shoot <name>\" to shoot.");
				privmsg(players[m].getNick(), "You have " + players[m].numBullets + " bullets remaining.");
			}
			// Next player
			m++;
		}
		// Announce the night to the players
		chanmsg("It is now night, and the villagers all retire to their beds. The full moon rises in the east, casting long, eerie " +
			"shadows across the village. A howl breaks out, as the wolves come out to slay someone.");
		chanmsg("Please check for private messages from me. If you have none, simply sit back and wait patiently for morning.");
	}
	
	/**
	 * Ends the night
	 */
	private void endNight() {
		int m = 0;
		// Posts the time the night took
		long millis = System.currentTimeMillis() - starttime;
		int secs = (int)(millis / 1000);
		int mins = (int)(secs / 60);
		secs = secs % 60;
		chanmsg("Night lasted " + mins + " minutes and " + secs + " seconds.");
		// Announce the end of the night 
		chanmsg("Dawn breaks! The villagers wake up and look around.");
		// Tally up the votes and announce the kill
		int[] voteresults = tallyvotes();
		//int nVotes = voteresults[0];
		int ind = voteresults[1];
		if(ind == -1) {
			// LOL
			chanmsg("The wolves were unable to decide who to kill last night. As a result, all villagers have survived.");
		} else {
			// Wolves attack someone
			// Is the target a harlot who visited someone?
			if(players[ind].isHarlot && (players[ind].visited >= 0) && (players[ind].visited != ind)) {
				// Harlot visited someone else
				chanmsg("The wolves' selected victim was a harlot, but she wasn't home.");
			} else {
				chanmsg("The dead body of \u0002" + players[ind].getNick() + "\u0002, a \u0002" + players[ind].getDisplayedRole() + 
					"\u0002, is found. Those remaining mourn his/her death.");
				players[ind].isAlive = false;
				devoice(players[ind].getNick());
			}
			// Did any harlots visit the victim?
			m = 0;
			while(m < playernum) {
				if(players[m].isHarlot) {
					if(players[m].visited == ind) {
						// Harlot visited victim and died
						chanmsg(players[m].getNick() + ", a harlot, made the unfortunate mistake of visiting the victim last night " +
							"and is now dead.");
						players[m].isAlive = false;
						devoice(players[m].getNick());
					} else if(players[players[m].visited].isWolf || players[players[m].visited].isWerecrow) {
						// Harlot visited wolf and died
						chanmsg("\u0002" + players[m].getNick() + "\u0002, a \u0002harlot\u0002, made the unfortunate mistake of " +
							"visiting a wolf last night and is now dead.");
						players[m].isAlive = false;
						devoice(players[m].getNick());
					}
				}
				// next player
				m++;
			}
		}
		// Reset the players' actions and votes
		votes = new int[MAX_WOLFPLAYERS];
		m = 0;
		while(m < playernum) {
			players[m].resetActions();
			m++;
		}
		// Check to see if the game ended
		checkForEnding();
		if(isRunning) {
			chanmsg("The villagers must now decide who to lynch. Use \"" + Javawolf.cmdchar + "lynch <name>\" to vote for someone.");
			isNight = false;
		}
		// Start timing the day
		starttime = System.currentTimeMillis();
	}
	
	/**
	 * Ends the day
	 */
	private void endDay() {
		if(isRunning) {
			// Posts the time the day took
			long millis = System.currentTimeMillis() - starttime;
			int secs = (int)(millis / 1000);
			int mins = (int)(secs / 60);
			secs = secs % 60;
			chanmsg("Day lasted " + mins + " minutes and " + secs + " seconds.");
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
		
		int m = 0;
		while(m < playernum) {
			if(votes[m] > nVotes) {
				nVotes = votes[m];
				indVoted = m;
			} else if(votes[m] == nVotes) {
				indVoted = -1; // tie
			}
			m++;
		}
		
		// return the values
		int[] retvals = new int[2];
		retvals[0] = nVotes;
		retvals[1] = indVoted;
		return retvals;
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
		int m = 0;
		while(m < playernum) {
			if(players[m].identmatch(nick, user, host)) {
				return m; // found him
			}
			m++;
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
		int m = 0;
		while(m < playernum) {
			if(players[m].nickmatch(nick)) {
				return m; // found him
			}
			m++;
		}
		// no such player
		return -1;
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
		if(getPlayer(nick, user, host) >= 0) return false;
		// only add the player if enough slots
		if(playernum < MAX_WOLFPLAYERS) {
			players[playernum] = new WolfPlayer(nick, user, host);
			playernum++;
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
			int m = plidx;
			while(m < playernum-1) {
				players[m] = players[m+1];
				m++;
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
	 * Broadcasts messages to the wolf team
	 * 
	 * @param msg
	 */
	private void wolfmsg(String msg) {
		int m = 0;
		while(m < playernum) {
			if(players[m].isWolf || players[m].isTraitor || players[m].isWerecrow) privmsg(players[m].getNick(), msg);
			m++;
		}
	}
	
	/**
	 * Messages the channel
	 * 
	 * @param msg
	 */
	private void chanmsg(String msg) {
		Javawolf.mgr.getSession(assocServer).channelSay(assocChannel, msg);
		try {
			Thread.sleep(200);
		} catch(InterruptedException ie) {
			System.err.println("[CONSOLE] : Could not sleep threading after channel message!");
			System.err.println("[CONSOLE] : " + ie.getMessage());
		}
		//Javawolf.eventbuf.addEvent(BufferedIRC.EVENT_MSG, null, msg);
	}
	
	/**
	 * Messages a player
	 * 
	 * @param nick
	 * @param msg
	 */
	private void privmsg(String nick, String msg) {
		Javawolf.mgr.getSession(assocServer).sayPrivate(nick, msg);
		try {
			Thread.sleep(200);
		} catch(InterruptedException ie) {
			System.err.println("[CONSOLE] : Could not sleep threading after private message!");
			System.err.println("[CONSOLE] : " + ie.getMessage());
		}
		//Javawolf.eventbuf.addEvent(BufferedIRC.EVENT_MSG, nick, msg);
	}
	
	/**
	 * Mutes the channel
	 */
	private void chanmute() {
		Javawolf.mgr.getSession(assocServer).mode(Javawolf.mgr.getSession(assocServer).getChannel(assocChannel), "+m");
	}
	
	/**
	 * Unmutes the channel
	 */
	private void chanunmute() {
		Javawolf.mgr.getSession(assocServer).mode(Javawolf.mgr.getSession(assocServer).getChannel(assocChannel), "-m");
	}
	
	/**
	 * Voices the given player
	 * 
	 * @param nick
	 */
	private void voice(String nick) {
		Javawolf.mgr.getSession(assocServer).voice(nick, Javawolf.mgr.getSession(assocServer).getChannel(assocChannel));
		//Javawolf.mgr.getSession(assocServer).rawSay("MODE " + assocChannel + " +v " + nick);
		try {
			Thread.sleep(200);
		} catch(InterruptedException ie) {
			System.err.println("[CONSOLE] : Could not sleep threading after voicing!");
			System.err.println("[CONSOLE] : " + ie.getMessage());
		}
		//System.out.println("[CONSOLE] : /raw MODE " + assocChannel + " +v " + nick);
	}
	
	/**
	 * Devoices the given player
	 * 
	 * @param nick
	 */
	private void devoice(String nick) {
		Javawolf.mgr.getSession(assocServer).deVoice(nick, Javawolf.mgr.getSession(assocServer).getChannel(assocChannel));
		//Javawolf.mgr.getSession(assocServer).rawSay("MODE " + assocChannel + " -v " + nick);
		try {
			Thread.sleep(200);
		} catch(InterruptedException ie) {
			System.err.println("[CONSOLE] : Could not sleep threading after devoicing!");
			System.err.println("[CONSOLE] : " + ie.getMessage());
		}
		//System.out.println("[CONSOLE] : /raw MODE " + assocChannel + " -v " + nick);
	}
}
