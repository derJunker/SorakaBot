package discord.bot.features.playlists;

import discord.bot.SorakaBot;
import discord.bot.features.commands.parts.Command;
import discord.bot.features.commands.parts.CommandRequirement;
import discord.bot.features.commands.parts.Executable;
import discord.logger.DiscordLogger;
import discord.utility.BotUtility;
import discord.utility.Description;
import discord.utility.MemManager;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;

import java.util.*;
import java.util.stream.Collectors;

public class PlaylistHandler {

	private DiscordLogger logger;
	private GatewayDiscordClient client;

	private final List<Playlist> playlists;

	//the list of commands and the string representation of it
	private Map<String, Command> commands;

	//the channels which are used to play playlists grouped by guild
	//the guild is also stored in the channel (channel.getGuild())
	//so the channels could be stored in a simple list
	//you don't have to add a new Guild (and so a new channel)
	//but you have to access the channel a lot when you only know the guild
	//and i don't want to traverse a whole list to check which is the right channel
	private final Map<Guild, GuildMessageChannel> musicChannels;

	public PlaylistHandler(DiscordLogger logger, GatewayDiscordClient client){
		//assigning the vars to params
		this.logger = logger;
		this.client = client;
		//loading the saved vars
		playlists = MemManager.loadPlaylists();
		musicChannels = MemManager.loadMusicChannels(client);

		//initializing all the commands
		initCommands();
	}

	private void initCommands(){
		commands = new HashMap<>();
		addCommandCreatePlaylist();
		addCommandShowPlaylists();
	}

	/**
	 * adding the command to create a playlist
	 */
	private void addCommandCreatePlaylist(){
		//the name of the command
		String name = "mkPlaylist";
		//so far anyone can make a playlist
		//the syntax is !mkPlaylist $name
		CommandRequirement syntax = CommandRequirement.correctSyntaxSegmentAmount(2);
		//also the command only makes sense in a guild
		CommandRequirement inGuild = CommandRequirement.IN_GUILD;
		List<CommandRequirement> requirements = List.of(syntax, inGuild);

		Description description = new Description("creates a new Playlist");

		Executable executable = message -> {
			//it requires the command to be in a guild
			GuildMessageChannel channel = (GuildMessageChannel) message.getChannel().block();
			Guild guild = channel.getGuild().block();
			//getting the content of the message and splitting it up to get the second argument (which is the name of the playlist)
			String content = message.getContent();
			String[] segments = content.split(" ");
			String playlistName = segments[1];

			//check if the name is already taken
			//first get the names of all the playlist, and then check if this name is in it
			List<String> takenNames = playlists.stream()
												.map(playlist -> playlist.getName())
												.collect(Collectors.toList());
			if(takenNames.contains(playlistName)){
				//then tell the user
				channel.createMessage("The name is already taken").block();
				return;
			}

			//give feedback to the user
			channel.createMessage("**Successfully** created the Playlist: ** " + playlistName + "**!").block();

			//now create the new Playlist and add it to the list of playlists
			playlists.add(new Playlist(playlistName, guild));
			//also save the playlists
			MemManager.savePlaylists(playlists);
		};

		commands.put(name, new Command(requirements, executable, description));
	}

	private void addCommandShowPlaylists(){
		//the name of the command
		String commandName = "showPlaylists";
		//the syntax is !showPlaylists
		CommandRequirement syntax = CommandRequirement.correctSyntaxSegmentAmount(1);
		//also the command only makes sense in a guild
		CommandRequirement inGuild = CommandRequirement.IN_GUILD;
		//and the bot has to have the permission to create an embed
		CommandRequirement canEmbed = CommandRequirement.botHasPermission(Permission.EMBED_LINKS);
		List<CommandRequirement> requirements = List.of(syntax, inGuild, canEmbed);

		Description description = new Description("shows all available playlists");

		Executable executable = message -> {
			//it requires the command to be in a guild
			GuildMessageChannel channel = (GuildMessageChannel) message.getChannel().block();
			Guild guild = channel.getGuild().block();

			//create the message to display all playlist
			//before that get all names of the playlists on the server
			List<String> playlistNames = playlists.stream()
													.filter(playlist -> guild.getId().asString().equals(playlist.getGuildId()))
													.map(playlist -> playlist.getName())
													.collect(Collectors.toList());

			channel.createEmbed(embed -> {

				embed.setTitle("Here are all Playlists for this server");
				StringBuilder playlistStrings = new StringBuilder();
				playlistNames.forEach(name -> playlistStrings.append("- **" + name + "**\n"));
				embed.setDescription(playlistStrings.toString());
			}).block();
		};

		commands.put(commandName, new Command(requirements, executable, description));
	}

	/**
	 * adds the command to add a song (yt-url) to a playlist
	 */
	private void addCommandAddSongToPlaylist(){
		//the name of the command
		String name = "addSong";
		//the syntax is !addSong $playlist $url
		CommandRequirement syntax = CommandRequirement.correctSyntaxSegmentAmount(3);
		//also the command only makes sense in a guild
		CommandRequirement inGuild = CommandRequirement.IN_GUILD;
		List<CommandRequirement> requirements = List.of(syntax, inGuild);

		Description description = new Description("adds a new song to a playlist. Syntax: **!addSong $playlist $yt-url**");
 
		Executable executable = message -> {

		};

		commands.put(name, new Command(requirements, executable, description));
	}

	//getter&setter
	public Map<String, Command> getCommands(){
		return commands;
	}

}
