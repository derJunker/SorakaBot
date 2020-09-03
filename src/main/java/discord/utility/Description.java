package discord.utility;

/**
 * this class models a description,
 * it contains a long and short version, but both dont have to exist
 */
public class Description {
	private String versionLong;
	private String versionShort;

	public Description(String versionShort, String versionLong){
		this.versionShort = versionShort;
		this.versionLong = versionLong;
	}

	public Description(String versionShort){
		this(versionShort, "");
	}

	public Description(){
		this("", "");
	}

	public String vLong() {
		return versionLong;
	}

	public void setVersionLong(String versionLong) {
		this.versionLong = versionLong;
	}

	public String vShort() {
		return versionShort;
	}

	public void setVersionShort(String versionShort) {
		this.versionShort = versionShort;
	}
}
