package Bot.Logger;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.MessageChannel;

public class DiscordLogger {

	private MessageChannel logChannel;

	public DiscordLogger(MessageChannel logChannel){
		this.logChannel = logChannel;
	}

	public void log(String message){
		logChannel.createMessage(message).subscribe();
	}

	public void log(String message, Guild guild){
		log("In guild: **" + guild.getName() + "**: " + message);
	}

	public void log(ReadyEvent event){
		log("ReadyEvent triggered, bot is now online");
	}
}
