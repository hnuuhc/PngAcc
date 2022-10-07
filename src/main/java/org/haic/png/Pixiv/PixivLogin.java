package org.haic.png.Pixiv;

import org.haic.often.ChromeBrowser.LocalCookie;
import org.haic.png.App;

import java.util.HashMap;
import java.util.Map;

public class PixivLogin {
	private static final String browser_userDataPath = App.browser_userDataPath;

	private static final boolean employ_cookies = App.pixiv_employ_cookies; // 使用cookies

	public static Map<String, String> GetCookies() {
		Map<String, String> cookies = new HashMap<>();
		if (employ_cookies) {
			cookies = LocalCookie.home(browser_userDataPath).getForDomain("pixiv.net");
		}
		return cookies;
	}

}