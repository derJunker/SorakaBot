package bot.soraka.features.commands;

import bot.soraka.SorakaBot;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.PermissionSet;

import java.util.ArrayList;
import java.util.List;

public class Command {
	private Executable command;
	List<CommandRequirement> requirements;

	Command(List<CommandRequirement> requirements, Executable command){
		this.requirements = new ArrayList<>(requirements);
		this.command = command;
	}

	Command(Executable command){
		this.command = command;
		requirements = new ArrayList<>();
	}

	public void execute(Message message){
		//first check if all the requirements are met
		for (CommandRequirement requirement : requirements) {
			if(!requirement.check(message)){
				SorakaBot.getLogger().log("Requirements for command not met");
				return;
			}
		}
		//if so then execute the command
		command.execute(message);
	}
}
