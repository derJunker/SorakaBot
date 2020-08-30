package Bot.Soraka;

import Bot.Utility.BotUtility;
import Bot.Utility.MemManager;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;

import java.util.*;
import java.util.stream.Collectors;

public class SorakaBot {

	//the bot stores itself as a user
	private static User self;

	//loaded static variables
	private static List<GuildMessageChannel> joinChannels = new ArrayList<>();
	private static Map<String, Role> emojiRoles = new HashMap<>();

	//emojis
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

		fillEmojisToRoles();

		//loading the data
		Map<String, String> snowflakeMap = MemManager.loadJoinChannels();
		if(snowflakeMap == null){
			snowflakeMap = new HashMap<>();
		}
		joinChannels = BotUtility.idMapToGuildChannels(snowflakeMap, client).stream()
																				.map(guildChannel -> (GuildMessageChannel) guildChannel)
																				.collect(Collectors.toList());

		onReady();
		onReactionAddEvent();
		onGuildEvent();

		client.onDisconnect().block();
	}

	/**
	 * this method triggers when a reaction get added
	 */
	private static void onReactionAddEvent() {
		client.getEventDispatcher().on(ReactionAddEvent.class)
				.subscribe(event -> {
					//check if it is a joinChannel
					Channel joinChannel = event.getChannel().block();
					//if the reaction was added somewhere not in a joinChannel, then its not important
					if(!joinChannels.contains(joinChannel))
						return;

					//get the emoji as a string
					String emoji = event.getEmoji().asUnicodeEmoji().get().getRaw();
					//and now get the role connected to that emoji, also check if there was an entry
					Role role = emojiRoles.get(emoji);
					//check if there is no role assigned to the emoji
					if(role == null){
						//TODO - log not confirmed role
						return;
					}
					//now assign the role to the user
					Member member = event.getMember().get();
					member.addRole(role.getId()).block();

					//TODO - log new role
					System.out.println("assigned role: " + role.getName());
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
					//the bot just saves its user data
					self = event.getSelf();
				});
	}

	/**
	 * this method creates a joinChannel for a guild if there isnt already one
	 * and saves it
	 * @param guild the guild where the joinChannel should be created
	 */
	private static void createJoinChannel(final Guild guild){
		//check if the guild already has a joinChannel, if so then just stop the program
		boolean hasJoinChannel = joinChannels.stream()
											.map(channel -> channel.getGuild().block())
											.anyMatch(joinedGuild -> BotUtility.sameGuildId(joinedGuild, guild));
		if(hasJoinChannel){
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
		allowed = /*PermissionSet.of(Permission.VIEW_CHANNEL, Permission.READ_MESSAGE_HISTORY);*/ PermissionSet.none();
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
			//TODO log it
			return;
		}
		//the permissions of the bot
		allowed = PermissionSet.of(Permission.VIEW_CHANNEL, Permission.ADD_REACTIONS, Permission.SEND_MESSAGES, Permission.READ_MESSAGE_HISTORY);
		denied = PermissionSet.none();
		//now saving the allowed and denied messages
		PermissionOverwrite botPermissions = PermissionOverwrite.forRole(role.getId(), allowed, denied);
		permissions.add(botPermissions);

		//finally making a new textChannel, with the name "join" and the right permissions
		TextChannel joinChannel = guild.createTextChannel(textChannel -> {
																		textChannel
																				.setName("join")
																				.setPermissionOverwrites(permissions);
																	}).block();

		//also
		final String content = "Choose your role: ";
		Message joinMessage = joinChannel.createMessage(content).block();
		addRoleEmojis(joinMessage);
		//finally adding it to the joinChannel list
		joinChannels.add(joinChannel);
		//and saving the joinChannels afterwards
		MemManager.saveJoinChannels(joinChannels);
		//TODO - log the adding of a joinChannel

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
	 * this method fills all the emojis to the right roles
	 */
	private static void fillEmojisToRoles(){
		//for every guild add the roles
		client.getGuilds().toStream()
				.forEach(guild -> {
					//do it both for the gamer role as well as the student role
					//first get the role with the right name
					Role gamer = BotUtility.getRoleByName(GAMER_NAME, guild);
					if(gamer != null){
						//then add the right emoji to the role into the map
						emojiRoles.put(GAMER_EMOJI, gamer);
					}

					//first get the role with the right name
					Role student = BotUtility.getRoleByName(STUDENT_NAME, guild);
					if(student != null){
						//then add the right emoji to the role into the map
						emojiRoles.put(STUDENT_EMOJI, student);
					}
				});
	}
 }