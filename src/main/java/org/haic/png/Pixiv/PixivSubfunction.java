package org.haic.png.Pixiv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpStatus;
import org.haic.often.logger.Logger;
import org.haic.often.logger.LoggerFactory;
import org.haic.often.net.Method;
import org.haic.often.net.URIUtil;

import org.haic.often.net.download.SionConnection;
import org.haic.often.net.download.SionDownload;
import org.haic.often.net.download.SionResponse;
import org.haic.often.net.http.HttpsUtil;
import org.haic.often.parser.json.JSONArray;
import org.haic.often.parser.json.JSONObject;

import org.haic.often.thread.ConsumerThread;
import org.haic.often.tuple.Tuple;
import org.haic.often.tuple.record.ThreeTuple;
import org.haic.often.util.FileUtil;
import org.haic.often.util.ReadWriteUtil;
import org.haic.often.util.StringUtil;
import org.haic.often.util.ThreadUtil;
import org.haic.png.App;
import org.haic.png.ChildRout;

public class PixivSubfunction {

	private static final String domain = App.pixiv_url;
	private static final String blacklabelFilePath = App.pixiv_blacklabels_filePath;
	private static final String alreadyUsedIdFilePath = App.pixiv_already_usedid_filePath;
	private static final String imageFolderPath = FileUtil.getAbsolutePath(App.pixiv_image_folderPath);

	private static final String proxyHost = App.proxyHost;
	private static final int proxyPort = App.proxyPort;

	private static final int MAX_API = App.pixiv_api_maxthreads; // API线程
	private static final boolean MAX_RETRY = App.MAX_RETRY; // 最大重试次数
	private static final int DOWN_THREADS = App.DOWN_THREADS; // 单文件下载线程
	private static final int MILLISECONDS_SLEEP = App.MILLISECONDS_SLEEP; // 程序等待
	private static final int limit = App.pixiv_api_limit;

	private static final boolean recordUsedId = App.pixiv_record_usedid; // 记录已下载的图片ID
	private static final boolean record_usedid = App.pixiv_record_usedid; // 记录已下载的图片ID
	private static final boolean bypassBlacklabel = App.pixiv_bypass_blacklabels; // 跳过黑名单标签
	private static final boolean bypassUsedId = App.pixiv_bypass_usedid; // 跳过已记录的图片ID

	private static boolean isInitialization; // 判断参数是否已初始化

	private static String x_csrf_token;

	public static Map<String, String> cookies = new HashMap<>();
	public static List<String> blacklabels = new ArrayList<>();
	public static List<String> usedIds = new CopyOnWriteArrayList<>();

	private static final Logger logger = LoggerFactory.getLogger(PixivSubfunction.class);

	/**
	 * 初始化参数
	 */
	public static void initialization() {
		if (!isInitialization) {
			cookies = PixivLogin.GetCookies();
			blacklabels = ReadWriteUtil.orgin(blacklabelFilePath).readAsLine();
			blacklabels.replaceAll(label -> label.replaceAll(" ", "_"));
			usedIds = ReadWriteUtil.orgin(alreadyUsedIdFilePath).readAsLine();
			x_csrf_token = JSONObject.parseObject(
					HttpsUtil.connect(domain).cookies(cookies).proxy(proxyHost, proxyPort)
							.retry(MAX_RETRY, MILLISECONDS_SLEEP).get().parse()
							.selectFirst("meta[id='meta-global-data']").attr("content")).getString("token");
			isInitialization = true;
		}
	}

