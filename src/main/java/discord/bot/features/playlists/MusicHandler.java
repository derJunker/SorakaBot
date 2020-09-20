package discord.bot.features.playlists;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord.bot.features.commands.parts.Command;
import discord.bot.features.commands.parts.CommandRequirement;
import discord.bot.features.commands.parts.Executable;
import discord.bot.features.playlists.lavaplayer.LavaPlayerAudioProvider;
import discord.bot.features.playlists.lavaplayer.TrackScheduler;
import discord.utility.Description;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.voice.AudioProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MusicHandler {

	//effectively final
	private AudioPlayerManager playerManager;
	private AudioProvider provider;
	private TrackScheduler scheduler;

	//the list of commands and the string representation of it
	private Map<String, Command> commands;

	public MusicHandler(){
		initLavaPlayer();

		initCommands();
	}

	private void initLavaPlayer(){
		// Creates AudioPlayer instances and translates URLs to AudioTrack instances
		playerManager = new DefaultAudioPlayerManager();
		// This is an optimization strategy that Discord4J can utilize. It is not important to understand
		playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
		// Allow playerManager to parse remote sources like YouTube links
		AudioSourceManagers.registerRemoteSources(playerManager);
		// Create an AudioPlayer so Discord4J can receive audio data
		AudioPlayer player = playerManager.createPlayer();
		// We will be creating LavaPlayerAudioProvider in the next step
		provider = new LavaPlayerAudioProvider(player);
		//creating the trackScheduler
		scheduler = new TrackScheduler(player);
	}

	//-------------------------------------commands-------------------------------------
	private void initCommands(){
		commands = new HashMap<>();
		addCommandJoinChannel();
		addCommandPlaySong();
	}

	private void addCommandJoinChannel(){
		final String name = "join";
		Description description = new Description("joins your Voice-Channel if you are in one");
		//syntax is !join
		CommandRequirement syntax = CommandRequirement.correctSyntaxSegmentAmount(1);
		CommandRequirement inGuild = CommandRequirement.IN_GUILD;
		List<CommandRequirement> requirements = List.of(syntax, inGuild);

		Executable executable = msg -> {
			//first the author as a member, bc this message has to be sent in a guild
			Member author  = msg.getAuthorAsMember().block();
			//checking if the member is in a voiceChannel of the guild
			VoiceState voiceState = author.getVoiceState().block();
			if(voiceState != null){
				VoiceChannel channel = voiceState.getChannel().block();
				if(channel != null){
					//if the author of the channel, is in a voiceChannel then join it
					channel.join(spec -> spec.setProvider(provider)).block();
				}
			}
		};

		commands.put(name, new Command(requirements, executable, description));

	}

	private void addCommandPlaySong(){
		final String name = "play";
		Description description = new Description("plays a song from youtube, only links allowed");

		//requirements
		//syntax: !play $url
		CommandRequirement syntax = CommandRequirement.correctSyntaxSegmentAmount(2);
		CommandRequirement inGuild = CommandRequirement.IN_GUILD;
		List<CommandRequirement> requirements = List.of(syntax, inGuild);

		Executable executable = msg -> {
			String content = msg.getContent();
			String[] segments = content.split(" "); //this array has length 2 (checked by requirement)
			playerManager.loadItem(segments[1], scheduler);
		};

		Command command = new Command(requirements, executable, description);
		commands.put(name, command);
	}

	//----------------------------------end-of-commands----------------------------------

	/**
	 * leaves the voiceChannel if no users(bots not included) are in it
	 * @param channel the voiceChannel to check
	 */
	public void disconnectOnEmptyChannel(VoiceChannel channel){
		//check if all members which are in the voiceChannel are bots
		//if so disconnect
		boolean allBots = channel.getVoiceStates()
				.map(state -> state.getMember().block())
				.all(member -> member.isBot()).block();
		//if there are still members in there then do nothing
		if(!allBots)
			return;

		channel.sendDisconnectVoiceState().block();
	}

	public Map<String, Command> getCommands(){
		return commands;
	}

}
