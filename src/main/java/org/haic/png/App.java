package org.haic.png;

import org.haic.png.Pixiv.PixivImagesDownload;
import org.haic.png.Sankaku.SankakuImagesDownload;
import org.haic.png.Sankaku.SankakuLogin;
import org.haic.png.Yande.YandeImagesDownload;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class App {
	// **************************** MiXin *********************************
	public static String image_folderPath = "F:\\Pictures"; // 图片保存路径
	public static String browser_userDataPath = System.getProperty("user.home") + "\\AppData\\Local\\Microsoft\\Edge\\User Data";

	public static String proxyHost = "127.0.0.1"; // 代理主机
	public static int proxyPort = 7890; // 代理端口

	public static int MAX_THREADS = 4; // 多线程下载,建议4-8
	public static int DOWN_THREADS = 2; // 单文件下载线程
	public static boolean MAX_RETRY = true; // 无限重试
	public static int MILLISECONDS_SLEEP = 1000; // 重试间隔时间(毫秒)

	public static AtomicInteger imageCount = new AtomicInteger(0);

	// **************************** Pixiv *********************************
	public static String pixiv_url = "https://www.pixiv.net/";

	public static String pixiv_image_folderPath = image_folderPath + "/Pixiv";

	public static String pixiv_start_date = "2020-08-01"; // 开始日期

	public static boolean pixiv_employ_cookies = true;// 使用cookie
	public static boolean pixiv_bypass_blacklabels = true; // 跳过黑名单标签
	public static boolean pixiv_record_usedid = true; // 记录已下载的图片ID
	public static boolean pixiv_bypass_usedid = true; // 跳过已记录的图片ID
	public static boolean pixiv_record_date = true; // 记录已完成的日期
	public static boolean pixiv_bypass_record_date = true; // 跳过记录的日期
	public static boolean pixiv_unbypass_within_aweek = true; // 不跳过一星期内的日期
	public static boolean pixiv_synchronized_attention = false; // 同步关注用户

	public static String pixiv_whitelabels_filePath = "data/PixivData/Pixiv_WhiteLabels.txt";
	public static String pixiv_blacklabels_filePath = "data/PixivData/Pixiv_BlackLabels.txt";
	public static String pixiv_record_date_filePath = "data/PixivData/Pixiv_RecordDate.txt"; // 日期文件
	public static String pixiv_already_usedid_filePath = "data/PixivData/Pixiv_AlreadyUsedId.txt";
	public static String pixiv_authors_uid_filePath = "data/PixivData/Pixiv_AuthorUIDs.txt";

	public static int pixiv_api_maxthreads = 40; // 访问API最大线程
	public static int pixiv_api_limit = 100; // API单页获取数量限制

	// **************************** Sankaku *********************************
	public static String sankaku_url = "https://chan.sankakucomplex.com/";
	public static String sankaku_image_folderPath = image_folderPath + "/Sankaku";

	public static String sankaku_user_name = ""; // 账号
	public static String sankaku_user_password = ""; // 密码

	public static boolean sankaku_employ_cookies = true;// 使用cookie
	public static boolean sankaku_browser_cookies = true;// 使用浏览器cookie否则尝试登陆
	public static boolean sankaku_bypass_blacklabels = true; // 跳过黑名单标签
	public static boolean sankaku_record_usedid = true; // 记录已下载的图片ID
	public static boolean sankaku_bypass_usedid = true; // 跳过已记录的图片ID

	public static String sankaku_cookies_filePath = "data/SankakuData/Sankaku_Cookies.txt";
	public static String sankaku_whitelabels_filePath = "data/SankakuData/Sankaku_WhiteLabels.txt";
	public static String sankaku_blacklabels_filePath = "data/SankakuData/Sankaku_BlackLabels.txt";
	public static String sankaku_already_usedid_filePath = "data/SankakuData/Sankaku_AlreadyUsedId.txt"; // 记录ID文件

	// public static int sankaku_api_maxthreads = 40; // 访问API最大线程
	public static int sankaku_api_limit = 100; // API单页获取数量限制

	public static Map<String, String> sankaku_cookies = SankakuLogin.GetCookies(); // cookies

	// **************************** Yande.re *********************************
	public static String yande_url = "https://yande.re/";
	public static String yande_image_folderPath = image_folderPath + "/Yande";

	public static String yande_user_name = ""; // 账号
	public static String yande_user_password = ""; // 密码

	public static String yande_start_date = "2018-01-01"; // 开始日期

	public static boolean yande_employ_cookies = true;// 使用cookie
	public static boolean yande_browser_cookies = true;// 使用浏览器cookie否则尝试登陆
	public static boolean yande_record_date = true; // 记录已完成的日期
	public static boolean yande_bypass_record_date = true; // 跳过记录的日期
	public static boolean yande_unbypass_within_aweek = true; // 不跳过一星期内的日期
	public static boolean yande_record_usedid = true; // 记录已下载的图片ID
	public static boolean yande_bypass_usedid = true; // 跳过已记录的图片ID
	public static boolean yande_bypass_blacklabels = true; // 跳过黑名单标签
	public static boolean yande_bypass_low_quality = true; // 跳过早期低质量图片
	public static boolean yande_global_label = true; // 全局方式更新前n条数据的白名单标签图片,在标签过多时可有效提升效率

	public static String yande_whitelabels_filePath = "data/YandeData/Yande_WhiteLabels.txt"; // 白名单文件
	public static String yande_blacklabels_filePath = "data/YandeData/Yande_BlackLabels.txt"; // 黑名单文件
	public static String yande_record_date_filePath = "data/YandeData/Yande_RecordDate.txt"; // 日期文件
	public static String yande_already_usedid_filePath = "data/YandeData/Yande_AlreadyUsedId.txt"; // 记录ID文件
	public static String yande_cookies_filePath = "data/YandeData/Yande_Cookies.txt"; // cookies文件

	public static int yande_api_maxthreads = 40; // 访问API最大线程
	public static int yande_api_limit = 1000; // API单页获取数量限制,最大值为1000
	public static int yande_global_min_site = 200001; // 全局方式模式时获取数据条目最小起始位
	public static int yande_global_max_site = 240000; // 全局方式模式时获取数据条目结束位

	public static void main(String[] args) {
		ChildRout.initialization(); // 初始化
		YandeImagesDownload.popularDaily(); // Yande 每日热门
		YandeImagesDownload.label(); // Yande 标签
		YandeImagesDownload.blackGlobal(); // Yande 最新图片(排除黑名单标签)
		SankakuImagesDownload.label(); // Sankaku 标签
		PixivImagesDownload.popularDaily(); // Pixiv 每日热门
		PixivImagesDownload.suggestion(); // Pixiv 推荐
		PixivImagesDownload.author(); // Pixiv 作者
		PixivImagesDownload.optimal(); // Pixiv 最佳
		PixivImagesDownload.label(); // Pixiv 标签
		System.exit(0);
	}
}