	public static Set<ThreeTuple<String, List<String>, String>> GetLabelImagesInfo(String whitelabel) {
		var label_api_url = "https://www.pixiv.net/ajax/search/artworks/" + whitelabel + "?mode=all&s_mode=s_tag_full&type=illust"; // API接口
		var illustManga = HttpsUtil.connect(label_api_url).proxy(proxyHost, proxyPort).cookies(cookies)
										  .retry(MAX_RETRY, MILLISECONDS_SLEEP).get().json()
										  .getJSONObject("body").getJSONObject("illustManga");
		Set<ThreeTuple<String, List<String>, String>> imagesInfo = PixivSubfunction
				.imageInfosOfJSONArray(illustManga.getJSONArray("data"));
		int total = Integer.parseInt(illustManga.getString("total")); // 获取图片数量
		var executorService = Executors.newFixedThreadPool(MAX_API); // 限制多线程
		for (int i = 2; i <= (int) Math.ceil((double) total / (double) 60); i++) { // limit=60为固定值
			executorService.execute(new ConsumerThread(i, (index) -> { // 执行多线程程
				var url = "https://www.pixiv.net/ajax/search/artworks/" + whitelabel + "?mode=all&s_mode=s_tag_full&type=illust&p=" + index;
				var data = HttpsUtil.connect(url).proxy(proxyHost, proxyPort).cookies(cookies)
								.retry(MAX_RETRY, MILLISECONDS_SLEEP).get().json()
								.getJSONObject("body").getJSONObject("illustManga").getJSONArray("data");// 获取数组
				imagesInfo.addAll(imageInfosOfJSONArray(data));
			}));
		}
		ThreadUtil.waitEnd(executorService); // 等待线程结束
		return imagesInfo;
	}

	public static Set<ThreeTuple<String, List<String>, String>> GetAuthorInfos(String uid) { // 获取用户的图片信息列表 //
		// Set<ThreeTuple<图片ID,下载链接列表,文件名>>
		var url = "https://www.pixiv.net/ajax/user/" + uid + "/profile/all?lang=zh"; // API接口
		Set<ThreeTuple<String, List<String>, String>> imagesInfo = new HashSet<>();
		var illusts = HttpsUtil.connect(url).proxy(proxyHost, proxyPort).cookies(cookies)
							   .retry(MAX_RETRY, MILLISECONDS_SLEEP).get().json().getJSONObject("body").getJSONObject("illusts");
		var executorService = Executors.newFixedThreadPool(MAX_API); // 限制多线程
		for (String imageId : illusts.keySet()) {
			if (bypassUsedId && usedIds.contains(imageId)) {
				continue;
			}
			executorService.execute(new Thread(() -> { // 程序
				ThreeTuple<String, List<String>, String> imageInfo = GetImageUrls(imageId);
				if (imageInfo != null) {
					imagesInfo.add(imageInfo);
				}
			}));
		}
		ThreadUtil.waitEnd(executorService); // 等待线程结束
		return imagesInfo;
	}

	public static ThreeTuple<String, List<String>, String> GetImageUrls(String imageId) {
		var url = "https://www.pixiv.net/ajax/illust/" + imageId; // API接口
		JSONObject body = null;
		while (body == null) {
			var info = HttpsUtil.connect(url).proxy(proxyHost, proxyPort).cookies(cookies)
							.retry(MAX_RETRY, MILLISECONDS_SLEEP).get().json();
			if (!info.getBoolean("error")) {
				body = JSONObject.parseObject(info.getString("body"));
			}
		}
		var labels = JSONArray.parseArray(JSONObject.parseObject(body.getString("tags")).getString("tags"));
		var fileName = new StringBuilder("pixiv " + imageId);
		for (int i = 0; i < labels.size(); i++) {
			var imagelabel = labels.getJSONObject(i).getString("tag");
			if (bypassBlacklabel && blacklabels.contains(imagelabel)) {
				return null;
			}
			if (!imagelabel.contains("users入り")
					&& FileUtil.nameLength(fileName.toString()) + FileUtil.nameLength(imagelabel) + 1 < 220) {
				fileName.append(" ").append(imagelabel);
			}
		}
		var fileUrl = JSONObject.parseObject(body.getString("urls")).getString("original");
		List<String> imageUrls = new ArrayList<>();
		imageUrls.add(fileUrl);
		for (int i = 1; i < Integer.parseInt(body.getString("pageCount")); i++) {
			var suffix = fileUrl.substring(fileUrl.lastIndexOf("."));
			var newUrl = fileUrl.substring(0, fileUrl.length() - suffix.length() - 1);
			imageUrls.add(newUrl + i + suffix);
		}
		return Tuple.of(imageId, imageUrls, fileName.toString());
	}

