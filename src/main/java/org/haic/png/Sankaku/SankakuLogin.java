package org.haic.png.Sankaku;

import org.haic.often.ChromeBrowser.LocalCookie;
import org.haic.often.Judge;
import org.haic.often.ReadWriteUtil;
import org.haic.often.StringUtil;
import org.haic.png.App;
import org.haic.png.ChildRout;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SankakuLogin {

	private static final String domain = App.sankaku_url;
	private static final String user_name = App.sankaku_user_name;
	private static final String user_password = App.sankaku_user_password;
	private static final String cookies_filePath = App.sankaku_cookies_filePath;

	private static final boolean employ_cookies = App.sankaku_employ_cookies; // 使用cookies
	private static final boolean browser_cookies = App.sankaku_browser_cookies;// 使用浏览器cookie否则尝试登陆
	private static final String browser_userDataPath = App.browser_userDataPath;

	public static Map<String, String> GetCookies() {
		Map<String, String> cookies = new HashMap<>();
		if (employ_cookies) {
			if (browser_cookies) {
				cookies = LocalCookie.home(browser_userDataPath).getCookiesForDomain("sankakucomplex.com");
			} else {
				cookies = StringUtil.toMap(ReadWriteUtil.orgin(cookies_filePath).read(), "; ");
				if (cookies.isEmpty() && !Judge.isEmpty(user_name) && !Judge.isEmpty(user_password)) {
					cookies = ChildRout.GetLoginCookies(domain, user_name, user_password);
					ChildRout.WriteFileInfo(cookies.entrySet().stream().map(l -> l.getKey() + "=" + l.getValue()).collect(Collectors.joining("; ")),
							cookies_filePath);
				}
			}
		}
		return cookies;
	}

}