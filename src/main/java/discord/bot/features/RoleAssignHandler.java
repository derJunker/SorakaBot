package discord.bot.features;

import discord.bot.features.commands.parts.Command;
import discord.bot.features.commands.parts.CommandRequirement;
import discord.bot.features.commands.parts.Executable;
import discord.logger.DiscordLogger;
import discord.bot.SorakaBot;
import discord.utility.BotUtility;
import discord.utility.Description;
import discord.utility.MemManager;
import discord.utility.Utility;
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

public class RoleAssignHandler {

	private DiscordLogger logger;
	private GatewayDiscordClient client;

	//this list stores all channels which are so called joinChannels
	//these channels are unique to a server, and there the bot writes a message
	//used to self assign roles via reactions
	private List<GuildMessageChannel> joinChannels;

	//the map names and their commands for role assigning
	private Map<String, Command> commands;

	//this map stores links between emojis and the associated roles, by Guild
	private final Map<Guild, Map<String, Role>> emojiRoles;

	public RoleAssignHandler(DiscordLogger logger, GatewayDiscordClient client){
		this.logger = logger;
		this.client = client;
		joinChannels = MemManager.loadJoinChannels(client);
		emojiRoles = MemManager.loadEmojiRoles(client);
		initCommands();
	}

	//----------------------------------------joinChannel-related----------------------------------------