	/**
	 * 关注用户
	 *
	 * @param userId 用户UID
	 * @return 关注是否成功
	 */
	public static boolean bookmarkAdd(String userId) {
		String url = "https://www.pixiv.net/bookmark_add.php"; // API接口
		Map<String, String> params = new HashMap<>();
		params.put("mode", "add");
		params.put("type", "user");
		params.put("restrict", "0");
		params.put("format", "json");
		params.put("user_id", userId);
		return URIUtil.statusIsOK(HttpsUtil.connect(url).header("x-csrf-token", x_csrf_token)
				.proxy(proxyHost, proxyPort).data(params).cookies(cookies)
				.retry(1, MILLISECONDS_SLEEP).method(Method.POST).execute().statusCode());
	}

	/**
	 * 取消关注用户
	 *
	 * @param userId 用户UID
	 * @return 取消关注是否成功
	 */
	public static boolean bookmarkDel(String userId) {
		String url = "https://www.pixiv.net/rpc_group_setting.php"; // API接口
		Map<String, String> params = new HashMap<>();
		params.put("mode", "del");
		params.put("type", "bookuser");
		params.put("id", userId);
		return URIUtil.statusIsOK(HttpsUtil.connect(url).header("x-csrf-token", x_csrf_token)
				.proxy(proxyHost, proxyPort).data(params).cookies(cookies)
				.retry(1, MILLISECONDS_SLEEP).method(Method.POST).execute().statusCode());
	}

	/**
	 * 获取 关注的用户UID列表
	 *
	 * @return 关注的用户UID列表
	 */
	public static List<String> GetFollowUserIds() {
		List<String> userIds = new CopyOnWriteArrayList<>();
		var userId = GetUserId();
		int total = GetFollowTotal();
		var executorService = Executors.newFixedThreadPool(MAX_API); // 限制多线程
		for (int i = 0; i < (int) Math.ceil((double) total / (double) limit); i++) {
			executorService.execute(new ConsumerThread(i, (index) -> { // 执行多线程程
				var url = "https://www.pixiv.net/ajax/user/" + userId + "/following?rest=show&tag=&lang=zh&limit="
						+ limit + "&offset=" + limit * index; // API接口
				var jsonObject = HttpsUtil.connect(url).proxy(proxyHost, proxyPort).cookies(cookies)
								.retry(MAX_RETRY, MILLISECONDS_SLEEP).get().json();
				var body = JSONObject.parseObject(jsonObject.getString("body"));
				var users = JSONArray.parseArray(body.getString("users"));
				for (int j = 0; j < users.size(); j++) {
					userIds.add(users.getJSONObject(j).getString("userId"));
				}
			}));
		}
		ThreadUtil.waitEnd(executorService); // 等待线程结束
		return userIds;
	}

	/**
	 * 获取 关注的用户数量
	 *
	 * @return 关注的用户数量
	 */
	public static int GetFollowTotal() {
		String url = "https://www.pixiv.net/ajax/user/extra?lang=zh"; // API接口
		return JSONObject.parseObject(JSONObject.parseObject(
				HttpsUtil.connect(url).proxy(proxyHost, proxyPort).cookies(cookies).retry(MAX_RETRY, MILLISECONDS_SLEEP)
						.execute().body())
				.getString("body"))
				.getInteger("following");
	}

	/**
	 * 获取 本人的用户ID
	 *
	 * @return 用户ID
	 */
	public static String GetUserId() {
		String url = "https://www.pixiv.net/setting_user.php"; // API接口
		return HttpsUtil.connect(url).proxy(proxyHost, proxyPort).cookies(cookies).retry(MAX_RETRY, MILLISECONDS_SLEEP)
				.execute().header("x-userid");
	}

