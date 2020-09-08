package discord.bot.features.playlists;

import discord.bot.features.commands.parts.Command;
import discord.bot.features.commands.parts.CommandRequirement;
import discord.bot.features.commands.parts.Executable;
import discord.logger.DiscordLogger;
import discord.utility.BotUtility;
import discord.utility.Description;
import discord.utility.MemManager;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Permission;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PlaylistHandler {

	private DiscordLogger logger;

	private final List<Playlist> playlists;

	private Map<String, Command> commands;

	public PlaylistHandler(DiscordLogger logger){
		playlists = MemManager.loadPlaylists();
		this.logger = logger;
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

	//getter&setter
	public Map<String, Command> getCommands(){
		return commands;
	}

}
