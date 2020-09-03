package discord.utility;

import discord.bot.SorakaBot;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.PermissionSet;

import java.util.*;

public class BotUtility {

	/**
	 * this method checks if a user and a user have the same name and discriminator
	 * @param user1		the user
	 * @param user2 	the other user
	 * @return			returns if they have the same user information
	 */
	public static boolean sameUser(User user1, User user2){
		return user2.getUsername().equals(user1.getUsername()) && user2.getDiscriminator().equals(user1.getDiscriminator());
	}

	/**
	 * this method removes all permissions from all GuildChannels for a role
	 * @param role this role will have no permissions afterwards
	 */
	public static void denyAllChannels(Role role){
		//first get the guild
		Guild guild = role.getGuild().block();
		//then make a PermissionOverwrite with none allowed and all denied for the role
		PermissionOverwrite noPermissions = PermissionOverwrite.forRole(role.getId(), PermissionSet.none(), PermissionSet.all());
		//and now add these overwrites for every Channel on the guild
		guild.getChannels().toStream().forEach(channel -> channel.addRoleOverwrite(role.getId(), noPermissions).block());
	}

	/**
	 * this method finds the first Channel of a guild by its name
	 * @param name the name of the channel
	 * @param guild the guild
	 * @return the found channel, or null
	 */
	public static GuildChannel getGuildChannelByName(String name, Guild guild){
		return guild.getChannels().toStream().filter(channel -> channel.getName().equals(name)).findFirst().orElse(null);
	}

	/**
	 * this method adds a role to a member by the name of the role, if there are duplicates then the first role found gets added
	 * @param roleName			the name of the role
	 * @param feedbackChannel	the channel where the bot sends the feedback to the member (could be private but also public)
	 * @param member			the member which receives the role
	 * @return
	 */
	public static boolean addRoleByName(String roleName, MessageChannel feedbackChannel, Member member){
		//first getting the desired role
		Role gamerRole = getRoleByName(roleName, member.getGuild().block());
		//if its not found send negative feedback
		if(gamerRole == null){
			feedbackChannel.createMessage("Sorry der Server hat keine \"**" + roleName + "**\"-Rolle schreib dem Admin für Hilfe!").block();
			return false;
		}
		//if its found add the role and tell it to the member/feedbackChannel
		member.addRole(gamerRole.getId()).block();
		feedbackChannel.createMessage("Die Rolle **" + roleName + "** wurden nun freigeschalten! Die entsprechenden Channels sind jetzt sichtbar!").block();
		return true;
	}

	/**
	 * this method looks for a role, depending on the name
	 * @param roleName	the name of the role which should be searched for
	 * @param guild		the guild in which the role should be
	 * @return			the first role with the same name as an Role Object, or null if not found
	 */
	public static Role getRoleByName(String roleName, Guild guild){

		return guild.getRoles()													//get all roles of the guild
				.filter(role -> role.getName().equals(roleName))//then just look at the roles which have the right name
				.blockFirst();									//bc it could be more than one just get the first one

	}

	/**
	 * this method sends back a message with alternating case
	 * @param message Message which gets sent back
	 */
	public static void spongebobMeme(Message message){
		//getting the content of the message
		String content = message.getContent();
		content = Utility.toAltCase(content);
		message.getChannel().block().createMessage(content).block();
	}

	/**
	 * compares the guildIds of 2 guilds
	 * @param g1 -
	 * @param g2 -
	 * @return returns if they are the same
	 */
	public static boolean sameGuildId(Guild g1, Guild g2){
		return Objects.equals(g1.getId(), g2.getId());
	}



	/**
	 * this method gets all the messages of a channel
	 * @param channel the channel where the messages should get returned
	 * @return returns all messages in a channel, or an empty list if there are none
	 */
	public static List<Message> getMessagesOfChannel(MessageChannel channel){
		//first get the last message of the channel
		try {
			Message lastMessage = channel.getLastMessage().block();
			//get all messages before the last message and then add the last message to it
			List<Message> messages = channel.getMessagesBefore(lastMessage.getId()).collectList().block();
			messages.add(lastMessage);

			return messages;
		}catch(ClientException | NullPointerException e){
			//if there was no last message then the channel is empty so return an empty list
			return new ArrayList<>();
		}


	}

	/**
	 * checks if a message was sent in a guild or via DM
	 * @param message the message to be checked
	 * @return if this wasn't a dm
	 */
	public static boolean inGuild(Message message){
		MessageChannel channel = message.getChannel().block();
		return !channel.getType().equals(Channel.Type.DM);
	}

	/**
	 * this method gets the string how the member is called in his guild (either his nickname or username
	 * @param member the member to get the name from
	 * @return returns his name
	 */
	public static String getNameInGuild(Member member){
		return member.getNickname().orElse(member.getUsername());
	}

	/**
	 * checks if the bot changed the name
	 * @param current the current state of the bot
	 * @param old the old state of the bot
	 * @return returns if a namechange between those two members occured
	 */
	public static boolean botNameChanged(Member current, Member old){
		//check if the member updated was the bot
		if(BotUtility.sameUser(SorakaBot.getSelf(), current)) {
			Guild guild = current.getGuild().block();
			//now check if the update was a rename
			String currentName = BotUtility.getNameInGuild(current);
			String oldName = BotUtility.getNameInGuild(old);
			if(!currentName.equals(oldName)){
				SorakaBot.getLogger().log("Renamed the bot from **" + oldName + "** to **" + currentName + "**", guild);
				return true;
			};
		}
		return false;
	}
}
