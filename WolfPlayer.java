/*
 * Includes
 */
package Javawolf;

import org.jibble.pircbot.Colors;

/**
 * @author Reaper Eternal
 *
 */
public class WolfPlayer {
	// Nickname
	private String nick = null;
	// Username
	private String user = null;
	// Host
	private String host = null;
	
	// roles
	public boolean[] roles = new boolean[32];
	// role indices
	// primary villager roles
	public static final int ROLE_PRIMARY_LOWBOUND = 0;
	public static final int ROLE_SEER = 0;
	public static final int ROLE_HARLOT = 1;
	public static final int ROLE_DRUNK = 2;
	public static final int ROLE_ANGEL = 3;
	public static final int ROLE_DETECTIVE = 4;
	public static final int ROLE_MEDIUM = 5;
	public static final int ROLE_PRIMARY_HIGHBOUND = 5;
	// secondary roles
	public static final int ROLE_GUNNER = 6;
	public static final int ROLE_CURSED = 7;
	// wolf roles
	public static final int ROLE_WOLF_LOWBOUND = 8;
	public static final int ROLE_WOLF = 8;
	public static final int ROLE_TRAITOR = 9;
	public static final int ROLE_WERECROW = 10;
	public static final int ROLE_SORCERER = 11;
	public static final int ROLE_WOLF_HIGHBOUND = 11;
	// role names
	public static Role[] sz_roles = null;
	// villager roles
	/*public boolean isSeer = false;
	public boolean isHarlot = false;
	public boolean isDrunk = false;
	public boolean isAngel = false;
	public boolean isDetective = false;
	public boolean isMedium = false;
	// secondary roles
	public boolean isGunner = false;
	public boolean isCursed = false;
	// evil roles >:)
	public boolean isWolf = false;
	public boolean isTraitor = false;
	public boolean isWerecrow = false;
	public boolean isSorcerer = false;*/
	// Number of bullets left
	public int numBullets = 0;
    public int votes = 0;
	// in tavern?
	public boolean isInTavern = false;
	
	// --- Indices into <WolfGame.java>'s <players> array ---
	// Who he has voted for / killed
	public WolfPlayer voted = null;
	// Who he has seen
	public WolfPlayer seen = null;
	// Who he has visited
	public WolfPlayer visited = null;
	// Who he has guarded
	public WolfPlayer guarded = null;
	// Who he has id'ed
	public WolfPlayer ided = null;
	// Who he has observed
	public WolfPlayer observed = null;
	// Who he has raised
	public WolfPlayer raised = null;
	// Who he has cursed
	public WolfPlayer cursed = null;
	// Who he loves
	public WolfPlayer lover = null;
	
	// Is he even alive?
	public boolean isAlive = false;
	// Can he vote?
	public boolean canVote = true;
	
	// last actions
	private static final int MAX_ACTION_STORE = 8;
	private static final int FLOOD_PROTECT_TIME = 4000; // in ms
	private long[] actiontimes = new long[MAX_ACTION_STORE];
	// Warned of idling?
	public boolean isIdleWarned = false;
	
