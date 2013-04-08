/*
 * 
 */
package Javawolf;

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
	
	// villager roles
	public boolean isSeer = false;
	public boolean isHarlot = false;
	public boolean isDrunk = false;
	public boolean isAngel = false;
	public boolean isDetective = false;
	public boolean isMedium = false;
	// secondary roles
	public boolean isGunner = false;
	public boolean isCursed = false;
	// in tavern?
	public boolean isInTavern = false;
	// evil roles >:)
	public boolean isWolf = false;
	public boolean isTraitor = false;
	public boolean isWerecrow = false;
	public boolean isSorcerer = false;
	// Number of bullets left
	public int numBullets = 0;
	
	// --- Indices into <WolfGame.java>'s <players> array ---
	// Who he has voted for / killed
	public int voted = -1;
	// Who he has seen
	public int seen = -1;
	// Who he has visited
	public int visited = -1;
	// Who he has guarded
	public int guarded = -1;
	// Who he has id'ed
	public int ided = -1;
	// Who he has observed
	public int observed = -1;
	// Who he has raised
	public int raised = -1;
	// Who he has cursed
	public int cursed = -1;
	// Who he loves
	public int lover = -1;
	
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
		nick = newNick;
		user = newUser;
		host = newHost;
		this.addAction(join);
	}
	
	// Is the player a match to the given player?
	public boolean identmatch(String mNick, String mUser, String mHost) {
		if((mNick.compareTo(nick) == 0) && (mUser.compareTo(user) == 0) && (mHost.compareTo(host) == 0)) {
			return true;
		} else {
			return false; // nope, not same guy
		}
	}
	
	// Does a player begin with the given nick
	public boolean nickmatch(String mNick) {
		if(mNick.compareTo(nick) == 0) {
			return true;
		} else {
			return false; // nope, not same guy
		}
	}
	
	// gets the nick
	public String getNick() {
		return nick;
	}
	
	// sets the nick
	public void setNick(String nick) {
		this.nick = nick;
	}
	
	// gets the nick
	public String getUser() {
		return user;
	}
	
	// gets the nick
	public String getHost() {
		return host;
	}
	
	// returns the role as displayed to players
	public String getDisplayedRole() {
		String str = "";
		int rolecount = 0;
		// village primary roles
		if(isSeer) {
			str = str + "seer";
			rolecount++;
		}
		if(isHarlot) {
			if(rolecount > 0) str = str + " and ";
			str = str + "harlot";
			rolecount++;
		}
		if(isDrunk) {
			if(rolecount > 0) str = str + " and ";
			str = str + "village drunk";
			rolecount++;
		}
		if(isAngel) {
			if(rolecount > 0) str = str + " and ";
			str = str + "guardian angel";
			rolecount++;
		}
		if(isDetective) {
			if(rolecount > 0) str = str + " and ";
			str = str + "detective";
			rolecount++;
		}
		if(isMedium) {
			if(rolecount > 0) str = str + " and ";
			str = str + "medium";
			rolecount++;
		}
		// village secondary roles
		if(isGunner) {
			if(rolecount > 0) str = str + " and ";
			str = str + "gunner";
			rolecount++;
		}
		// wolf roles
		if(isWolf) {
			if(rolecount > 0) str = str + " and ";
			str = str + "wolf";
			rolecount++;
		}
		if(isTraitor) {
			if(rolecount > 0) str = str + " and ";
			str = str + "traitor";
			rolecount++;
		}
		if(isWerecrow) {
			if(rolecount > 0) str = str + " and ";
			str = str + "werecrow";
			rolecount++;
		}
		if(isSorcerer) {
			if(rolecount > 0) str = str + " and ";
			str = str + "sorcerer";
			rolecount++;
		}
		// no role
		if(rolecount == 0) str = "villager";
		// done making the list of roles
		return str;
	}
	
	// counts the roles
	public int countMainRoles() {
		// Return wolf + villager count.
		return this.countVillageRoles() + this.countWolfRoles();
	}
	
	// counts the villager roles
	public int countVillageRoles() {
		int rolecount = 0;
		if(isSeer) rolecount++;
		if(isHarlot) rolecount++;
		if(isDrunk) rolecount++;
		if(isAngel) rolecount++;
		if(isDetective) rolecount++;
		if(isMedium) rolecount++;
		
		// done making the role count
		return rolecount;
	}
	
	// counts the wolf roles
	public int countWolfRoles() {
		int rolecount = 0;
		if(isWolf) rolecount++;
		if(isTraitor) rolecount++;
		if(isWerecrow) rolecount++;
		if(isSorcerer) rolecount++;
		
		// done making the role count
		return rolecount;
	}
	
	// resets the actions taken by the player
	public void resetActions() {
		voted = -1;
		seen = -1;
		visited = -1;
		guarded = -1;
		ided = -1;
		observed = -1;
		raised = -1;
		cursed = -1;
		canVote = true;
	}
	
	/**
	 * Adds an action at the given time.
	 * 
	 * @param time
	 * @return
	 */
	public boolean addAction(long time) {
		int m = 0;
		while(m <= MAX_ACTION_STORE-2) {
			actiontimes[m] = actiontimes[m+1];
			m++;
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
}

