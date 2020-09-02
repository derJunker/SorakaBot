package bot.soraka.features.commands.parts;

import bot.soraka.SorakaBot;
import bot.utility.Description;
import discord4j.core.object.entity.Message;

import java.util.ArrayList;
import java.util.List;

public class Command {

	private Executable command;
	private List<CommandRequirement> requirements;
	private Description description;


	public Command(List<CommandRequirement> requirements, Executable command, Description description){
		this.requirements = new ArrayList<>(requirements);
		this.command = command;
		this.description = description;
	}

	public Command(Executable command, Description description){
		this.command = command;
		requirements = new ArrayList<>();
		this.description = description;
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

	public Description getDescription(){
		return description;
	}
}
