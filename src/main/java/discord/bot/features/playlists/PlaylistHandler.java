package discord.bot.features.playlists;

import discord.bot.features.commands.parts.Command;
import discord.logger.DiscordLogger;
import discord.utility.MemManager;
import discord4j.core.object.entity.channel.MessageChannel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaylistHandler {

	private DiscordLogger logger;

	private final List<Playlist> playlists;

	private Map<String, Command> commands;

	public PlaylistHandler(DiscordLogger logger){
		playlists = MemManager.loadPlaylists();
		this.logger = logger;
		initCommands();

		Playlist test = new Playlist("Test");
		test.add("yay");
		test.add("dope");
		playlists.add(test);
	}

	private void initCommands(){
		commands = new HashMap<>();
	}

	private void addCommandAddPlaylist(){

	}

}
