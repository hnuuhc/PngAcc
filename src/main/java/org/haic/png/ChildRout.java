package org.haic.png;

import org.haic.often.FilesUtil;
import org.haic.often.Multithread.MultiThreadUtil;
import org.haic.often.Network.JsoupUtil;
import org.haic.often.Network.Method;
import org.haic.often.ReadWriteUtil;
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
		FilesUtil.createFile(App.pixiv_whitelabels_filePath);
		FilesUtil.createFile(App.pixiv_blacklabels_filePath);
		FilesUtil.createFile(App.pixiv_record_date_filePath);
		FilesUtil.createFile(App.pixiv_already_usedid_filePath);
		FilesUtil.createFile(App.pixiv_authors_uid_filePath);
		// Sankaku
		FilesUtil.createFile(App.sankaku_cookies_filePath);
		FilesUtil.createFile(App.sankaku_whitelabels_filePath);
		FilesUtil.createFile(App.sankaku_blacklabels_filePath);
		FilesUtil.createFile(App.sankaku_already_usedid_filePath);
		// Yande
		FilesUtil.createFile(App.yande_whitelabels_filePath);
		FilesUtil.createFile(App.yande_blacklabels_filePath);
		FilesUtil.createFile(App.yande_record_date_filePath);
		FilesUtil.createFile(App.yande_already_usedid_filePath);
		FilesUtil.createFile(App.yande_cookies_filePath);
	}

	public static void exitTask() {
		long start = System.currentTimeMillis(); // 获取开始时间
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			long end = System.currentTimeMillis(); // 获取结束时间
			System.out.println("[RESULT] 程序运行时间：" + (end - start) / 1000 + "s 本次共计下载 " + App.imageCount + " 张图片");
		}));
	}

	public static void WriteFileInfo(String str, String filePath) {
		ReadWriteUtil.orgin(filePath).write(str + "\n");
	}

	public static void outInfo() {
		String info = """
				[INFO] 图片爬虫
				[INFO] 作者: haicdust
				[INFO] 仅供学习使用,禁止用于违法用途
				[INFO] 支持网站 Yande Sankaku Pixiv
				[INFO] 最后更新: 2022/9/23 18:11
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
		FilesUtil.createFolder(file);
		new Thread(() -> { // 执行多线程程
			while (true) {
				if (file.getFreeSpace() / 1024 / 1024 / 1024 < 10) {
					System.out.println("存储空间不足,停止程序!");
					System.exit(1);
				}
				MultiThreadUtil.waitForThread(10000);
			}
		}).start();

	}
}