	/**
	 * this method creates a joinChannel for a guild if there isn't already one
	 * and add it to the list of joinChannels
	 * @param guild the guild where the joinChannel should be created
	 */
	public void createJoinChannel(final Guild guild){
		//check if the guild already has a joinChannel, if so then just stop the program
		GuildMessageChannel existingChannel = joinChannels.stream()
				.filter(channel -> BotUtility.sameGuildId(channel.getGuild().block(), guild))
				.findFirst().orElse(null);
		if(existingChannel != null){
			//if there is a joinChannel also check if there is a joinMessage
			//if not create one
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
		allowed = PermissionSet.of(	Permission.VIEW_CHANNEL, Permission.ADD_REACTIONS,
									Permission.SEND_MESSAGES, Permission.READ_MESSAGE_HISTORY,
									Permission.MANAGE_MESSAGES);
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
	 * this method finds the method out of a (assumed) joinChannel
	 * the criteria of a joinMessage are:
	 * the message was sent from this bot
	 * the message has all needed emojis as reactions on it
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
		//check if there are no emojiRoles,
		//if so maybe they didn't load so link them
		if(emojiGuildRoles == null) {
			linkEmojisToRoles();
			emojiGuildRoles = emojiRoles.get(guild);
			//now if they are stil no emojiRoles, then the server has none
			if(emojiGuildRoles == null)
				emojiGuildRoles = new HashMap<>();
		}
		//getting all the emojis of a guild, which are used for roles
		//these are needed to tell if the channel has a joinMessage
		//to match the criteria
		List<String> guildEmojis = Utility.getKeys(emojiGuildRoles);
		Message joinMessage = messages.stream()
				.filter(message -> BotUtility.sameUser(message.getAuthor().get(), SorakaBot.getSelf())) //first criteria -> the message from the bot
				.filter(message -> { //second criteria -> the message has the right emojis on it
					//getting all the raw strings of the emojis on the message
					List<String> messageEmojis = message.getReactions()
							.stream()
							.filter(reaction -> reaction.getEmoji().asUnicodeEmoji().isPresent()) //get all unicode reactions only
							.map(reaction -> reaction.getEmoji().asUnicodeEmoji().get().getRaw()) //only the string representations of them
							.collect(toList());
					return messageEmojis.containsAll(guildEmojis);									//now if this list contains all the needed emojis its a joinMsg
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
		setToRoleEmojis(joinChannel.createMessage(content).block());
	}

	/**
	 * this method updates the joinMessage of the joinChannel, by editing in an updated version of the context
	 * e.g.: when the amount of roles changed
	 * @param guild the guild where the joinChannel is
	 */
	public void updateJoinMessage(Guild guild) {
		//first finding the joinMessage of the guild
		//and check if it was found
		Message joinMessage = findJoinMessage(guild);
		//well if it was found edit the content of the message to an updated version of itself
		if(joinMessage != null) {
			//updating the message to the correct one
			joinMessage.edit(message -> message.setContent(makeJoinMessageContent(guild))).block();
		}
		setToRoleEmojis(joinMessage);
	}

	/**
	 * this method makes the message which gets displayed on the joinChannel of the guild
	 * @param guild the guild where the joinChannel should be
	 */
	private String makeJoinMessageContent(Guild guild){
		Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);

		String botNameInGuild = BotUtility.getNameInGuild(guild.getMemberById(SorakaBot.getSelf().getId()).block());
		String content = 	"Welcome to **" + guild.getName() + "** :wave_tone3:,\n" +
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

	//--------------------------------------end: joinMessage-related--------------------------------------

	//----------------------------------------reaction-related----------------------------------------

	/**
	 * it adds the emojis used to assign roles to a message
	 * @param msg this is the message
	 */
	private void setToRoleEmojis(Message msg){
		Optional<Guild> optGuild = msg.getGuild().blockOptional();
		if(optGuild.isPresent()){
			Guild guild = optGuild.get();
			//getting the emojis
			Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);
			//making sure everything is correctly linked
			if(guildEmojiRoles == null) {
				linkEmojisToRoles();
				guildEmojiRoles = emojiRoles.get(guild);
				if(guildEmojiRoles == null)
					return;

			}
			//now adding the reactions to the message
			//and i will also removing anything else after that
			//so during this foreach i will fill a list only the keys
			final List<String> roleEmojis = new LinkedList<>();
			guildEmojiRoles.forEach((emoji, role) -> {
				roleEmojis.add(emoji);
				msg.addReaction(ReactionEmoji.unicode(emoji)).block();
			});

			//also removing every emoji which is not a roleEmoji
			//so first get all unicode reactions
			//and then get all reactions which are not in the list
			//and for all of them remove the reaction
			msg.getReactions().stream()
					.map(reaction -> reaction.getEmoji())
					.filter(reaction -> reaction.asUnicodeEmoji().isPresent())
					.filter(emoji -> !roleEmojis.contains(emoji.asUnicodeEmoji().get().getRaw()))
					.forEach(emoji -> msg.removeReactions(emoji).block());
		}
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
			logger.log("The emoji: " + rawEmoji + "was removed which has no role assigned to it by: " + BotUtility.getNameInGuild(member)
					, guild);
			return;
		}

		//now assign the role to the user
		member.removeRole(role.getId()).block();

		MemManager.saveEmojiReactors(getCurrentEmojiReactors());
		logger.log("Removed role: **" + role.getName() + "** from: **" + BotUtility.getNameInGuild(member) + "**", guild);
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
			logger.log("The emoji: " + rawEmoji + "was added which has no role assigned to it by: " + BotUtility.getNameInGuild(member)
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
		logger.log("Assigned role: **" + role.getName() + "** to: **" + BotUtility.getNameInGuild(member) + "**", guild);
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
		emojiRoles.putAll(MemManager.loadEmojiRoles(client));
	}

	/**
	 * this method links an Emoji to a certain role of a guild
	 * @param rawEmoji the raw string of the emoji
	 * @param role the role in the guild, where the link will be created
	 */
	 private void linkEmojiToRole(String rawEmoji, Role role){
		Guild guild = role.getGuild().block();
		Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);
		if(guildEmojiRoles == null)
			guildEmojiRoles = new HashMap<>();
		guildEmojiRoles.put(rawEmoji, role);
		emojiRoles.put(guild, guildEmojiRoles);
	}

	/**
	 * removes the link from a emoji to a role
	 * @param role the entry to be deleted
	 */
	private boolean removeLinkEmojiToRole(Role role){
		Guild guild = role.getGuild().block();
		Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);
		if(guildEmojiRoles == null)
			guildEmojiRoles = new HashMap<>();

		//now going through the map, and checking if any entry has the role as its value
		String linkedEmoji = hasEmojiRoleLink(role);
		return guildEmojiRoles.remove(linkedEmoji) != null;

	}