	/**
	 * Creates the basic player
	 * 
	 * @param newNick
	 * @param newUser
	 * @param newHost
	 */
	public WolfPlayer(String newNick, String newUser, String newHost, long join) {
		// Generate player
		nick = newNick;
		user = newUser;
		host = newHost;
		this.addAction(join);
		// Assign role strings
		if(sz_roles == null) {
			sz_roles = new Role[32];
			sz_roles[ROLE_SEER] = new Role("seer", "seer", true, true, true, false, false);
			sz_roles[ROLE_HARLOT] = new Role("harlot", "harlot", true, true, true, false, false);
			sz_roles[ROLE_DRUNK] = new Role("village drunk", "village drunk", true, true, true, false, false);
			sz_roles[ROLE_ANGEL] = new Role("guardian angel", "guardian angel", true, true, true, false, false);
			sz_roles[ROLE_DETECTIVE] = new Role("detective", "detective", true, true, true, false, false);
			sz_roles[ROLE_MEDIUM] = new Role("medium", "medium", true, true, true, false, false);
			sz_roles[ROLE_GUNNER] = new Role("gunner", null, true, false, true, false, false);
			sz_roles[ROLE_CURSED] = new Role("cursed", "wolf", true, true, false, false, false);
			sz_roles[ROLE_WOLF] = new Role("wolf", "wolf", true, true, true, true, true);
			sz_roles[ROLE_TRAITOR] = new Role("traitor", null, true, false, true, false, true);
			sz_roles[ROLE_WERECROW] = new Role("werecrow", "werecrow", true, true, true, true, true);
			sz_roles[ROLE_SORCERER] = new Role("sorcerer", "sorcerer", true, true, true, false, true);
		}
	}
	
	// Is the player a match to the given player?
	public boolean identmatch(String mNick, String mUser, String mHost) {
		return mNick.equals(nick) && mUser.equals(user) && mHost.equals(host);
	}
    public boolean identMatch(WolfPlayer wp) {
		return identmatch(wp.getNick(), wp.getUser(), wp.getHost());
	}
	
	// Does a player begin with the given nick
	public boolean nickmatch(String mNick) {
		return mNick.equals(nick);
	}
	
	// gets the nick
	public String getNick() {
		return nick;
	}
    
    public String getNickBold(){
        return Colors.BOLD + nick + Colors.BOLD;
    }
	
	// sets the nick
	public void setNick(String nick) {
		this.nick = nick;
	}
	
	// gets the nick
	public String getUser() {
		return user;
	}
	
	// gets the hostmask
	public String getHost() {
		return host;
	}
	
    // Role accessor methods
    public boolean isWolf(){
        return roles[ROLE_WOLF];
    }
    public boolean isWerecrow(){
        return roles[ROLE_WERECROW];
    }
    public boolean isTraitor(){
        return roles[ROLE_TRAITOR];
    }
    public boolean isSorcerer(){
        return roles[ROLE_SORCERER];
    }
    public boolean isSeer(){
        return roles[ROLE_SEER];
    }
    public boolean isHarlot(){
        return roles[ROLE_HARLOT];
    }
    public boolean isAngel(){
        return roles[ROLE_ANGEL];
    }
    public boolean isDetective(){
        return roles[ROLE_DETECTIVE];
    }
    public boolean isDrunk(){
        return roles[ROLE_DRUNK];
    }
    public boolean isGunner(){
        return roles[ROLE_GUNNER];
    }
    public boolean isMedium(){
        return roles[ROLE_MEDIUM];
    }

	/**
	 * Used to show all the roles on death.
	 * 
	 * @return
	 */
	public String getEndGameDisplayedRole() {
		// Enumerate roles
		String[] roleList = new String[32];
		int count = 0;
		for (int m = 0; m <= ROLE_WOLF_HIGHBOUND; m++) {
			if(roles[m]) {
				roleList[count] = sz_roles[m].szName;
				count++;
			}
		}
		// parse into string
		return parseEnumeratedRoles(roleList, count);
	}
	
	/**
	 * Gets the role to display to players on death.
	 * 
	 * @return
	 */
	public String getDeathDisplayedRole() {
		// Enumerate roles
		String[] roleList = new String[32];
		int count = 0;
		for (int m = 0; m <= ROLE_WOLF_HIGHBOUND; m++) {
			if(roles[m] && sz_roles[m].shownOnDeath) {
				roleList[count] = sz_roles[m].szName;
				count++;
			}
		}
		// parse into string
		return parseEnumeratedRoles(roleList, count);
	}
	
