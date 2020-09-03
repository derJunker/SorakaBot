package discord.bot.features.commands.parts;

import discord.bot.SorakaBot;
import discord.utility.Description;
import discord4j.core.object.entity.Message;

import java.util.ArrayList;
import java.util.List;

public class Command {

	//the method the command executes
	private Executable command;
	//a list of methods which all have to be true, that the command can be executed
	private List<CommandRequirement> requirements;
	//the description of what the command does
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

	/**
	 * this method executes the command if the requirements are true
	 * @param message the message where the command is described
	 */
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
