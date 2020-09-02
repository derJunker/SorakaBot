package bot.soraka.features.commands.parts;

import bot.utility.BotUtility;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Permission;

import java.util.Arrays;
import java.util.List;

/**
 * this interface is a single method interface
 * which has a check method, this is used to check if a command is valid
 * a Command object stores a list of requirements which have to be true, that the command can get approved
 * for a normal requirement it is normal give out an errorMessage if its false
 */
public interface CommandRequirement {
	//some common requirements
	CommandRequirement IN_GUILD = message -> {
												boolean result = BotUtility.inGuild(message);
												//if the requirement wasn't met then make an errorMessage
												if(!result){
													CommandRequirement.errorMessage("This command can only be used in guilds!", message);
												}
												return result;
											};


	/**
	 * checks if the a requirement is met
	 * @param message the message with the needed information to check if everything is right
	 * @return returns if the requirement is met
	 */
	boolean check(Message message);

	/**
	 * makes an errorMessage for a not met requirement (that's how it's intended) this is a redundant method just so it looks better
	 * @param content the content of the error message
	 * @param message it contains the channel where the message was sent to reply
	 */
	static void errorMessage(String content, Message message){
		message.getChannel().subscribe(channel -> channel.createMessage(content).subscribe());
	}

	/**
	 * returns a CommandRequirement, which checks if the member has the right permissions
	 * it assumes you are on a guild
	 * @param permissions the array of permissions
	 * @return returns a method(1 method interface) which where it checks if said permissions are met
	 */
	static CommandRequirement hasPermissions(final Permission... permissions){
		return message -> {
			//because its required that this message was sent in a guild
			//i can assume there will be no error getting the author as a member
			Member author = message.getAuthorAsMember().block();
			//now check if every permission is in the list of basePermissions of the author
			boolean result = author.getBasePermissions().block().containsAll(Arrays.asList(permissions));
			if(!result){
				CommandRequirement.errorMessage("Sorry " + BotUtility.getNameInGuild(author) +
								", you have to have the permission: **\"" + permissions + "\"** to change the prefix of this bot"
						, message);
			}
			return result;
		};
	}

	/**
	 * returns a CommandRequirement, which checks if the member has the right permission
	 * it assumes you are on a guild
	 * @param permission the permission to be met
	 * @return returns a method(1 method interface) which where it checks if said permissions are met
	 */
	static CommandRequirement hasPermission(final Permission permission){
		return message -> {
							//because its required that this message was sent in a guild
							//i can assume there will be no error getting the author as a member
							Member author = message.getAuthorAsMember().block();
							//checking if the author of the message has the permission to manage the guild
							//this is the requirement to change the prefix
							boolean result = author.getBasePermissions().block().contains(permission);
							if(!result){
								CommandRequirement.errorMessage("Sorry " + BotUtility.getNameInGuild(author) +
												", you have to have the permission: **\"" + permission + "\"** to change the prefix of this bot"
										, message);
							}
							return result;
						};
	}

}
