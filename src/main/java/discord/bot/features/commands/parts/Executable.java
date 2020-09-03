package discord.bot.features.commands.parts;

import discord4j.core.object.entity.Message;

public interface Executable {
	void execute(Message message);
}
