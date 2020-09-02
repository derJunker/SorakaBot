package bot.soraka.features.commands;

import bot.logger.DiscordLogger;
import bot.soraka.features.commands.parts.Command;
import bot.soraka.features.commands.parts.CommandRequirement;
import bot.soraka.features.commands.parts.Executable;
import bot.utility.BotUtility;
import bot.utility.Description;
import bot.utility.MemManager;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
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

	/**
	 * adds a command to the list of available commands
	 * @param name the name of the command (gets called with !name) if ! is the prefix
	 * @param executable the code which gets executed when the command gets triggered
	 * @param description the description of the command
	 * @return the command of the hashMap if there was a collision
	 */
	public Command addCommand(String name, Executable executable, Description description){
		return availableCommands.put(name, new Command(executable, description));
	}
	/**
	 * adds a command to the list of available commands
	 * @param name the name of the command (gets called with !name) if ! is the prefix
	 * @param executable the code which gets executed when the command gets triggered
	 * @param description the description of the command
	 * @param requirements the requirements which the member has to meet before the command gets executed
	 * @return the command of the hashMap if there was a collision
	 */
	public Command addCommand(String name, Executable executable, List<CommandRequirement> requirements, Description description){
		return availableCommands.put(name, new Command(requirements, executable, description));
	}

	/**
	 * adds a command to the list of available commands
	 * @param name the name of the command (gets called with !name) if ! is the prefix
	 * @param command the command
	 * @return the command of the hashMap if there was a collision
	 */
	public Command addCommand(String name, Command command){
		return availableCommands.put(name, command);
	}

	/**
	 * adding multiple commands
	 * @param commands the map for the names and the commands itself
	 */
	public void addCommands(Map<String, Command> commands){
		availableCommands.putAll(commands);
	}


	/**
	 * removes a command by its name
	 * @param key the name of the command
	 * @return returns the command
	 */
	public Command remove(String key){
		return availableCommands.remove(key);
	}

	/**
	 * removes an entry if the key and value are the same
	 * @param key the name of the command
	 * @param command the command
	 * @return returns if somthing has been removed
	 */
	public boolean remove(String key, Command command){
		return availableCommands.remove(key, command);
	}

	/**
	 * this method executes a command written in a message if there was one
	 * @param message the message the command should be in
	 * @return returns if a command has been executed
	 */
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

	/**
	 * this method adds some miscellaneous commands
	 * which have no dependency to a feature
	 * ping, help, prefix
	 */
	private void addMiscCommands(){
		//these vars are used to make a command
		//for each new command they get set some value
		List<CommandRequirement> requirements;
		Executable executable;
		Description description;

		//adding the ping command
		//no requirements needed
		requirements = new LinkedList<>();
		//it just replies pong
		executable =  message -> {
			MessageChannel channel = message.getChannel().block();
			channel.createMessage("pong").block();
		};
		description = new Description("pings the bot -- if online the bot responds with pong");
		addCommand("ping", executable, description);


		requirements = new LinkedList<>();
		description = new Description("A more hands on approach for help");
		executable = message ->{
			MessageChannel channel = message.getChannel().block();
			channel.createMessage("Moritz heul nicht rum").block();
		};
		addCommand("Hilfeeee", executable, description);

		//adding the prefix command
		//there are two requirements for this command
		//the user has to send he message from a guildChannel
		//the user has to have the permission MANAGE_SERVER
		requirements.add(CommandRequirement.IN_GUILD);
		//for the next requirement you can assume the requirements before that are true
		requirements.add(CommandRequirement.hasPermission(Permission.MANAGE_GUILD));
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
		description = new Description("it changes the prefix for commands: syntax **!prefix $newPrefix**, no spaces in the newPrefix!");
		addCommand("prefix", executable, requirements, description);

		//adding the help command
		//no requirements needed
		//it gives out every command with a description
		requirements = new LinkedList<>();
		description = new Description("gives out every possible command, with a description");
		executable = message -> {
			//first get the prefix of the guild or the default if not in the guild
			//to accurately represent the commands
			String prefix = guildPrefixOrDefault(message);
			MessageChannel channel = message.getChannel().block();
			//now build the help message
			StringBuilder content = new StringBuilder().append("commands: \n");
			content.append("-------------------------------\n");
			//listing all the possible commands
			availableCommands.forEach((name, command) -> {

				content.append("**" + prefix + name + "** -> " + command.getDescription().vShort() + "\n");
			});
			content.append("-------------------------------\n");
			//then send the message
			channel.createMessage(content.toString()).block();
		};
		addCommand("help", executable, description);
	}

	/**
	 * this method gets the prefix of the guild, or if the message was sent in a dm the default prefix
	 * @param message contains in which guild/dm the message was sent
	 * @return returns the right prefix, which will be used to interpret commands
	 */
	private String guildPrefixOrDefault(Message message){
		//if you are in a guild return the set prefix of that guild, except if there is no entry for it
		//then return the default prefix, as well as when you are not in a guild
		if(BotUtility.inGuild(message)){
			String prefix = prefixes.get(message.getGuild().block());
			if(prefix != null)
				return prefix;
		}
		return DEFAULT_PREFIX;
	}

	/**
	 * this method puts in the default value for a guild, if they are not in the map already
	 * @param guild the guild to insert
	 */
	public void addToPrefixes(Guild guild){
		prefixes.putIfAbsent(guild,DEFAULT_PREFIX);
	}

	/**
	 * checks every guild the bot is on, and in every guild there is no set prefix set it to the default one
	 */
	private void fillUpPrefixMissingGuilds(){
		client.getGuilds().toStream().forEach(this::addToPrefixes);
	}
}
