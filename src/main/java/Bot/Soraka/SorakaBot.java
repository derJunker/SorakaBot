package Bot.Soraka;

import Bot.Logger.DiscordLogger;
import Bot.Utility.BotUtility;
import Bot.Utility.MemManager;
import Bot.Utility.Utility;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.channel.TextChannelDeleteEvent;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.MemberUpdateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveAllEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.json.response.ErrorResponse;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;

import java.util.*;
import java.util.stream.Collectors;

public class SorakaBot {

	//the bot stores itself as a user
	private static User self;

	private static DiscordLogger logger;

	//this list stores all channels which are so called joinChannels
	//these channels are unique to a server, and there the bot writes a message
	//used to self assign roles via reactions
	private static List<GuildMessageChannel> joinChannels = new ArrayList<>();

	//this map stores links between emojis and the associated roles, by Guild
	private static Map<Guild, Map<String, Role>> emojiRoles = new HashMap<>();
	private static Map<Guild, Map<String, Member>> emojiReactors = new HashMap<>();

	//emojis
	//currently hardcoded
	private final static String GAMER_EMOJI = "\uD83C\uDFAE";
	private final static String STUDENT_EMOJI = "\uD83D\uDCDA";

	//the names of the roles
	private final static String GAMER_NAME = "Elite-Gamer";
	private final static String STUDENT_NAME = "Student";


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

		//normally the emoji get loaded but its currently hardcoded
		fillEmojisToRoles();
		joinChannels = MemManager.loadJoinChannels(client);

		onReady();
		onReactionAdd();
		onReactionRemove();
		onReactionRemoveAll();
		onMemberUpdate();
		onMessageDelete();
		onTextChannelDelete();
		onGuildEvent();