	/**
	 * 获取 每日热门图片的链接信息
	 *
	 * @param currentDate 日期 格式-20201012
	 * @return 每日热门图片的链接信息
	 */
	public static Set<ThreeTuple<String, List<String>, String>> GetHeatdayImageInfos(String currentDate) {
		Set<ThreeTuple<String, List<String>, String>> imagesInfo = new CopyOnWriteArraySet<>();
		List<String> heatdayUrls = new ArrayList<>();
		for (int i = 1; i <= 2; i++) {
			var heatdayUrl = "https://www.pixiv.net/ranking.php?mode=daily_r18&format=json&date=" + currentDate
					+ "&p=" + i; // API:接口 R18
			heatdayUrls.add(heatdayUrl);
		}
		for (int i = 1; i <= 10; i++) {
			var heatdayUrl = "https://www.pixiv.net/ranking.php?mode=daily&format=json&date=" + currentDate + "&p="
					+ i; // API接口
			heatdayUrls.add(heatdayUrl);
		}
		ExecutorService executorService = Executors.newFixedThreadPool(MAX_API); // 限制多线程
		for (String heatdayUrl : heatdayUrls) { // 执行多线程程
			executorService.execute(new Thread(() -> { // 程序
				var contents =  HttpsUtil.connect(heatdayUrl).cookies(cookies).proxy(proxyHost, proxyPort)
											   .retry(MAX_RETRY, MILLISECONDS_SLEEP).get().json().getJSONArray("contents");// 获取数组
				imagesInfo.addAll(imageInfosOfJSONArray(contents, "illust_type", "illust_id", "illust_page_count"));
			}));
		}
		ThreadUtil.waitEnd(executorService);
		return new HashSet<>(imagesInfo);
	}

	public static Set<ThreeTuple<String, List<String>, String>> GetOptimalImageInfos() {
		var url = "https://www.pixiv.net/ajax/top/illust?mode=all&lang=zh"; // API接口
		var jsonObject = HttpsUtil.connect(url).proxy(proxyHost, proxyPort).cookies(cookies)
								  .retry(MAX_RETRY, MILLISECONDS_SLEEP).get().json();
		var body = JSONObject.parseObject(jsonObject.getString("body"));
		var thumbnails = JSONObject.parseObject(body.getString("thumbnails"));
		var illusts = thumbnails.getJSONArray("illust");// 获取数组
		return imageInfosOfJSONArray(illusts);
	}

	public static Set<ThreeTuple<String, List<String>, String>> GetSuggestionImageInfos() {
		var url = "https://www.pixiv.net/ajax/search/suggestion?mode=all"; // API接口
		var json = HttpsUtil.connect(url).proxy(proxyHost, proxyPort).cookies(cookies)
				.retry(MAX_RETRY, MILLISECONDS_SLEEP).get().json();
		var thumbnails = json.getJSONObject("body").getJSONArray("thumbnails");// 获取数组
		return imageInfosOfJSONArray(thumbnails);
	}

	public static Set<ThreeTuple<String, List<String>, String>> imageInfosOfJSONArray(JSONArray jsonArray) {
		return imageInfosOfJSONArray(jsonArray, "illustType", "id", "pageCount");
	}

	public static Set<ThreeTuple<String, List<String>, String>> imageInfosOfJSONArray(JSONArray jsonArray,
			String illustTypeName, String imageIdName,
			String pageCountName) {
		Set<ThreeTuple<String, List<String>, String>> imagesInfo = new HashSet<>();
		for_jsonArray: for (int i = 0; i < jsonArray.size(); i++) { // 提取出family中的所有
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			String illustType = jsonObject.getString(illustTypeName);
			if (!illustType.equals("0")) { // illustType: 0-插画 1-漫画 2-动图
				continue;
			}
			List<String> fileUrls = new ArrayList<>();
			String imageId = String.valueOf(jsonObject.get(imageIdName));
			if (bypassUsedId && usedIds.contains(imageId)) {
				continue;
			}
			String[] imagelabels = String.valueOf(jsonObject.get("tags")).replaceAll("[\\[\"\\]]", "").split(",");
			StringBuilder filename = new StringBuilder("pixiv " + imageId);
			for (String imagelabel : imagelabels) {
				if (bypassBlacklabel && blacklabels.contains(imagelabel)) {
					continue for_jsonArray;
				}
				if (!imagelabel.contains("users入り")
						&& FileUtil.nameLength(filename.toString()) + FileUtil.nameLength(imagelabel) + 1 < 220) {
					filename.append(" ").append(imagelabel);
				}
			}
			String imageDate = StringUtil.extract(String.valueOf(jsonObject.get("url")),
					"\\d+/\\d+/\\d+/\\d+/\\d+/\\d+/");
			int pageCount = Integer.parseInt(String.valueOf(jsonObject.get(pageCountName)));
			String headUrl = "https://i.pximg.net/img-original/img/";
			for (int count = 0; count < pageCount; count++) { // 获取文件URL,无后缀格式
				String suffixUrl = imageId + "_p" + count;
				fileUrls.add(headUrl + imageDate + suffixUrl);
			}
			imagesInfo.add(Tuple.of(imageId, fileUrls, filename.toString()));
		}
		return imagesInfo;
	}

