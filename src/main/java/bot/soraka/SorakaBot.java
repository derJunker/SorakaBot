package bot.soraka;

import bot.logger.DiscordLogger;
import bot.soraka.features.RoleAssignHandler;
import bot.soraka.features.commands.CommandHandler;
import bot.utility.BotUtility;
import bot.utility.MemManager;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.channel.TextChannelDeleteEvent;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.MemberUpdateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import discord4j.core.object.reaction.ReactionEmoji;

public class SorakaBot {

	//the bot stores itself as a user
	private static User self;

	private static DiscordLogger logger;
	private static RoleAssignHandler roleAssignHandler;
	private static CommandHandler commandHandler;

	//the client
	private static GatewayDiscordClient client;

	public static void main(String[] args){
		client = DiscordClientBuilder.create(args[0])
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

		initEvents();

		client.onDisconnect().block();
	}

	/**
	 * it sets up all events, for event-handling
	 */
	private static void initEvents(){
		onReady();
		onMessageCreate();
		onReactionAdd();
		onReactionRemove();
		onReactionRemoveAll();
		onMemberUpdate();
		onMessageDelete();
		onTextChannelDelete();
		onGuildCreate();
	}

	/**
	 * when a message gets sent
	 */
	private static void onMessageCreate(){
		client.getEventDispatcher().on(MessageCreateEvent.class)
				.subscribe(event -> {
					commandHandler.execute(event.getMessage());
				});
	}

	/**
	 * this method triggers when a reaction get added
	 */
	private static void onReactionAdd() {
		client.getEventDispatcher().on(ReactionAddEvent.class)
				.subscribe(event -> {
					//get the emoji as a string
					ReactionEmoji emoji = event.getEmoji();
					Member member = event.getMember().get();
					Message message = event.getMessage().block();
					roleAssignHandler.reactionAdded(emoji, member, message);
				});
	}

	/**
	 * when a reaction gets removed
	 */
	private static void onReactionRemove(){
		client.getEventDispatcher().on(ReactionRemoveEvent.class)
				.subscribe(event -> {
					Message message = event.getMessage().block();
					ReactionEmoji emoji = event.getEmoji();
					Guild guild = event.getGuild().block();
					Member member = guild.getMemberById(event.getUserId()).block();

					roleAssignHandler.reactionRemoved(emoji, member, message);
				});
	}

	/**
	 * when all reactions get removed from a message
	 */
	private static void onReactionRemoveAll(){
		client.getEventDispatcher().on(ReactionRemoveAllEvent.class)
				.subscribe(event -> {
					MessageChannel channel = event.getChannel().block();
					//this is to check if a person removed the reactions from the joinMessage
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
					logger.log(event);
					//the bot just saves its user data
					self = event.getSelf();

					roleAssignHandler.checkJoinReactions();
				});
	}

	/**
	 * what happens when the nickname of a member gets changed or the member gets a new role
	 */
	private static void onMemberUpdate(){
		client.getEventDispatcher().on(MemberUpdateEvent.class)
				.subscribe(event -> {
					//check if the bot was renamed because then the joinMessage has to be updated
					Guild guild = event.getGuild().block();
					Member member = event.getMember().block();
					//check if the member updated was the bot
					if(BotUtility.sameUser(self, member)){
						//now check if the update was a rename
						String currentName = BotUtility.getNameInGuild(member);
						String oldName = BotUtility.getNameInGuild(event.getOld().get());
						if(!currentName.equals(oldName)){
							logger.log("Renamed the bot from **" + oldName + "** to **" + currentName + "**", guild);
							//update the JoinMessage of the guild
							roleAssignHandler.updateJoinMessage(guild);
						}
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