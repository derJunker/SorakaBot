package discord.bot.features.commands.parts;

import discord.bot.SorakaBot;
import discord.utility.BotUtility;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Permission;

import java.util.Arrays;

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
								", you have to have the permission: **\"" + permissions + "\"**  to execute this command!"
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
												", you have to have the permission: **\"" + permission + "\"** to execute this command!"
										, message);
							}
							return result;
						};
	}

	/**
	 * returns a CommandRequirement, which checks if the bot has the right permission
	 * it assumes you are on a guild
	 * @param permission the permission to be met
	 * @return returns a method(1 method interface) which where it checks if said permissions are met
	 */
	static CommandRequirement botHasPermission(final Permission permission){
		return message -> {
			//because its required that this message was sent in a guild
			//i can assume there will be no error getting the author as a member
			Guild guild = message.getGuild().block();
			Member botMember = guild.getMemberById(SorakaBot.getSelf().getId()).block();
			//checking if the author of the message has the permission to manage the guild
			//this is the requirement to change the prefix
			boolean result = botMember.getBasePermissions().block().contains(permission);
			if(!result){
				CommandRequirement.errorMessage("Sorry " + BotUtility.getNameInGuild(botMember) +
								" has to have the permission: **\"" + permission + "\"**  to execute this command!"
						, message);
			}
			return result;
		};
	}

	/**
	 * checks if the message has the right amount of arguments
	 * @param segments the expected amount of arguments
	 * @return returns if it has the right amount
	 */
	static CommandRequirement correctSyntaxSegmentAmount (int segments){
		return message -> {

			String content = message.getContent();
			//splitting the command by its spaces
			//the first one is the command itself, the second should be the prefix
			String[] contentSegments = content.split(" ");
			//before assigning the new prefix, check if there are the right amount of segments (if so the syntax isn't uphold)
			boolean result = contentSegments.length == segments;
			if(!result){
				MessageChannel channel = message.getChannel().block();
				channel.createMessage("SyntaxError, too many or missing arguments, use the **help** command for information").block();
			}
			return result;
		};
	}
	/**
	 * checks if the message has one of the right amount of arguments
	 * @param possibleSegments the expected amount of arguments
	 * @return returns if it has the right amount
	 */
	static CommandRequirement correctSyntaxSegmentAmount (int... possibleSegments){
		return message -> {

			String content = message.getContent();
			//splitting the command by its spaces
			//the first one is the command itself, the second should be the prefix
			String[] contentSegments = content.split(" ");
			//before assigning the new prefix, check if there is the right amount of segments (if so the syntax isn't uphold)
			boolean result = Arrays.stream(possibleSegments).anyMatch(segment -> segment == contentSegments.length);
			if(!result){
				MessageChannel channel = message.getChannel().block();
				channel.createMessage("SyntaxError, too many or missing arguments, use the **help** command for information").block();
			}
			return result;
		};
	}

}