	/**
	 * checks if a role has an entry in the emojiRoles
	 * @param role the role to check
	 * @return the emoji or an empty String ("")
	 */
	private String hasEmojiRoleLink(Role role){
		Guild guild = role.getGuild().block();
		Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);
		if(guildEmojiRoles == null)
			guildEmojiRoles = new HashMap<>();

		//now going through the map, and checking if any entry has the role as its value
		String linkedEmoji = null;
		for (Map.Entry<String, Role> entry : guildEmojiRoles.entrySet()) {
			if(entry.getValue().getId().equals(role.getId())) {
				linkedEmoji = entry.getKey();
				break;
			}
		}
		return linkedEmoji;
	}

	//--------------------------------------end: reaction-related----------------------------------------

	//--------------------------------------role-assign-commands----------------------------------------

	/**
	 * this method fills in all the commands for the role assigning
	 */
	private void initCommands(){
		commands = new HashMap<>();
		//adding the different commands
		addCommandAddRole();
		addCommandRemoveRole();
		addCommandChangeEmojiRole();
	}

	/**
	 * this helper method adds the command addRole to the list of commands
	 */
	private void addCommandAddRole(){
		//adding a new role to the assignable roles
		//the requirements are:
		//syntax: !addRole [-n] $roleName $emoji
		//the -n tag is for adding a role, which doesn't exist so the bot creates it
		//the message has to be sent in a guild
		//the member has to have the Manage_Guild permission
		CommandRequirement syntax = CommandRequirement.correctSyntaxSegmentAmount(3, 4);
		CommandRequirement inGuild = CommandRequirement.IN_GUILD;
		CommandRequirement controlOverBot = CommandRequirement.hasPermission(Permission.MANAGE_GUILD);
		List<CommandRequirement> requirements = List.of(syntax, inGuild, controlOverBot);

		Description description = new Description("you can add a new role to the self-assignable roles. Syntax: **!addRole [-n] $role $emoji**");

		Executable executable = message -> {
			//definitely has a guild
			Guild guild = message.getGuild().block();
			MessageChannel channel = message.getChannel().block();
			//splitting the content up into the arguments
			//it is assumed it has the right arguments, because its a requirement
			String content = message.getContent();
			String[] segments = content.split(" ");
			//now you have to check which argument is which, and this differs depending if it has 3 or 4 segments

			boolean hasTag = segments.length == 4;
			//so the name and the emoji are moved one to the right if there is a tag
			//so i made an index which "points" at the name of the role, and this would be normally (3 segments) at index 1
			//but if there is a tag (4 segments) then the index is 2
			int roleNameIndex = 1;
			//so add one if the segments are 4
			//also store the tag
			String tag = "";
			if(hasTag)
				tag = segments[roleNameIndex++];

			String roleName = segments[roleNameIndex++];
			String rawEmoji = segments[roleNameIndex];

			Role role;
			//so if there was a tag first interpret it
			//(currently its only 1, to create a new role)
			if(hasTag) {
				//if it is said task then the bot should create a new role with this name
				//but i don't want 2 roles with the same name, so check if there is already a role
				//with this name
				if(tag.equals("-" + "n")) {
					//search for a role, and if it was found, tell the member, that you used the existing role
					//if not go on with making a new role
					role = BotUtility.getRoleByName(roleName, guild);
					if(role != null) {
						channel.createMessage("There is already a Role with this name, so i used that").block();
					}
					//if there was no such role
					else {
						//creating the role
						role = guild.createRole(roleSpec -> roleSpec.setName(roleName)).block();
						logger.log("Created role for self-assigning", guild);
					}
				}
				//if the tag was something different then stop the command
				else {
					channel.createMessage("Wrong tag, only allowed: \"-n\"").block();
					return;
				}
			}
			//if there was no tag then find the role mentioned
			//if it is not found then stop the command
			else {
				role = BotUtility.getRoleByName(roleName, guild);
				if(role == null) {
					channel.createMessage("No role found with that name, use the tag -n to create a new Role").block();
					return;
				}
			}
			//now the role has been successfully found/created
			//you also have to check if the emoji is a valid unicode emoji
			//there isn't really a method for it, so i just add the emoji as a reaction, and check if there is an error
			ReactionEmoji.Unicode emoji = ReactionEmoji.unicode(rawEmoji);
			try{
				//getting the joinMessage of the guild
				Message joinMessage = findJoinMessage(guild);
				if(joinMessage == null)
					return;
				//now adding the role with the associated emoji
				joinMessage.addReaction(emoji).block();
			}
			//if its not an emoji
			catch (ClientException e){
				channel.createMessage("Invalid Emoji! Either it's a custom emoji or an invalid one. Only standard unicode emoji!").block();
				return;
			}
			//now you have to check if there is already a link for the emoji, if so deny the command
			Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);
			if(guildEmojiRoles != null && guildEmojiRoles.get(rawEmoji) != null){
				channel.createMessage("The Emoji has already a role associated with it!").block();
				return;
			}

			//now the emoji has been added, so create a link to the role
			linkEmojiToRole(emoji.getRaw(), role);
			//then update the joinMessage, to include the description of the role
			updateJoinMessage(guild);
			channel.createMessage("Successfully added role **" + roleName + "** to the assignable roles with the emoji " + rawEmoji).block();
			logger.log("Added role **" + roleName + "** to the assignable roles with the emoji " + rawEmoji);
			//last save the emojiRoles
			MemManager.saveEmojiRoles(emojiRoles);

		};

		commands.put("addEmoji", new Command(requirements, executable, description));
	}

	/**
	 * this helper method adds the command removeRole to the list of commands
	 */
	private void addCommandRemoveRole(){
		/*
		removing a role to the assignable roles
		the requirements are:
		syntax: !removeRole $roleName
		the message has to be sent in a guild
		the member has to have the Manage_Guild permission
		*/
		CommandRequirement syntax = CommandRequirement.correctSyntaxSegmentAmount(2);
		CommandRequirement inGuild = CommandRequirement.IN_GUILD;
		CommandRequirement controlOverBot = CommandRequirement.hasPermission(Permission.MANAGE_GUILD);
		List<CommandRequirement> requirements = List.of(syntax, inGuild, controlOverBot);

		Description description = new Description("you can remove a role from the self-assignable roles (doesn't delete the role). " +
													"Syntax: **!removeRole $role**");

		Executable executable = message -> {
			//definitely has a guild
			Guild guild = message.getGuild().block();
			MessageChannel channel = message.getChannel().block();
			//splitting the content up into the arguments
			//it is assumed it has the right arguments, because its a requirement
			String content = message.getContent();
			String[] segments = content.split(" ");

			String roleName = segments[1];

			Role role = BotUtility.getRoleByName(roleName, guild);

			if(role == null) {
				channel.createMessage("The role doesn't exist!").block();
				return;
			}

			boolean successfulRemove = removeLinkEmojiToRole(role);
			if(!successfulRemove) {
				channel.createMessage("the role wasn't self-assignable").block();
				return;
			}
			//feedback for the user
			channel.createMessage("the role " + roleName + " was successfully removed from the self-assigning").block();
			logger.log("The role " + roleName + " was successfully removed from the self-assigning", message);

			//then update the joinMessage, to remove the description of the role
			updateJoinMessage(guild);
			//last save the emojiRoles
			MemManager.saveEmojiRoles(emojiRoles);

		};

		commands.put("removeRole", new Command(requirements, executable, description));
	}

	/**
	 * this helper method adds the command changeEmoji to the list of commands
	 */
	private void addCommandChangeEmojiRole(){
		/*
		editing the emoji of a role to the assignable roles
		the requirements are:
		syntax: !editRole $roleName $emoji
		the message has to be sent in a guild
		the member has to have the Manage_Guild permission
		*/
		CommandRequirement syntax = CommandRequirement.correctSyntaxSegmentAmount(3);
		CommandRequirement inGuild = CommandRequirement.IN_GUILD;
		CommandRequirement controlOverBot = CommandRequirement.hasPermission(Permission.MANAGE_GUILD);
		List<CommandRequirement> requirements = List.of(syntax, inGuild, controlOverBot);

		Description description = new Description("You can change the emoji of a Role. Syntax: **!changeEmoji $role $emoji**");

		Executable executable = message -> {
			//definitely has a guild
			Guild guild = message.getGuild().block();
			MessageChannel channel = message.getChannel().block();
			//splitting the content up into the arguments
			//it is assumed it has the right arguments, because its a requirement
			String content = message.getContent();
			String[] segments = content.split(" ");

			String roleName = segments[1];
			String rawNewEmoji = segments[2];

			Role role = BotUtility.getRoleByName(roleName, guild);

			if (role == null) {
				channel.createMessage("The role doesn't exist!").block();
				return;
			}
			//now check if there is an entry for the role in the emojiRoles
			String rawOldEmoji = hasEmojiRoleLink(role);
			if(rawOldEmoji == null){
				channel.createMessage("The role is not self-assignable").block();
				return;
			}

			//now the role has been successfully found
			//you also have to check if the emoji is a valid unicode emoji
			//there isn't really a method for it, so i just add the emoji as a reaction, and check if there is an error
			ReactionEmoji.Unicode newEmoji = ReactionEmoji.unicode(rawNewEmoji);
			try{
				//getting the joinMessage of the guild
				Message joinMessage = findJoinMessage(guild);
				if(joinMessage == null)
					return;
				//now adding the reaction
				joinMessage.addReaction(newEmoji).block();
				//there is no need to remove the old one, this gets done in the updateJoinMessage() method
			}
			//if its not an emoji
			catch (ClientException e){
				channel.createMessage("Invalid Emoji! Either it's a custom emoji or an invalid one. Only standard unicode emoji!").block();
				return;
			}

			//now you have to check if there is already a link for the emoji, if so deny the command
			Map<String, Role> guildEmojiRoles = emojiRoles.get(guild);
			if(guildEmojiRoles.get(rawNewEmoji) != null){
				channel.createMessage("The Emoji has already a role associated with it!").block();
				return;
			}

			guildEmojiRoles.put(newEmoji.getRaw(), role);
			guildEmojiRoles.remove(rawOldEmoji);
			//then update the joinMessage, to remove the description of the role
			updateJoinMessage(guild);
			//give feedback to the user
			channel.createMessage("Changed the emoji for the role **" + roleName + "** from " + rawOldEmoji + " to " + rawNewEmoji).block();
			logger.log("Changed the emoji for the role **" + roleName + "** from " + rawOldEmoji + " to " + rawNewEmoji, message);
			//last save the emojiRoles
			MemManager.saveEmojiRoles(emojiRoles);

		};

		commands.put("changeEmoji", new Command(requirements, executable, description));
	}

	//--------------------------------------end: role-assign-commands----------------------------------------

	//----------------------------------------getter&setter----------------------------------------

	public List<GuildMessageChannel> getJoinChannels(){
		return joinChannels;
	}

	public Map<String, Command> getCommands(){
		return commands;
	}
}
