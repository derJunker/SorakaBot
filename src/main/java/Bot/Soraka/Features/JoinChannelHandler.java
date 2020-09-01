package Bot.Soraka.Features;

import Bot.Logger.DiscordLogger;
import Bot.Soraka.SorakaBot;
import Bot.Utility.BotUtility;
import Bot.Utility.MemManager;
import Bot.Utility.Utility;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.reaction.Reaction;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.json.response.ErrorResponse;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;

import java.util.*;

import static java.util.stream.Collectors.toList;

public class JoinChannelHandler {

	private DiscordLogger logger;
	private GatewayDiscordClient client;

	//this list stores all channels which are so called joinChannels
	//these channels are unique to a server, and there the bot writes a message
	//used to self assign roles via reactions
	private List<GuildMessageChannel> joinChannels = new LinkedList<>();

	//emojis
	//currently hardcoded
	private final static String GAMER_EMOJI = "\uD83C\uDFAE";
	private final static String STUDENT_EMOJI = "\uD83D\uDCDA";

	//the names of the roles
	private final static String GAMER_NAME = "Elite-Gamer";
	private final static String STUDENT_NAME = "Student";

	//this map stores links between emojis and the associated roles, by Guild
	private final Map<Guild, Map<String, Role>> emojiRoles = new HashMap<>();

	public JoinChannelHandler(DiscordLogger logger, GatewayDiscordClient client){
		this.logger = logger;
		this.client = client;
		joinChannels = MemManager.loadJoinChannels(client);
		linkEmojisToRoles();
	}

	//----------------------------------------joinChannel-related----------------------------------------

	/**
	 * this method creates a joinChannel for a guild if there isn't already one
	 * and saves it
	 * @param guild the guild where the joinChannel should be created
	 */
	public void createJoinChannel(final Guild guild){
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
		Member botAsMember = SorakaBot.getSelf().asMember(guild.getId()).block();
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
	 * this method makes the message which gets displayed on the joinChannel of the guild
	 * @param guild the guild where the joinChannel should be
	 */
	private String makeJoinMessageContent(Guild guild){
		Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);

		String botNameInGuild = guild.getMemberById(SorakaBot.getSelf().getId()).block().getNickname().orElse(SorakaBot.getSelf().getUsername());
		String content = "Welcome to **" + guild.getName() + "** :wave_tone3:,\n" +
				"This is the join-channel, where you can choose your Roles!\n" +
				"Depending on which roles you choose you unlock different voice and text channels.\n" +
				"Note if this bot (**" + botNameInGuild + " [BOT]**) isn't online it could take a while until you get your role!\n" +
				"Possible roles to choose from are:\n";
		if(guildEmojiRoles != null)
			for(Map.Entry<String, Role> entry : guildEmojiRoles.entrySet()){
				content += entry.getKey() + ": for the **" + entry.getValue().getName() + "** role\n";
			}

		content += "Just click the emojis below for the right role glhf!";
		return content;
	}

	/**
	 * this method finds the joinChannel for a guild
	 * @param guild the guild where the joinChannel should be
	 * @return the joinChannel, or null if there is no joinChannel
	 */
	private GuildMessageChannel findJoinChannel(Guild guild){
		return guild.getChannels()
				.filter(channel -> channel instanceof GuildMessageChannel)
				.map(channel -> (GuildMessageChannel) channel)
				.filter(channel -> joinChannels.contains(channel)).blockFirst();
	}

	//--------------------------------------end: joinChannel-related--------------------------------------

	//-----------------------------------------joinMessage-related-----------------------------------------

	/**
	 * this method finds the joinMessage of a guild (if there is a joinChannel
	 * @param guild the guild where the message should be
	 * @return the message or null if not found
	 */
	private Message findJoinMessage(Guild guild){
		GuildMessageChannel joinChannel = findJoinChannel(guild);
		if(joinChannel == null)
			return null;
		return findJoinMessage(joinChannel);
	}

