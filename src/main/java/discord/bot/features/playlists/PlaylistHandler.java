package discord.bot.features.playlists;

import discord.logger.DiscordLogger;
import discord.utility.MemManager;

import java.util.List;

public class PlaylistHandler {

	private DiscordLogger logger;

	private final List<Playlist> playlists;

	public PlaylistHandler(DiscordLogger logger){

		playlists = MemManager.loadPlaylists();
		this.logger = logger;
	}

}
