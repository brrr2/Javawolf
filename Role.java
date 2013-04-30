/*
 * Import stuff
 */
package Javawolf;

public class Role {
	// Name of this role
	public String szName = null;
	// What this role is seen as
	public String szSeenAs = null;
	// Show the role on death?
	public boolean shownOnDeath = true;
	// Show the role on being seen?
	public boolean shownOnSeen = true;
	// Show the role on being id'ed?
	public boolean shownOnId = true;
	// Kills harlot on visiting?
	public boolean killsOnVisit = false;
	// Are we evil?
	public boolean evil = false;
	
	public Role(String szName, String szSeenAs, boolean shownOnDeath, boolean shownOnSeen, boolean shownOnId,
			boolean killsOnVisit, boolean evil) {
		// Assign variables
		this.szName = szName;
		this.szSeenAs = szSeenAs;
		this.shownOnDeath = shownOnDeath;
		this.shownOnSeen = shownOnSeen;
		this.shownOnId = shownOnId;
		this.killsOnVisit = killsOnVisit;
		this.evil = evil;
	}
}
