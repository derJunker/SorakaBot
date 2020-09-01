package Bot.Utility;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.GuildMessageChannel;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class MemManager {

	private static final String RES_FOLDER = "src/main/resources/";

	//filenames
	private static final String JOIN_CHANNEL_NAMES = "join.channels";
	private static final String EMOJI_ROLE_NAMES = "emoji.reactors";
	private static final String EMOJI_REACTOR_NAMES = "emoji.reactors";

	//--------------------------------------------------load-methods--------------------------------------------------

	/**
	 * this method loads all the joinChannels of every guild the bot is in
	 * @return returns this list
	 */
	public static List<GuildMessageChannel> loadJoinChannels(GatewayDiscordClient client){
		try{
			String filePath = RES_FOLDER + JOIN_CHANNEL_NAMES;
			FileInputStream fis = new FileInputStream(filePath);
			ObjectInputStream ois = new ObjectInputStream(fis);
			//reading the map of guild-id and channel-id
			@SuppressWarnings("unchecked")
			Map<String, String> guildChannelMap = (Map<String, String>) ois.readObject();
			if(guildChannelMap == null){
				guildChannelMap = new HashMap<>();
			}
			ois.close();
			//now deserialize it, so make it the list of joinChannels
			List<GuildMessageChannel> joinChannels = deserializeJoinChannels(guildChannelMap, client);
			return joinChannels;
		}
		catch(IOException | ClassNotFoundException e){
			return null;
		}
	}

	/**
	 * loads the saved emojiRoles
	 * @param client the client of the bot
	 * @return the emojiRoles as a map
	 */
	public static Map<Guild, Map<String, Role>> loadEmojiRoles(GatewayDiscordClient client){
		try{
			String filePath = RES_FOLDER + EMOJI_ROLE_NAMES;
			FileInputStream fis = new FileInputStream(filePath);
			ObjectInputStream ois = new ObjectInputStream(fis);
			//reading the map of guild-id and channel-id
			@SuppressWarnings("unchecked")
			Map<String, Map<String, String>> emojiRoleIds = (Map<String, Map<String, String>>) ois.readObject();
			if(emojiRoleIds == null){
				emojiRoleIds = new HashMap<>();
			}
			ois.close();
			//now deserialize it, so make it the list of joinChannels
			Map<Guild, Map<String, Role>> emojiRoles = deserializeEmojiRoles(emojiRoleIds, client);
			return emojiRoles;
		}
		catch(IOException | ClassNotFoundException e){
			return null;
		}
	}

	/**
	 * loads the saved emojiRoles
	 * @param client the client of the bot
	 * @return the emojiRoles as a map
	 */
	public static Map<Guild, Map<String, List<Member>>> loadEmojiReactors(GatewayDiscordClient client){
		try{
			String filePath = RES_FOLDER + EMOJI_REACTOR_NAMES;
			FileInputStream fis = new FileInputStream(filePath);
			ObjectInputStream ois = new ObjectInputStream(fis);
			//reading the map of guild-id and channel-id
			@SuppressWarnings("unchecked")
			Map<String, Map<String, List<String>>> emojiReactorIds = (Map<String, Map<String, List<String>>>) ois.readObject();
			if(emojiReactorIds == null){
				emojiReactorIds = new HashMap<>();
			}
			ois.close();
			//now deserialize it, so make it the list of joinChannels
			return deserializeEmojiReactors(emojiReactorIds, client);
		}
		catch(IOException | ClassNotFoundException e){
			return null;
		}
	}

	//------------------------------------------------end: load-methods------------------------------------------------

	//--------------------------------------------------save-methods--------------------------------------------------

	/**
	 * this method saves a list of joinChannels
	 * @param joinChannels the GuildMessageChannels which are the joinChannels
	 */
	public static void saveJoinChannels(List<GuildMessageChannel> joinChannels, GatewayDiscordClient client){
		try {
			//first convert the list into a map with the guild-id as key and the channel id as value,
			//because the guild, and channel classes are not serializable
			Map<String, String> convertedJoinChannels = serializeJoinChannels(joinChannels, client);

			String fileName = RES_FOLDER + JOIN_CHANNEL_NAMES;
			FileOutputStream fos = new FileOutputStream(fileName);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(convertedJoinChannels);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * this method aves the roles associated with emojis, for every guild
	 * by extracting the ids of the guilds, and roles
	 * @param emojiRoles the object to be saved
	 */
	public static void saveEmojiRoles(Map<Guild, Map<String, Role>> emojiRoles){
		try {
			//fist convert the map
			Map<String, Map<String, String>> convertedEmojiRoles = serializeEmojiRoles(emojiRoles);
			String fileName = RES_FOLDER + EMOJI_ROLE_NAMES;
			FileOutputStream fos = new FileOutputStream(fileName);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(convertedEmojiRoles);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * this method aves the roles associated with emojis, for every guild
	 * by extracting the ids of the guilds, and roles
	 * @param emojiRoles the object to be saved
	 */
	public static void saveEmojiReactors(Map<Guild, Map<String, List<Member>>> emojiRoles){
		try {
			//fist convert the map
			Map<String, Map<String, List<String>>> convertedEmojiReactors = serializeEmojiReactors(emojiRoles);
			String fileName = RES_FOLDER + EMOJI_REACTOR_NAMES;
			FileOutputStream fos = new FileOutputStream(fileName);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(convertedEmojiReactors);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//--------------------------------------------------end: save-methods--------------------------------------------------

	//--------------------------------------------------serialize-methods--------------------------------------------------

	/**
	 * this method makes the map of emoji roles into a mak of string representation of ids, so you can save the map
	 * @param emojiRoles the map to be converted
	 * @return returns the serializable map
	 */
	private static Map<String, Map<String, String>> serializeEmojiRoles(Map<Guild, Map<String, Role>> emojiRoles){
		//first convert the map into a map with the guild, and roles replaced with its ids
		//then save this map
		Map<String, Map<String, String>> convertedEmojiRoles = new HashMap<>();
		emojiRoles.forEach((guild, guildEmojiRoles) -> {
			//first convert the guildEmojiRoles to the Map<String, String)
			//basically mapping the guildEmojiRoles, to itself, but the role is represented by id
			//and this done during the collect method
			Map<String, String> convertedGuildEmojiRoles =  guildEmojiRoles.entrySet().stream()
					.collect(Collectors.toMap(entry -> entry.getKey()
							, entry -> entry.getValue().getId().asString()));
			convertedEmojiRoles.put(guild.getId().asString(), convertedGuildEmojiRoles);
		});
		return convertedEmojiRoles;
	}

	/**
	 * this method makes the map of emoji roles into a mak of string representation of ids, so you can save the map
	 * @param emojiReactors the map to be converted
	 * @return returns the serializable map
	 */
	private static Map<String, Map<String, List<String>>> serializeEmojiReactors(Map<Guild, Map<String, List<Member>>> emojiReactors){
		//first convert the map into a map with the guild, and roles replaced with its ids
		//then save this map

		//you have to read this code from the inner loop to the outer ones
		//so you first you have the guild, and the emoji, and you fill a list of embers who reacted to this emoji
		//then you go out one loop, now you add for every guild, every emoji with its list of reactors
		//and you do this for every guild
		Map<String, Map<String, List<String>>> convertedEmojiRoles = new HashMap<>();
		emojiReactors.forEach((guild, guildEmojiReactors) -> {

			//for every guild add all the emojis with its reactors
			final Map<String, List<String>> guildEmojiReactorIds = new HashMap<>();
			guildEmojiReactors.forEach((rawEmoji, reactorsByEmoji) -> {

				//for every guild and emoji get a list of all the reactors
				final List<String> reactorIds = new LinkedList<>();
				reactorsByEmoji.forEach(member -> reactorIds.add(member.getId().asString()));
				guildEmojiReactorIds.put(rawEmoji, reactorIds);
			});
			convertedEmojiRoles.put(guild.getId().asString(), guildEmojiReactorIds);

		});
		return convertedEmojiRoles;
	}

	/**
	 * this method makes a List of joinChannels, from the loaded file, which was saved as a map of 2 ids, (guild id, channel id)
	 * @param joinChannels List to be converted
	 * @return returns the serializable map
	 */
	private static Map<String, String> serializeJoinChannels(List<GuildMessageChannel> joinChannels, GatewayDiscordClient client){
		final Map<String, String> convertedJoinChannels = new HashMap<>();
		//putting every channel into the map with its guild
		joinChannels.forEach(channel -> convertedJoinChannels.put(channel.getGuildId().asString(), channel.getId().asString()));
		return convertedJoinChannels;
	}

	//-------------------------------------------------end: serialize-methods-------------------------------------------------

	//--------------------------------------------------deserialize-methods--------------------------------------------------

	/**
	 * this method converts a map of ids linked to another map of emojiStrings, with role ids as string
	 * @param emojiRoleIds (guild id, (emoji, roleId)) all as strings
	 * @param client the client, to get the guilds by id
	 * @return returns the list
	 */
	private static Map<Guild, Map<String, Role>> deserializeEmojiRoles(Map<String, Map<String, String>> emojiRoleIds, GatewayDiscordClient client){
		//first convert the map into a map with the guild, and roles replaced with its ids
		//then save this map
		Map<Guild, Map<String, Role>> emojiRoles = new HashMap<>();
		emojiRoleIds.forEach((guildId, guildEmojiRolesIds) -> {
			//get the find the guild by the id
			Optional<Guild> optionalGuild = client.getGuildById(Snowflake.of(guildId)).blockOptional();
			if(optionalGuild.isEmpty()){
				return;
			}
			final Guild guild = optionalGuild.get();
			//now go through every emoji of this guild, and find the role for it
			//and add them to the map below
			final Map<String, Role> guildEmojiRoles = new HashMap<>();
			guildEmojiRolesIds.forEach((rawEmoji, roleID) ->{
				//finding the role, by first checking if it even exists
				Optional<Role> optionalRole = guild.getRoleById(Snowflake.of(roleID)).blockOptional();
				if(optionalRole.isEmpty()){
					return;
				}
				Role role = optionalRole.get();
				//now put this role into the converted map
				guildEmojiRoles.put(rawEmoji, role);
			});
			//now the guildEmojiRoles map is filled, so add it to the emojiRoles map
			emojiRoles.put(guild, guildEmojiRoles);
		});
		return emojiRoles;
	}

	/**
	 * this method converts a map of ids linked to another map of emojiStrings, with role ids as string
	 * @param emojiRoleIds (guild id, (emoji, roleId)) all as strings
	 * @param client the client, to get the guilds by id
	 * @return returns the list
	 */
	private static Map<Guild, Map<String, List<Member>>> deserializeEmojiReactors(Map<String, Map<String, List<String>>> emojiRoleIds, GatewayDiscordClient client){
		//first convert the map into a map with the guild, and roles replaced with its ids
		//then save this map
		Map<Guild, Map<String, List<Member>>> emojiReactors = new HashMap<>();
		emojiRoleIds.forEach((guildId, guildEmojiReactorIds) -> {
			//get the find the guild by the id
			Optional<Guild> optionalGuild = client.getGuildById(Snowflake.of(guildId)).blockOptional();
			if(optionalGuild.isEmpty()){
				return;
			}
			final Guild guild = optionalGuild.get();
			//now go through every emoji of this guild, and find the role for it
			//and add them to the map below
			final Map<String, List<Member>> guildEmojiReactors = new HashMap<>();
			guildEmojiReactorIds.forEach((rawEmoji, memberIds) ->{
				//getting the members and for each adding them
				final List<Member> reactors = new LinkedList<>();
				memberIds.forEach(memberId -> {
					Optional<Member> optionalMember = guild.getMemberById(Snowflake.of(memberId)).blockOptional();
					if(optionalMember.isEmpty()){
						return;
					}
					Member member = optionalMember.get();
					//now put this role into the converted map
					reactors.add(member);
				});
				guildEmojiReactors.put(rawEmoji, reactors);

			});
			//now the guildEmojiRoles map is filled, so add it to the emojiReactors map
			emojiReactors.put(guild, guildEmojiReactors);
		});
		return emojiReactors;
	}

	/**
	 * this method converts a map of ids into a list of supposed JoinChannels (type: GuildMessageChannel)
	 * @param joinChannelMap (guild id, channel id) both as strings
	 * @param client the client, to get the guilds by id
	 * @return returns the list
	 */
	private static List<GuildMessageChannel> deserializeJoinChannels(Map<String, String> joinChannelMap, GatewayDiscordClient client){
		//now converting it into a list of channels
		List<GuildMessageChannel> joinChannels = new ArrayList<>();
		joinChannelMap.forEach((guildId, channelId) -> {
			Optional<Guild> optionalGuild = client.getGuildById(Snowflake.of(guildId)).blockOptional();
			if(optionalGuild.isEmpty()){
				return;
			}
			Guild guild = optionalGuild.get();

			GuildMessageChannel joinChannel = (GuildMessageChannel) guild.getChannels().filter(channel -> channel.getId().equals(Snowflake.of(channelId))).blockFirst();

			if(joinChannel != null)
				joinChannels.add(joinChannel);
		});
		//finally convert the channels into GuildMessage
		return joinChannels;
	}

	//------------------------------------------------end: deserialize-methods------------------------------------------------
}
