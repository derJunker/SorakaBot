package Bot.Utility;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Entity;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.GuildMessageChannel;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MemManager {

	private static final String RES_FOLDER = "src/main/resources/";

	//filenames
	private static final String JOIN_CHANNEL_NAMES = "join.channels";
	private static final String GUILD_NAMES = "join.guilds";

	/**
	 * this method loads all the joinChannels of every guild the bot is in
	 * @return returns this list
	 */
	public static Map<String, String> loadJoinChannels(){
		try{
			String filePath = RES_FOLDER + JOIN_CHANNEL_NAMES;
			FileInputStream fis = new FileInputStream(filePath);
			ObjectInputStream ois = new ObjectInputStream(fis);
			//reading the map of guild-id and channel-id
			@SuppressWarnings("unchecked")
			Map<String, String> guildChannelMap = (Map<String, String>) ois.readObject();
			ois.close();
			return guildChannelMap;
		}
		catch(IOException | ClassNotFoundException e){
			return null;
		}
	}

	/**
	 * this method saves a list of joinChannels
	 * @param joinChannels the GuildMessageChannels which are the joinChannels
	 */
	public static void saveJoinChannels(List<GuildMessageChannel> joinChannels){
		try {
			//first convert the list into a map with the guild-id as key and the channel id as value,
			//because the guild, and channel classes are not serializable
			final Map<String, String> guildChannelMap = new HashMap<>();
			//putting every channel into the map with its guild
			joinChannels.forEach(channel -> guildChannelMap.put(channel.getGuildId().asString(), channel.getId().asString()));

			String fileName = RES_FOLDER + JOIN_CHANNEL_NAMES;
			FileOutputStream fos = new FileOutputStream(fileName);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(guildChannelMap);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
