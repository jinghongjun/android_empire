package com.empire.library.communication.protocol.http;

public abstract class HttpRequestListener {

	public abstract void onSuccess(String result);

	/**
	 * 请求失败
	 * @param type
	 * @param arg1
	 * @return false 不处理失败信息， true 处理失败信息
	 */
	public boolean onFailure(int type, String arg1) {
		return false;
	}
}