	/**
	 * this method finds the method with all
	 * @param channel the channel where the joinMessage should be
	 * @return the message or null if not found
	 */
	private Message findJoinMessage(GuildMessageChannel channel){
		//basically find the first message sent by the bot
		//with all the right emojis
		Guild guild = channel.getGuild().block();

		List<Message> messages = BotUtility.getMessagesOfChannel(channel);
		//getting all emojis which are linked to a role, into a list,
		//which will be used to check if a message is the join message, by checking if the message
		//as all the guildEmojis
		Map<String, Role> emojiGuildRoles = emojiRoles.get(guild);
		if(emojiGuildRoles == null) {
			linkEmojisToRoles();
			emojiGuildRoles = emojiRoles.get(guild);
			if(emojiGuildRoles == null)
				emojiGuildRoles = new HashMap<>();
		}
		List<String> guildEmojis = Utility.getKeys(emojiGuildRoles);
		Message joinMessage = messages.stream()
				.filter(message -> BotUtility.sameUser(message.getAuthor().get(), SorakaBot.getSelf()))
				.filter(message -> {
					//getting all the raw strings of the emojis on the message
					List<String> messageEmojis = message.getReactions()
							.stream()
							.filter(reaction -> reaction.getEmoji().asUnicodeEmoji().isPresent())
							.map(reaction -> reaction.getEmoji().asUnicodeEmoji().get().getRaw())
							.collect(toList());
					return messageEmojis.containsAll(guildEmojis);
				})
				.findFirst().orElse(null);
		return joinMessage;
	}

	/**
	 * this method adds a joinMessage to a channel If the channel is a joinChannel, and there is no joinMessage already
	 * @param channel the channel where to create a joinMessage
	 */
	public void ifAbsentCreateJoinMessage(MessageChannel channel){
		if(joinChannels.contains(channel)){
			GuildMessageChannel joinChannel = (GuildMessageChannel) channel;
			if(findJoinMessage(joinChannel) == null){
				createJoinMessage(joinChannel);
				logger.log("New joinMessage created", joinChannel.getGuild().block());
			}
		}
	}

	/**
	 * this method creates the join message in a channel
	 * @param joinChannel the channel where the message should be created
	 */
	private void createJoinMessage(GuildMessageChannel joinChannel){
		Guild guild = joinChannel.getGuild().block();
		final String content = makeJoinMessageContent(guild);
		joinChannel.createMessage(content).subscribe(message -> addRoleEmojis(message));
	}

	/**
	 * this method updates the joinMessage of the joinChannel, by editing in an updated version of the context
	 * e.g.: when the amount of roles changed
	 * @param guild the guild where the joinChannel is
	 */
	public void updateJoinMessage(Guild guild) {
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

	//--------------------------------------end: joinMessage-related--------------------------------------

	//----------------------------------------reaction-related----------------------------------------

	/**
	 * it adds the emojis used to assign roles to a message
	 * @param msg this is the message
	 */
	private void addRoleEmojis(Message msg){
		msg.addReaction(ReactionEmoji.unicode(GAMER_EMOJI)).subscribe();
		msg.addReaction(ReactionEmoji.unicode(STUDENT_EMOJI)).subscribe();
	}

	/**
	 * this method simulates a that a reaction has been removed
	 * somebody could've really removed a reaction at that point, but it doesn't have to be
	 * @param emoji the emoji which was removed as a reaction
	 * @param member the person who reacted
	 * @param message the message on which was reacted
	 */
	public void reactionRemoved(ReactionEmoji emoji, Member member, Message message){
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

		MemManager.saveEmojiReactors(getCurrentEmojiReactors());
		logger.log("Removed role: **" + role.getName() + "** from: **" + member.getNickname().orElse(member.getUsername()) + "**", guild);
	}

	/**
	 * this method checks if while the bot was offline, new people reacted to the joinMessages of the guilds
	 * and if people retracted their reaction, if so then
	 */
	public void checkJoinReactions(){
		//get the old and current reactors
		Map<Guild, Map<String, List<Member>>> oldEmojiReactors = MemManager.loadEmojiReactors(client);
		//if there was no file containing the emojiReactors then stop this
		if(oldEmojiReactors == null)
			return;
		Map<Guild, Map<String, List<Member>>> currentEmojiReactors = getCurrentEmojiReactors();

		//now go through every guild, emoji and check if there is a difference in Members
		currentEmojiReactors.forEach((guild, emojiReactorsByGuild) -> {
			//get the message which contains the reactions
			Message joinMessage = findJoinMessage(guild);
			//if there is none then return (this should never happens because the currentEmojiReactors just have entries if there is a channel
			if(joinMessage == null)
				return;

			emojiReactorsByGuild.forEach((rawEmoji, currentReactors) -> {
				//get the reactors of the same guild and emoji of the oldReactors
				Map<String, List<Member>> oldEmojiReactorsByGuild = oldEmojiReactors.get(guild);
				//if there is no entry simulate an empty entry
				if(oldEmojiReactorsByGuild == null)
					oldEmojiReactorsByGuild = new HashMap<>();
				List<Member> oldReactors = oldEmojiReactorsByGuild.get(rawEmoji);
				if(oldReactors == null)
					oldReactors = new LinkedList<>();

				//for every emoji get the differences of the old and current reactors (both ways)
				//if you take current / old then you get the new added reactors
				//if you take old / current you get the removed reactors
				List<Member> newReactors = Utility.listDifference(currentReactors, oldReactors);
				List<Member> removedReactors = Utility.listDifference(oldReactors, currentReactors);
				//now simulate the ReactionAdd event for the new Reactors, and same to the removedReactors
				newReactors.forEach(member -> {
					//finding the ReactionEmoji
					reactionAdded(ReactionEmoji.unicode(rawEmoji), member, joinMessage);
				});
				removedReactors.forEach(member -> {
					//finding the ReactionEmoji
					reactionRemoved(ReactionEmoji.unicode(rawEmoji), member, joinMessage);
				});
			});
		});

	}

	/**
	 * this method simulates a that a reaction has been added
	 * somebody could've really added a reaction at that point, but it doesn't have to be
	 * @param emoji the emoji which was added as a reaction
	 * @param member the person who reacted
	 * @param message the message on which was reacted
	 */
	public void reactionAdded(ReactionEmoji emoji, Member member, Message message){
		Channel joinChannel = message.getChannel().block();
		Guild guild = member.getGuild().block();
		//if the reaction was added somewhere not in a joinChannel, then its not important
		if(!joinChannels.contains(joinChannel))
			return;
		//if this wasn't the joinMessage
		if(!message.equals(findJoinMessage((GuildMessageChannel) joinChannel)))
			return;
		//check if the member is the bot then also cancel this method

		if(BotUtility.sameUser(SorakaBot.getSelf(), member))
			return;


		//and now get the role connected to that emoji, also check if there was an entry
		Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);
		Role role = null;
		String rawEmoji = emoji.asUnicodeEmoji().orElse(ReactionEmoji.unicode("customEmoji")).getRaw();
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

		MemManager.saveEmojiReactors(getCurrentEmojiReactors());
		logger.log("Assigned role: **" + role.getName() + "** to: **" + member.getNickname().orElse(member.getUsername()) + "**", guild);
	}

