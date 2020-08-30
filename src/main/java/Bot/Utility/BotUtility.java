package Bot.Utility;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.PermissionSet;

public class BotUtility {
	/**
	 * this method checks if a member and a user have the same name and discriminator
	 * @param user		the user
	 * @param member	the member to compare to
	 * @return			returns if they have the same user
	 */
	public static boolean sameUser(User user, Member member){
		return member.getUsername().equals(user.getUsername()) && member.getDiscriminator().equals(user.getDiscriminator());
	}

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
	 * @param role this role will have no permissions afterwars
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
	 * @return
	 */
	public static GuildChannel getGuildChannelByName(String name, Guild guild){
		return guild.getChannels().toStream().filter(channel -> channel.getName().equals(name)).findFirst().get();
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

	public static void spongebobMeme(MessageCreateEvent createEvent){
		//getting the content of the message
		String content = createEvent.getMessage().getContent();
		content = Utility.toAltCase(content);
		createEvent.getMessage().getChannel().block().createMessage(content).block();
	}
}
