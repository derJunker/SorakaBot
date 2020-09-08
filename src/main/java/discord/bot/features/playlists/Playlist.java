package discord.bot.features.playlists;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Color;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Playlist implements Serializable {

	private static final long serialVersionUID = 6633067407683597948L;
	//the name of the playlist
	private String name;

	//it stores the keyWords/links to which will be executed with the play command of Rythm
	//it can either be urls to videos, or just a description
	private final List<String> songs;

	//you cant save the guild itself, bc you can't save/serialize a guild object
	private String guildId;


	public Playlist(String name, List<String> songKeyWords, Guild guild){
		this.name = name;
		this.songs = songKeyWords;
		this.guildId = guild.getId().asString();
	}

	public Playlist(String name, Guild guild){
		this(name, new ArrayList<>(), guild);
	}

	/**
	 * adds a song to the playlist
	 * @param keyPhrase the phrase which should be added
	 */
	public void add(String keyPhrase){
		songs.add(keyPhrase);
	}

	public boolean remove(String keyPhrase){
		return songs.remove(keyPhrase);
	}

	public String remove(int i){
		return songs.remove(i);
	}

	/**
	 * displays the playlist as an embed message
	 * @param channel the channel where to display it
	 */
	public void embedInformation(MessageChannel channel){
		channel.createEmbed(embed -> {
			embed.setTitle(name)
					.setColor(Color.RED);
			StringBuilder songListing = new StringBuilder();
			for(int i = 0; i < songs.size(); i++){
				String song = songs.get(i);
				songListing.append("**" + i + "**: " + song + "\n");
			}
			embed.setDescription(songListing.toString());

		}).block();
	}

	//getter & setter
	public String getName(){
		return name;
	}

	public String getGuildId(){
		return guildId;
	}
}
