package com.deathreturn.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.deathreturn.network.RewindPayload;

public class DeathReturnClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 收到服务端的回溯通知 -> 在客户端主线程上启动回溯流程
		ClientPlayNetworking.registerGlobalReceiver(RewindPayload.TYPE, (payload, context) ->
				context.client().execute(() -> RewindController.begin(payload.levelName(), payload.reason())));
	}
}