	public static void download(String imageId, List<String> fileUrls, String fileName) {
		String imageIdUrl = "https://www.pixiv.net/artworks/" + imageId;
		PixivSubfunction.usedIds.add(imageId);
		boolean downStatus = true;

		for (int i = 1; i <= fileUrls.size(); i++) {
			String imagefileUrl = fileUrls.get(i - 1);
			String suffix = imagefileUrl.substring(imagefileUrl.lastIndexOf("."));
			logger.info("正在下载: " + imageIdUrl + (fileUrls.size() > 1 ? "" : " " + i + "/" + fileUrls.size()));
			int statusCode = SionDownload.connect(imagefileUrl).proxy(proxyHost, proxyPort).referrer(imageIdUrl)
					.fileName(i > 1 ? fileName + " " + i + suffix : fileName + suffix)
					.retry(MAX_RETRY, MILLISECONDS_SLEEP).thread(DOWN_THREADS)
					.folder(imageFolderPath).execute().statusCode();
			if (URIUtil.statusIsOK(statusCode)) {
				App.imageCount.addAndGet(1);
			} else {
				downStatus = false;
			}
		}

		if (recordUsedId && downStatus) {
			ChildRout.WriteFileInfo(imageId, alreadyUsedIdFilePath);
		} else {
			logger.error("Pixiv - 下载失败 ID: " + imageId + " URL: " + imageIdUrl);
		}
	}

	public static void noSuffixDownload(String imageId, List<String> fileUrls, String fileName) {
		String imageIdUrl = "https://www.pixiv.net/artworks/" + imageId;
		usedIds.add(imageId);
		int count = 0;
		for (int i = 1; i <= fileUrls.size(); i++) {
			String log = "正在下载: " + imageIdUrl;
			logger.info(log + (fileUrls.size() > 1 ? " " + i + "/" + fileUrls.size() : ""));
			String suffix = ".jpg";

			SionConnection conn = SionDownload.connect(fileUrls.get(i - 1) + suffix).proxy(proxyHost, proxyPort)
					.referrer(imageIdUrl)
					.fileName(i > 1 ? fileName + " " + suffix : fileName + suffix).thread(DOWN_THREADS)
					.retry(MAX_RETRY, MILLISECONDS_SLEEP)
					.folder(imageFolderPath);
			SionResponse res = conn.execute();
			int statusCode = res.statusCode();
			if (statusCode == HttpStatus.SC_NOT_FOUND) {
				suffix = ".png";
				res = conn.url(fileUrls.get(i - 1) + suffix)
						.fileName(i > 1 ? fileName + " " + suffix : fileName + suffix).execute();
				statusCode = res.statusCode();
			}
			if (URIUtil.statusIsOK(statusCode)) {
				App.imageCount.addAndGet(1);
				count++;
			} else {
				logger.error("下载失败 Status: " + statusCode + " URL: " + res.url());
				break;
			}
		}
		if (record_usedid && count == fileUrls.size()) {
			ChildRout.WriteFileInfo(imageId, alreadyUsedIdFilePath);
		}
	}

}
