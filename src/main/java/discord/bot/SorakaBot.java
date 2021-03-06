package discord.bot;

import discord.bot.features.playlists.MusicHandler;
import discord.logger.DiscordLogger;
import discord.bot.features.RoleAssignHandler;
import discord.bot.features.commands.CommandHandler;
import discord.utility.BotUtility;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.channel.TextChannelDeleteEvent;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.MemberUpdateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import discord4j.core.object.reaction.ReactionEmoji;

import java.util.Optional;

public class SorakaBot {

	//the bot stores itself as a user
	private static User self;

	private static DiscordLogger logger;
	private static CommandHandler commandHandler;
	//the different features
	private static RoleAssignHandler roleAssignHandler;
	private static MusicHandler musicHandler;

	//the client
	private static GatewayDiscordClient client;

	public static void main(String[] args){
		client = DiscordClientBuilder.create(System.getenv("BOT_TOKEN"))
				.build()
				.login()
				.block();

		//initiating the logger
		//first getting the guild "DieLolMains" where the bot should have a logChannel
		Guild dieLolMains = client.getGuildById(Snowflake.of(273116206314291210L)).block();
		MessageChannel logChannel = (MessageChannel) BotUtility.getGuildChannelByName("soraka_bot_log", dieLolMains);
		logger = new DiscordLogger(logChannel);

		commandHandler = new CommandHandler(logger, client);
		roleAssignHandler = new RoleAssignHandler(logger, client);
		musicHandler = new MusicHandler();
		//adding the commands
		commandHandler.addCommands(roleAssignHandler.getCommands());
		commandHandler.addCommands(musicHandler.getCommands());

		initEvents();

		client.onDisconnect().block();
	}

	/**
	 * it sets up all events, for event-handling
	 */
	private static void initEvents(){
		client.getEventDispatcher().on(Event.class)
				.subscribe(System.out::println);
		onReady();
		onMessageCreate();
		onReactionAdd();
		onReactionRemove();
		onReactionRemoveAll();
		onMemberUpdate();
		onMessageDelete();
		onTextChannelDelete();
		onGuildCreate();
		onVoiceStateUpdate();
	}

	/**
	 * when a message gets sent
	 */
	private static void onMessageCreate(){
		client.getEventDispatcher().on(MessageCreateEvent.class)
				.subscribe(event -> {
					Message message = event.getMessage();
					MessageChannel channel = message.getChannel().block();
					//if the message was sent by this bot ignore it
					if(BotUtility.sameUser(self, message.getAuthor().get())){
						return;
					}
					//execute the command of the message if it is one
					//if not tell the user its an unknown command
					if(!commandHandler.execute(message)){
						channel.createMessage("Unknown Command! use the \"help\" command for all the commands").block();
					}
				});
	}

	/**
	 * this method triggers when a reaction get added
	 */
	private static void onReactionAdd() {
		client.getEventDispatcher().on(ReactionAddEvent.class)
				.subscribe(event -> {
					logger.log("ReactionAddEvent triggered", event.getMessage().block());
					Optional<Guild> optGuild = event.getGuild().blockOptional();
					if(optGuild.isPresent()) {
						//get the emoji as a string
						ReactionEmoji emoji = event.getEmoji();
						Member member = event.getMember().get();
						Message message = event.getMessage().block();
						roleAssignHandler.reactionAdded(emoji, member, message);
					}
				});
	}

	/**
	 * when a reaction gets removed
	 */
	private static void onReactionRemove(){
		client.getEventDispatcher().on(ReactionRemoveEvent.class)
				.subscribe(event -> {
					logger.log("ReactionRemoveEvent triggered", event.getMessage().block());
					Optional<Guild> optGuild = event.getGuild().blockOptional();
					if(optGuild.isPresent()) {
						Message message = event.getMessage().block();
						ReactionEmoji emoji = event.getEmoji();
						Guild guild = optGuild.get();
						Member member = guild.getMemberById(event.getUserId()).block();

						roleAssignHandler.reactionRemoved(emoji, member, message);
					}
				});
	}

	/**
	 * when all reactions get removed from a message
	 */
	private static void onReactionRemoveAll(){
		client.getEventDispatcher().on(ReactionRemoveAllEvent.class)
				.subscribe(event -> {
					MessageChannel channel = event.getChannel().block();
					//this is to check if a person (not the bot) removed the reactions from the joinMessage
					//if so then the joinMessage can't be found anymore (no reactions) so create a new one
					roleAssignHandler.ifAbsentCreateJoinMessage(channel);
				});
	}

	/**
	 * this method triggers when the bot joins a guild AND when the bot is online
	 */
	private static void onGuildCreate() {
		client.getEventDispatcher().on(GuildCreateEvent.class)
				.subscribe(event -> {
					roleAssignHandler.createJoinChannel(event.getGuild());
				});
	}


	/**
	 * this is what happens if the bot is ready and running
	 */
	private static void onReady(){
		client.getEventDispatcher().on(ReadyEvent.class)
				.subscribe(event -> {
					//the bot just saves its user data
					self = event.getSelf();

					roleAssignHandler.checkJoinReactions();
					logger.log(event);
				});
	}

	/**
	 * what happens when the nickname of a member gets changed or the member gets a new role
	 */
	private static void onMemberUpdate(){
		client.getEventDispatcher().on(MemberUpdateEvent.class)
				.subscribe(event -> {
					Member member = event.getMember().block();
					if(BotUtility.botNameChanged(member, event.getOld().get())){
						Guild guild = event.getGuild().block();
						//update the JoinMessage of the guild
						roleAssignHandler.updateJoinMessage(guild);
					}
				});
	}

	/**
	 * what happens when a message gets deleted
	 */
	private static void onMessageDelete(){
		client.getEventDispatcher().on(MessageDeleteEvent.class)
				.subscribe(event -> {
					MessageChannel channel = event.getChannel().block();
					//check if the message deleted was the joinMessage of the joinChannel
					//if so then just create it again
					roleAssignHandler.ifAbsentCreateJoinMessage(channel);
				});
	}

	/**
	 * what happens when a TextChannel is deleted
	 */
	private static void onTextChannelDelete(){
		client.getEventDispatcher().on(TextChannelDeleteEvent.class)
				.subscribe(event ->{
					//check if the channel deleted was a joinChannel, if so then create a new joinChannel
					GuildMessageChannel joinChannel = event.getChannel();
					if(roleAssignHandler.getJoinChannels().remove(joinChannel)){
						Guild guild = joinChannel.getGuild().block();
						roleAssignHandler.createJoinChannel(guild);
						logger.log("joinChannel created", guild);
					}
				});
	}

	private static void onVoiceStateUpdate(){
		client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
				.subscribe(event -> {
					System.out.println("event");
					//check if a user leaves a voiceChannel
					//then the current VoiceChannel is null
					VoiceChannel oldChannel = event.getOld().get().getChannel().block();
					VoiceChannel currentChannel = event.getCurrent().getChannel().block();
					if(currentChannel == null){
						musicHandler.disconnectOnEmptyChannel(oldChannel);
					}
		});
	}


	//getter &setter

	public static GatewayDiscordClient getClient() {
		return client;
	}

	public static User getSelf(){
		return self;
	}

	public static DiscordLogger getLogger(){
		return logger;
	}
}