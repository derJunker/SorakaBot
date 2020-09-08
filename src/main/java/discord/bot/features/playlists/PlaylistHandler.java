package discord.bot.features.playlists;

import discord.bot.features.commands.parts.Command;
import discord.bot.features.commands.parts.CommandRequirement;
import discord.bot.features.commands.parts.Executable;
import discord.logger.DiscordLogger;
import discord.utility.Description;
import discord.utility.MemManager;
import discord4j.core.object.entity.channel.MessageChannel;

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

		Playlist test = new Playlist("Test");
		test.add("yay");
		test.add("dope");
		playlists.add(test);
	}

	private void initCommands(){
		commands = new HashMap<>();
		addCommandCreatePlaylist();
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
		List<CommandRequirement> requirements = List.of(syntax);

		Description description = new Description("creates a new Playlist");

		Executable executable = message -> {
			MessageChannel channel = message.getChannel().block();
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

			//now create the new Playlist and add it to the list of playlists
			playlists.add(new Playlist(playlistName));
			//also save the playlists
			MemManager.savePlaylists(playlists);
		};

		commands.put(name, new Command(requirements, executable, description));
	}

	//getter&setter
	public Map<String, Command> getCommands(){
		return commands;
	}

}
