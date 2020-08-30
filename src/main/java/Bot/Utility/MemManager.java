package Bot.Utility;

import discord4j.core.object.entity.channel.GuildMessageChannel;

import java.io.*;
import java.util.List;

public class MemManager {

	private static final String RES_FOLDER = "resources/";

	//filenames
	private static final String JOIN_CHANNEL_NAMES = "join.channels";

	/**
	 * this method loads all the joinChannels of every guild the bot is in
	 * @return returns this list
	 */
	public static List<GuildMessageChannel> loadJoinChannels(){
		try{
			String filePath = RES_FOLDER + JOIN_CHANNEL_NAMES;
			FileInputStream fis = new FileInputStream(filePath);
			ObjectInputStream ois = new ObjectInputStream(fis);
			@SuppressWarnings("unchecked")
			List<GuildMessageChannel> joinChannels = (List<GuildMessageChannel>) ois.readObject();
			ois.close();
			return joinChannels;
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
			String fileName = RES_FOLDER + JOIN_CHANNEL_NAMES;
			FileOutputStream fos = new FileOutputStream(fileName);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(joinChannels);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