		client.onDisconnect().block();
	}

	/**
	 * this method triggers when a reaction get added
	 */
	private static void onReactionAdd() {
		client.getEventDispatcher().on(ReactionAddEvent.class)
				.subscribe(event -> {
					//check if it is a joinChannel
					Channel joinChannel = event.getChannel().block();
					//get the emoji as a string
					ReactionEmoji emoji = event.getEmoji();
					Member member = event.getMember().get();
					Message message = event.getMessage().block();
					reactionAdded(emoji, member, message);
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

					reactionRemoved(emoji, member, message);
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
					ifAbsentCreateJoinMessage(channel);
				});
	}

	/**
	 * this method triggers when the bot joins a guild AND when the bot is online
	 */
	private static void onGuildEvent() {
		client.getEventDispatcher().on(GuildCreateEvent.class)
				.subscribe(event -> {
					createJoinChannel(event.getGuild());
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

					checkJoinReactions();
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
						String currentName = event.getCurrentNickname().orElse(member.getUsername());
						String oldName = event.getOld().get().getNickname().orElse(member.getUsername());
						if(!currentName.equals(oldName)){
							logger.log("Renamed the bot from **" + oldName + "** to **" + currentName + "**", guild);
							//update the JoinMessage of the guild
							updateJoinMessage(guild);
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
					ifAbsentCreateJoinMessage(channel);
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
					if(joinChannels.contains(joinChannel)){
						joinChannels.remove(joinChannel);
						Guild guild = joinChannel.getGuild().block();
						createJoinChannel(guild);
						logger.log("joinChannel created", guild);
					}
				});
	}

	/**
	 * this method simulates a that a reaction has been added
	 * somebody could've really added a reaction at that point, but it doesn't have to be
	 * @param emoji the emoji which was added as a reaction
	 * @param member the person who reacted
	 * @param message the message on which was reacted
	 */
	private static void reactionAdded(ReactionEmoji emoji, Member member, Message message){
		Channel joinChannel = message.getChannel().block();
		Guild guild = member.getGuild().block();
		//if the reaction was added somewhere not in a joinChannel, then its not important
		if(!joinChannels.contains(joinChannel))
			return;
		//if this wasn't the joinMessage
		if(!message.equals(findJoinMessage((GuildMessageChannel) joinChannel)))
			return;
		//check if the member is the bot then also cancel this method

		if(BotUtility.sameUser(self, member))
			return;


		//and now get the role connected to that emoji, also check if there was an entry
		Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);
		Role role = null;
		String rawEmoji = emoji.asUnicodeEmoji().orElse(ReactionEmoji.Unicode.unicode("customEmoji")).getRaw();
		if(guildEmojiRoles != null)
			role = guildEmojiRoles.get(rawEmoji);

		//check if there is no role assigned to the emoji
		//then remove the emoji again
		if(role == null){
			logger.log("The emoji: " + rawEmoji + "was added which has no role assigned to it by: " + member.getNickname().orElse(member.getUsername())
					, guild);
			//remove the reaction if possible (could be missing permissions if the admin does it (or a role higher than him)
			try {
				message.removeReactions(emoji).block();
			}
			catch(ClientException e){
				ErrorResponse resp = e.getErrorResponse().orElse(null);
				String errorMessage = "no error response";
				if(resp != null){
					errorMessage = e.getErrorResponse().get().toString();
				}
				logger.log(errorMessage);
			}
			return;
		}
		//now assign the role to the user
		member.addRole(role.getId()).block();

		logger.log("Assigned role: **" + role.getName() + "** to: **" + member.getNickname().orElse(member.getUsername()) + "**", guild);
	}

	/**
	 * this method simulates a that a reaction has been removed
	 * somebody could've really removed a reaction at that point, but it doesn't have to be
	 * @param emoji the emoji which was removed as a reaction
	 * @param member the person who reacted
	 * @param message the message on which was reacted
	 */
	private static void reactionRemoved(ReactionEmoji emoji, Member member, Message message){
		Guild guild = member.getGuild().block();
		String rawEmoji = emoji.asUnicodeEmoji().orElse(ReactionEmoji.unicode("customEmoji")).getRaw();
		//and now get the role connected to that emoji, also check if there was an entry
		Role role = emojiRoles.get(guild).get(rawEmoji);

		Channel joinChannel = message.getChannel().block();

		//if the reaction was removed somewhere not in a joinChannel, then its not important
		if(!joinChannels.contains(joinChannel))
			return;
		//if this wasn't the joinMessage
		if(!message.equals(findJoinMessage((GuildMessageChannel) joinChannel)))
			return;


		//check if there is no role assigned to the emoji or the user doesn't have the role
		if(role == null || member.getRoles().filter(role::equals).blockFirst() == null){
			logger.log("The emoji: " + rawEmoji + "was removed which has no role assigned to it by: " + member.getNickname().orElse(member.getUsername())
					, guild);
			return;
		}

		//now assign the role to the user
		member.removeRole(role.getId()).block();

		logger.log("Removed role: **" + role.getName() + "** from: **" + member.getNickname().orElse(member.getUsername()) + "**", guild);
	}

	/**
	 * this method adds a joinMessage to a channel If the channel is a joinChannel, and there is no joinMessage already
	 * @param channel the channel where to create a joinMessage
	 */
	private static void ifAbsentCreateJoinMessage(MessageChannel channel){
		if(joinChannels.contains(channel)){
			GuildMessageChannel joinChannel = (GuildMessageChannel) channel;
			if(findJoinMessage(joinChannel) == null){
				createJoinMessage(joinChannel);
				logger.log("New joinMessage created", joinChannel.getGuild().block());
			}
		}
	}

	/**
	 * this method updates the joinMessage of the joinChannel, by editing in an updated version of the context
	 * e.g.: when the amount of roles changed
	 * @param guild the guild where the joinChannel is
	 */
	private static void updateJoinMessage(Guild guild) {
		//getting the joinChannel
		GuildMessageChannel joinChannel = findJoinChannel(guild);
		if(joinChannel == null)
			return;

		Message joinMessage = findJoinMessage(joinChannel);
		if(joinMessage != null) {
			//updating the message to the correct one
			joinMessage.edit(message -> message.setContent(makeJoinMessageContent(guild))).block();
		}
	}

	/**
	 * this method finds the joinChannel for a guild
	 * @param guild the guild where the joinChannel should be
	 * @return the joinChannel, or null if there is no joinChannel
	 */
	private static GuildMessageChannel findJoinChannel(Guild guild){
		return guild.getChannels()
						.filter(channel -> channel instanceof GuildMessageChannel)
						.map(channel -> (GuildMessageChannel) channel)
						.filter(channel -> joinChannels.contains(channel)).blockFirst();
	}

	/**
	 * this method finds the method with all
	 * @param channel the channel where the joinMessage should be
	 * @return the message or null if not found
	 */
	private static Message findJoinMessage(GuildMessageChannel channel){
		//basically find the first message sent by the bot
		//with all the right emojis
		Guild guild = channel.getGuild().block();

		List<Message> messages = BotUtility.getMessagesOfChannel(channel);
		//getting all emojis which are linked to a role, into a list,
		//which will be used to check if a message is the join message, by checking if the message
		//as all the guildEmojis
		Map<String, Role> emojiGuildRoles = emojiRoles.get(guild);
		if(emojiGuildRoles == null) {
			fillEmojisToRoles();
			emojiGuildRoles = emojiRoles.get(guild);
			if(emojiGuildRoles == null)
				emojiGuildRoles = new HashMap<>();
		}
		List<String> guildEmojis = Utility.getKeys(emojiGuildRoles);
		Message joinMessage = messages.stream()
								.filter(message -> BotUtility.sameUser(message.getAuthor().get(), self))
								.filter(message -> {
									//getting all the raw strings of the emojis on the message
									List<String> messageEmojis = message.getReactions()
																			.stream()
																			.filter(reaction -> reaction.getEmoji().asUnicodeEmoji().isPresent())
																			.map(reaction -> reaction.getEmoji().asUnicodeEmoji().get().getRaw())
																			.collect(Collectors.toList());
									return messageEmojis.containsAll(guildEmojis);
								})
								.findFirst().orElse(null);
		return joinMessage;
	}

	/**
	 * this method creates a joinChannel for a guild if there isn't already one
	 * and saves it
	 * @param guild the guild where the joinChannel should be created
	 */
	private static void createJoinChannel(final Guild guild){
		//check if the guild already has a joinChannel, if so then just stop the program
		GuildMessageChannel existingChannel = joinChannels.stream()
											.filter(channel -> BotUtility.sameGuildId(channel.getGuild().block(), guild))
											.findFirst().orElse(null);
		if(existingChannel != null){
			//if there is a joinChannel also check if there i a joinMessage
			if(findJoinMessage(existingChannel) == null){
				createJoinMessage(existingChannel);
			}
			return;
		}
		//now if there is no joinChannel, then actually create a join channel with the right permissions
		//so first set up the permissions
		//this set will be given to the textChannel as the permissions
		final Set<PermissionOverwrite> permissions = new HashSet<>();
		Role role;
		PermissionSet allowed;
		PermissionSet denied;
		//
		//adding the the permissions of the everyone role (they can just read messages and add reactions
		role = guild.getEveryoneRole().block();
		allowed = PermissionSet.of(Permission.VIEW_CHANNEL, Permission.READ_MESSAGE_HISTORY);
		denied = PermissionSet.all();
		PermissionOverwrite everyoneRolePermissions = PermissionOverwrite.forRole(role.getId(), allowed, denied);
		permissions.add(everyoneRolePermissions);
		//

		//adding the permission for the integrated role of the bot
		Member botAsMember = self.asMember(guild.getId()).block();
		//now get the integrated role, if for some reason there isn't one then stop the method
		//the integrated role is found, by the attribute managed of a role which models exactly that
		role = botAsMember.getRoles().toStream()
										.filter(Role::isManaged)
										.findFirst()
										.orElse(null);
		if(role == null){
			logger.log("No integrated role found for the bot", guild);
			return;
		}
		//the permissions of the bot
		allowed = PermissionSet.of(Permission.VIEW_CHANNEL, Permission.ADD_REACTIONS, Permission.SEND_MESSAGES, Permission.READ_MESSAGE_HISTORY, Permission.MANAGE_MESSAGES);
		denied = PermissionSet.none();
		//now saving the allowed and denied messages
		PermissionOverwrite botPermissions = PermissionOverwrite.forRole(role.getId(), allowed, denied);
		permissions.add(botPermissions);

		//finally making a new textChannel, with the name "join" and the right permissions
		GuildMessageChannel joinChannel = guild.createTextChannel(textChannel -> textChannel
																				.setName("join")
																				.setPermissionOverwrites(permissions)).block();

		//now send the message
		createJoinMessage(joinChannel);
		//finally adding it to the joinChannel list
		joinChannels.add(joinChannel);
		//and saving the joinChannels afterwards
		MemManager.saveJoinChannels(joinChannels, client);
	}

	/**
	 * this method creates the join message in a channel
	 * @param joinChannel the channel where the message should be created
	 */
	private static void createJoinMessage(GuildMessageChannel joinChannel){
		Guild guild = joinChannel.getGuild().block();
		final String content = makeJoinMessageContent(guild);
		joinChannel.createMessage(content).subscribe(message -> addRoleEmojis(message));
	}

	/**
	 * this method makes the message which gets displayed on the joinChannel of the guild
	 * @param guild the guild where the joinChannel should be
	 */
	private static String makeJoinMessageContent(Guild guild){
		Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);

		String botNameInGuild = guild.getMemberById(self.getId()).block().getNickname().orElse(self.getUsername());
		String content = "Welcome to **" + guild.getName() + "** :wave_tone3:,\n" +
						"This is the join-channel, where you can choose your Roles!\n" +
						"Depending on which roles you choose you unlock different voice and text channels.\n" +
						"Note that this only works if this bot (**" + botNameInGuild + " [BOT]**) is online!\n" +
						"Possible roles to choose from are:\n";
		if(guildEmojiRoles != null)
			for(Map.Entry<String, Role> entry : guildEmojiRoles.entrySet()){
				content += entry.getKey() + ": for the **" + entry.getValue().getName() + "** role\n";
			}

		content += "Just click the emojis below for the right role glhf!";
		return content;
	}

	/**
	 * it adds the emojis used to assign roles to a message
	 * @param msg this is the message
	 */
	private static void addRoleEmojis(Message msg){
		msg.addReaction(ReactionEmoji.unicode(GAMER_EMOJI)).subscribe();
		msg.addReaction(ReactionEmoji.unicode(STUDENT_EMOJI)).subscribe();
	}

	/**
	 * this method links all the emojis to the right roles
	 */
	private static void fillEmojisToRoles(){
		//for every guild add the roles
		client.getGuilds().toStream()
				.forEach(guild -> {
					HashMap<String, Role> guildEmojiRoles = new HashMap<>();
					//do it both for the gamer role as well as the student role
					//first get the role with the right name
					Role gamer = BotUtility.getRoleByName(GAMER_NAME, guild);
					if(gamer != null){
						//then add the right emoji to the role into the map
						guildEmojiRoles.put(GAMER_EMOJI, gamer);
					}

					//first get the role with the right name
					Role student = BotUtility.getRoleByName(STUDENT_NAME, guild);
					if(student != null){
						//then add the right emoji to the role into the map
						guildEmojiRoles.put(STUDENT_EMOJI, student);
					}
					emojiRoles.put(guild, guildEmojiRoles);
				});
	}

	/**
	 * this method checks if while the bot was offline, new people reacted to the joinMessages of the guilds
	 * and if people retracted their reaction, if so then
	 */
	private static void checkJoinReactions(){

	}
 }