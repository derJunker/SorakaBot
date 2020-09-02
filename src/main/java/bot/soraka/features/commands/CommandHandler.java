package bot.soraka.features.commands;

import bot.logger.DiscordLogger;
import bot.utility.BotUtility;
import bot.utility.MemManager;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Permission;

import java.util.*;

public class CommandHandler {

	private final Map<String, Command> availableCommands;
	private final Map<Guild, String> prefixes;

	private static final String DEFAULT_PREFIX = "!";

	private final DiscordLogger logger;
	private final GatewayDiscordClient client;

	public CommandHandler(DiscordLogger logger, GatewayDiscordClient client){
		this.logger = logger;
		this.client = client;
		availableCommands = new HashMap<>();
		prefixes = MemManager.loadPrefixes(client);
		fillUpPrefixMissingGuilds();
		addMiscCommands();
	}

	//methods to add and remove a command
	public Command addCommand(String name, Executable executable){
		return availableCommands.put(name, new Command(executable));
	}

	public Command addCommand(String name, Executable executable, List<CommandRequirement> requirements){
		return availableCommands.put(name, new Command(requirements, executable));
	}

	public Command addCommand(String name, Command command){
		return availableCommands.put(name, command);
	}

	public void addCommands(Map<String, Command> commands){
		availableCommands.putAll(commands);
	}


	public Command remove(String key){
		return availableCommands.remove(key);
	}

	public boolean remove(String key, Command command){
		return availableCommands.remove(key, command);
	}

	public boolean execute(Message message){
		String content = message.getContent();
		String prefix = guildPrefixOrDefault(message);

		for (Map.Entry<String, Command> entry : availableCommands.entrySet()) {
			// The variable COMMAND_PREFIX determines what initiates a commandline.
			if (content.startsWith(prefix + entry.getKey())) {
				logger.log("Executed command: **" + entry.getKey() + "**", message);
				entry.getValue().execute(message);
				return true;
			}
		}
		return false;
	}

	private void addMiscCommands(){
		//these vars are used to make a command
		List<CommandRequirement> requirements = new LinkedList<>();
		Executable executable;

		//adding the ping command
		//no requirements needed
		executable =  message -> {
			MessageChannel channel = message.getChannel().block();
			channel.createMessage("pong").block();
		};
		addCommand("ping", executable);

		//adding the help command
		//no requirements needed
		executable = message -> {
			String prefix = guildPrefixOrDefault(message);
			MessageChannel channel = message.getChannel().block();
			StringBuilder content = new StringBuilder().append("commands: \n");
			content.append("-------------------------------\n");
			//listing all the possible commands
			availableCommands.forEach((name, ignored) -> content.append("**" + prefix + name + "**\n"));
			content.append("-------------------------------\n");
			channel.createMessage(content.toString()).block();
		};
		addCommand("help", executable);

		//adding the prefix command
		//there are two requirements for this command
		//the user has to send he message from a guildChannel
		//the user has to have the permission MANAGE_SERVER
		requirements.add(CommandRequirement.IN_GUILD);
		//for the next requirement you can assume the requirements before that are true
		requirements.add(message -> {
			Member author = message.getAuthorAsMember().block();
			boolean result = author.getBasePermissions().block().contains(Permission.MANAGE_GUILD);
			if(!result){
				CommandRequirement.errorMessage("Sorry " + BotUtility.getNameInGuild(author) +
												", you have to have the permission: **\"manage guild\"** to change the prefix of this bot"
												, message);
			}
			return result;
		});
		executable = message ->{
			Guild guild = message.getGuild().block();
			String prefix = prefixes.get(guild);
			MessageChannel channel = message.getChannel().block();

			String content = message.getContent();
			//splitting the command by its spaces
			//the first one is the command itself, the second should be the prefix
			String[] contentSegments = content.split(" ");
			//before assigning the new prefix, check if there are more than 2 segments (if so the syntax isn't uphold
			if(contentSegments.length != 2){
				channel.createMessage("Wrong usage of this command, see " + prefix + "help for help").block();
				return;
			}
			logger.log("Changed Prefix from: **" + prefix + "** to: **" + contentSegments[1] + "**");

			//now if there are only two arguments the second should adjust the prefix
			prefix = contentSegments[1];
			channel.createMessage("Okay, from now on the prefix is: **" + prefix + "**").block();
			//finally store the new Prefix first in the prefix map and then save prefixes
			prefixes.put(guild, prefix);
			MemManager.savePrefixes(prefixes, client);
		};
		addCommand("prefix", executable, requirements);
	}

	private String guildPrefixOrDefault(Message message){

		return BotUtility.inGuild(message) ?  prefixes.get(message.getGuild().block()) : DEFAULT_PREFIX;
	}

	public void addToPrefixes(Guild guild){
		prefixes.putIfAbsent(guild,DEFAULT_PREFIX);
	}

	private void fillUpPrefixMissingGuilds(){
		client.getGuilds().toStream().forEach(guild -> addToPrefixes(guild));
	}
}
