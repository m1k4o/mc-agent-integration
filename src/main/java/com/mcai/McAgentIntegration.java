package com.mcai;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McAgentIntegration implements ModInitializer {
	public static final String MOD_ID = "mc-agent-integration";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[mc-agent-integration] loaded (client commands registered in client entrypoint)");
	}
}
