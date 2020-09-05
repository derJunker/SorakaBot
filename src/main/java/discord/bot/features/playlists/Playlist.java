package discord.bot.features.playlists;

import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Color;

import java.util.ArrayList;
import java.util.List;

public class Playlist {

	//the name of the playlist
	private String name;

	//it stores the keyWords/links to which will be executed with the play command of Rythm
	//it can either be urls to videos, or just a description
	private final List<String> songs;

	public Playlist(String name, List<String> songKeyWords){
		this.name = name;
		this.songs = songKeyWords;
	}

	public Playlist(String name){
		this(name, new ArrayList<>());
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
			StringBuilder songListing = new StringBuilder("Songs: \n");
			for(int i = 0; i < songs.size(); i++){
				String song = songs.get(i);
				songListing.append("**" + i + "**: " + song + "\n");
			}
			embed.setDescription(songListing.toString());

		}).block();
	}
}