	/**
	 * this method gets all people which have their reactions on a JoinMessage
	 * grouped first by guild, and then by emoji
	 * this is done by a map
	 * @return returns this map
	 */
	private Map<Guild, Map<String, List<Member>>> getCurrentEmojiReactors(){
		//first get every joinMessage
		//then add for every message, for every emoji the reactor as an entry of the inner map
		final Map<Guild, Map<String, List<Member>>> emojiReactors = new HashMap<>();
		joinChannels.forEach(channel -> {
			final Guild guild = channel.getGuild().block();
			//this stores the members which reacted to the emoji, for a certain guild
			final Map<String, List<Member>> emojiReactorsByGuild = new HashMap<>();
			final Message joinMessage = findJoinMessage(channel);
			//check if there even is a joinMessage, if not then there were no new members (technically every role should be removed
			//but i wont do that
			if(joinMessage == null)
				return;

			//now go through every reaction emoji and get the reactors
			joinMessage.getReactions().stream()
					.map(Reaction::getEmoji)
					.forEach(emoji -> {
						final List<Member> reactorsByEmoji = new LinkedList<>();
						//getting the reactors of the guild, by first getting all users who reacted
						//then finding them in the guild
						//but they don't have to be in there (they could've left and the reaction stays)
						//so check if they are still in, then get the member
						//then add every member to the list of members who reacted
						joinMessage.getReactors(emoji).toStream()
								.map(user -> guild.getMemberById(user.getId()).blockOptional())
								.filter(Optional::isPresent)
								.map(Optional::get)
								.forEach(reactorsByEmoji::add);
						//get the raw version of the emoji
						String rawEmoji = emoji.asUnicodeEmoji().orElse(ReactionEmoji.unicode("customEmoji")).getRaw();
						//now put this entry into the emojiReactorsByGuild
						emojiReactorsByGuild.put(rawEmoji, reactorsByEmoji);

					});

			//now putting this map into the map of the emojiReactors
			emojiReactors.put(guild, emojiReactorsByGuild);
		});

		//finally return the map
		return emojiReactors;
	}

	/**
	 * this method links all the emojis to the right roles
	 */
	private void linkEmojisToRoles(){
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

	//--------------------------------------end: reaction-related----------------------------------------

	//----------------------------------------getter&setter----------------------------------------

	public List<GuildMessageChannel> getJoinChannels(){
		return joinChannels;
	}
}