	/**
	 * Gets the role as IDed by detectives.
	 * 
	 * @return
	 */
	public String getIDedRole() {
		// Enumerate roles
		String[] roleList = new String[32];
		int count = 0;
		for (int m = 0; m <= ROLE_WOLF_HIGHBOUND; m++) {
			if(roles[m] && sz_roles[m].shownOnId) {
				roleList[count] = sz_roles[m].szName;
				count++;
			}
		}
		// parse into string
		return parseEnumeratedRoles(roleList, count);
	}
	
	/**
	 * Gets the role as seen by seers.
	 * 
	 * @return
	 */
	public String getSeenRole() {
		// Enumerate roles
		String[] roleList = new String[32];
		int count = 0;
		for (int m = 0; m <= ROLE_WOLF_HIGHBOUND; m++) {
			if(roles[m] && sz_roles[m].shownOnSeen) {
				roleList[count] = sz_roles[m].szName;
				count++;
				// "Wolf" always blocks vision of all other roles
				if(sz_roles[m].szName.contentEquals("wolf")) return "\u0002wolf\u0002";
			}
		}
		// parse into string
		return parseEnumeratedRoles(roleList, count);
	}
	
	/**
	 * Parses an enumerated list of roles into a displayable string.
	 * 
	 * @param roleList a String array of roles
	 * @param count 
	 * @return
	 */
	private String parseEnumeratedRoles(String[] roleList, int count) {
		if(count == 0) return Colors.BOLD+"villager"+Colors.BOLD;
		String str = "";
		for (int m = 0; m < count; m++) {
			str = str + Colors.BOLD + roleList[m] + Colors.BOLD;
			if(m == count - 2) {
				str = str + ", and ";
			} else if(m < count - 2) {
				str = str + ", ";
			}
		}
		
		// return the string
		return str;
	}
	
	// counts all the roles
	public int countAllRoles() {
		return this.countRoles(ROLE_PRIMARY_LOWBOUND, ROLE_WOLF_HIGHBOUND);
	}
	
	// counts the main village roles
	public int countMainRoles() {
		// Return wolf + villager count.
		return this.countVillageRoles() + this.countWolfRoles();
	}
	
	// counts the villager roles
	public int countVillageRoles() {
		return countRoles(ROLE_PRIMARY_LOWBOUND, ROLE_PRIMARY_HIGHBOUND);
	}
	
	// counts the wolf roles
	public int countWolfRoles() {
		return countRoles(ROLE_WOLF_LOWBOUND, ROLE_WOLF_HIGHBOUND);
	}
	
	/**
	 * Counts the roles between the given bounds (inclusive count).
	 * 
	 * @param low
	 * @param high
	 * @return
	 */
	private int countRoles(int low, int high) {
		int count = 0;
        for (int m = low; m <= high; m++) {
			if(roles[m]) count++;
		}
		// return it
		return count;
	}
	
	// resets the actions taken by the player
	public void resetActions() {
		voted = null;
		seen = null;
		visited = null;
		guarded = null;
		ided = null;
		observed = null;
		raised = null;
		cursed = null;
        lover = null;
		canVote = true;
	}
	
	/**
	 * Adds an action at the given time.
	 * 
	 * @param time
	 * @return
	 */
	public boolean addAction(long time) {
		for (int m = 0; m <= MAX_ACTION_STORE-2; m++) {
			actiontimes[m] = actiontimes[m+1];
		}
		actiontimes[MAX_ACTION_STORE-1] = time;
		// Obviously, an action has just occurred.
		isIdleWarned = false;
		// Return whether flooding is occurring.
		return (actiontimes[MAX_ACTION_STORE-1] - actiontimes[0]) < FLOOD_PROTECT_TIME;
	}
	
	/**
	 * Gets the time of the last action of this player.
	 * 
	 * @return
	 */
	public long getLastAction() {
		return actiontimes[MAX_ACTION_STORE-1];
	}
    
    public boolean equals(WolfPlayer wp){
        return identMatch(wp);
    }
}

