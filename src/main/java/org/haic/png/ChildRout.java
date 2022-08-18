package org.haic.png;

import org.haic.often.FilesUtils;
import org.haic.often.Multithread.MultiThreadUtils;
import org.haic.often.Network.JsoupUtil;
import org.haic.often.Network.Method;
import org.haic.often.ReadWriteUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ChildRout {

	public static void initialization() {
		ChildRout.exitTask(); // 输出程序运行时间
		ChildRout.outInfo(); // 输出程序信息
		ChildRout.exitSystem(); // 存储空间不足退出程序
		ChildRout.createProjectFile(); // 创建项目文件
	}

	/**
	 * 创建项目文件
	 */
	public static void createProjectFile() {
		// Pixiv
		FilesUtils.createFile(App.pixiv_whitelabels_filePath);
		FilesUtils.createFile(App.pixiv_blacklabels_filePath);
		FilesUtils.createFile(App.pixiv_record_date_filePath);
		FilesUtils.createFile(App.pixiv_already_usedid_filePath);
		FilesUtils.createFile(App.pixiv_authors_uid_filePath);
		// Sankaku
		FilesUtils.createFile(App.sankaku_cookies_filePath);
		FilesUtils.createFile(App.sankaku_whitelabels_filePath);
		FilesUtils.createFile(App.sankaku_blacklabels_filePath);
		FilesUtils.createFile(App.sankaku_already_usedid_filePath);
		// Yande
		FilesUtils.createFile(App.yande_whitelabels_filePath);
		FilesUtils.createFile(App.yande_blacklabels_filePath);
		FilesUtils.createFile(App.yande_record_date_filePath);
		FilesUtils.createFile(App.yande_already_usedid_filePath);
		FilesUtils.createFile(App.yande_cookies_filePath);
	}

	public static void exitTask() {
		long start = System.currentTimeMillis(); // 获取开始时间
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			long end = System.currentTimeMillis(); // 获取结束时间
			System.out.println("[RESULT] 程序运行时间：" + (end - start) / 1000 + "s 本次共计下载 " + App.imageCount + " 张图片");
		}));
	}

	public static void WriteFileInfo(String str, String filePath) {
		ReadWriteUtils.orgin(filePath).text(str);
	}

	public static void outInfo() {
		String info = """
				*********************************************************************************
				*                                喜欢挑三拣四的图片爬虫                        \t*
				*                                   作者: haicdust                            \t*
				*                                    仅供学习使用                              \t*
				*                            支持网站 Yande Sankaku Pixiv                      \t*
				*                              最后更新: 2022/2/18 06:46                       \t*
				*********************************************************************************
				""";
		System.out.println(info);
	}

	/**
	 * 获取网站登陆cookies
	 *
	 * @param domin    网站域名
	 * @param userName 用户名
	 * @param password 密码
	 * @return cookies
	 */
	public static Map<String, String> GetLoginCookies(@NotNull String domin, @NotNull String userName, @NotNull String password) {
		Map<String, String> params = new HashMap<>();
		params.put("user[name]", userName);
		params.put("user[password]", password);
		String loginUrl = domin + "user/authenticate";
		return JsoupUtil.connect(loginUrl).timeout(10000).data(params).proxy(App.proxyHost, App.proxyPort).retry(2, App.MILLISECONDS_SLEEP).errorExit(true)
				.method(Method.POST).execute().cookies();
	}

	/**
	 * 存储空间不足时,停止程序
	 */
	public static void exitSystem() {
		File file = new File(App.image_folderPath);
		new Thread(() -> { // 执行多线程程
			while (true) {
				if (file.getFreeSpace() / 1024 / 1024 / 1024 < 10) {
					System.out.println("存储空间不足,停止程序!");
					System.exit(1);
				}
				MultiThreadUtils.WaitForThread(10000);
			}
		}).start();

	}
}
