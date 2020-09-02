package bot.soraka.features.commands;

import bot.utility.BotUtility;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;


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




	boolean check(Message message);
	static void errorMessage(String content, Message message){
		MessageChannel channel = message.getChannel().block();
		channel.createMessage(content).block();
	}

}
