package bot.logger;

import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;

public class DiscordLogger {

	private MessageChannel logChannel;

	public DiscordLogger(MessageChannel logChannel){
		this.logChannel = logChannel;
	}

	public void log(String message){
		logChannel.createMessage(message).block();
	}

	public void log(String message, Guild guild){
		log("In guild: **" + guild.getName() + "**: " + message);
	}

	public void log(String content, Message message){
		//for logging
		Guild guild = message.getGuild().blockOptional().orElse(null);
		User author = message.getAuthor().get();

		String origin = "Via DM: **" + author.getUsername() + "**: ";
		if(guild != null){
			GuildChannel guildChannel = (GuildChannel) message.getChannel().block();
			origin = "In channel: **" + guild.getName() + "/#" + guildChannel.getName() + "**: ";
		}
		log(origin + content);
	}

	public void log(ReadyEvent event){
		log("ReadyEvent triggered, bot is now online");
	}
}
