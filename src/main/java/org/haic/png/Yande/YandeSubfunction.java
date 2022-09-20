package org.haic.png.Yande;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.haic.often.FilesUtils;
import org.haic.often.Judge;
import org.haic.often.ReadWriteUtils;
import org.haic.often.RemDuplication;
import org.haic.often.Multithread.ConsumerThread;
import org.haic.often.Multithread.MultiThreadUtil;
import org.haic.often.Network.HttpStatus;
import org.haic.often.Network.HttpsUtil;
import org.haic.often.Network.JsoupUtil;
import org.haic.often.Network.NetworkUtil;
import org.haic.often.Network.Response;
import org.haic.often.Network.URIUtils;
import org.haic.png.App;
import org.haic.png.ChildRout;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

public class YandeSubfunction {

	private static final boolean record_usedid = App.yande_record_usedid; // 记录已下载的图片ID
	private static final boolean bypass_low_quality = App.yande_bypass_low_quality;
	private static final boolean bypass_usedid = App.yande_bypass_usedid; // 跳过已记录的图片ID
	private static final boolean bypass_blacklabels = App.yande_bypass_blacklabels; // 标签黑名单
	private static final boolean MAX_RETRY = App.MAX_RETRY; // 最大重试次数

	private static final String image_folderPath = FilesUtils.getAbsolutePath(App.yande_image_folderPath);
	private static final String blacklabelFilePath = App.yande_blacklabels_filePath; // 黑名单文件
	private static final String alreadyUsedIdFilePath = App.yande_already_usedid_filePath; // 记录ID文件
	private static final String proxyHost = App.proxyHost;

	private static final int proxyPort = App.proxyPort;
	private static final int DOWN_THREADS = App.DOWN_THREADS; // 单文件下载线程
	private static final int MILLISECONDS_SLEEP = App.MILLISECONDS_SLEEP; // 程序等待
	private static final int API_MAX_THREADS = App.yande_api_maxthreads; // 访问API最大线程
	private static final int limit = App.yande_api_limit; // API单页获取数量限制
	private static final int global_label_amount = App.yande_global_label_amount;
	private static final int MAX_LOW_QUALITY = 250000;

	private static boolean isInitialization; // 判断参数是否已初始化

	public static Map<String, String> cookies = new HashMap<>();

	public static List<String> blacklabels = new ArrayList<>();
	public static List<String> usedIds = new CopyOnWriteArrayList<>();

	private static final Logger logger = LoggerFactory.getLogger(YandeSubfunction.class);

	public static void initialization() {
		if (!isInitialization) {
			cookies = YandeLogin.GetCookies();
			blacklabels = ReadWriteUtils.orgin(blacklabelFilePath).readAsLine();
			blacklabels.replaceAll(label -> label.replaceAll(" ", "_"));
			usedIds = ReadWriteUtils.orgin(alreadyUsedIdFilePath).readAsLine().parallelStream()
					.map(info -> info.split(" ")[0]).collect(Collectors.toList());
			isInitialization = true;
		}
	}

	public static List<JSONObject> GetLabelImagesInfo(String whitelabel) {
		List<JSONObject> imagesInfo = new CopyOnWriteArrayList<>();
		String url = "https://yande.re/post.xml?tags=" + whitelabel + "&limit=1";
		Document doc = JsoupUtil.connect(url).proxy(proxyHost, proxyPort).cookies(cookies)
				.retry(MAX_RETRY, MILLISECONDS_SLEEP).get();
		int postCount = Integer.parseInt(Objects.requireNonNull(doc.selectFirst("posts")).attr("count"));
		ExecutorService executorService = Executors.newFixedThreadPool(API_MAX_THREADS); // 限制多线程
		for (int i = 1; i <= (int) Math.ceil((double) postCount / (double) limit); i++, MultiThreadUtil
				.waitForThread(36)) {
			executorService.execute(new ConsumerThread<>(i, (index) -> { // 执行多线程程
				String whitelabelUrl = "https://yande.re/post.json?tags=" + whitelabel + "&page=" + index + "&limit="
						+ limit;
				Response labelInfo = JsoupUtil.connect(whitelabelUrl).proxy(proxyHost, proxyPort).cookies(cookies)
						.retryStatusCodes(502)
						.retry(MAX_RETRY, MILLISECONDS_SLEEP).execute();
				for (JSONObject post : JSONObject.parseArray(labelInfo.body(), JSONObject.class)) {
					String imageid = post.getString("id");
					if (bypass_low_quality && Integer.parseInt(imageid) < MAX_LOW_QUALITY) {
						continue;
					}
					if (bypass_usedid && usedIds.contains(imageid)) {
						continue;
					}
					if (bypass_blacklabels && Arrays.stream(post.getString("tags").split(" "))
							.anyMatch(l -> blacklabels.contains(l))) {
						continue;
					}
					imagesInfo.add(post);
				}
			}));
		}
		MultiThreadUtil.waitForEnd(executorService); // 等待线程结束
		return new ArrayList<>(imagesInfo);
	}

	public static List<JSONObject> GetLabelImagesInfoAsGlobal(List<String> whitelabels) {
		List<JSONObject> imagesInfo = new CopyOnWriteArrayList<>();
		ExecutorService executorService = Executors.newFixedThreadPool(API_MAX_THREADS); // 限制多线程
		for (int i = 1; i <= (int) Math.ceil((double) global_label_amount / (double) limit); i++, MultiThreadUtil
				.waitForThread(36)) { // 20w是最大值
			executorService.execute(new ConsumerThread<>(i, (index) -> { // 执行多线程程
				String whitelabelUrl = "https://yande.re/post.json?page=" + index + "&limit=" + limit;
				Response res = JsoupUtil.connect(whitelabelUrl).proxy(proxyHost, proxyPort).cookies(cookies)
						.retryStatusCodes(502)
						.retry(MAX_RETRY, MILLISECONDS_SLEEP).execute();
				if (res.statusCode() == 500) {
					throw new RuntimeException("Status: 500 URL: " + whitelabelUrl);
				}
				for (JSONObject post : JSONObject.parseArray(res.body(), JSONObject.class)) {
					String imageid = post.getString("id");
					if (bypass_low_quality && Integer.parseInt(imageid) < MAX_LOW_QUALITY) {
						continue;
					}
					if (bypass_usedid && usedIds.contains(imageid)) {
						continue;
					}
					String[] labels = post.getString("tags").split(" ");
					if (Arrays.stream(labels).noneMatch(whitelabels::contains)) {
						continue;
					}
					if (bypass_blacklabels && Arrays.stream(labels).anyMatch(l -> blacklabels.contains(l))) {
						continue;
					}
					imagesInfo.add(post);
				}
			}));
		}
		MultiThreadUtil.waitForEnd(executorService); // 等待线程结束
		return new ArrayList<>(imagesInfo);
	}

	public static List<JSONObject> GetParentImagesInfo(String parentImageId) {
		return GetParentImagesInfo(parentImageId, new ArrayList<>());
	}

	public static List<JSONObject> GetParentImagesInfo(String parentImageId, List<String> childrenImageidLists) {
		List<JSONObject> imagesInfo = new ArrayList<>();
		String parentIdUrl = "https://yande.re/post.json?tags=parent%3A" + parentImageId + "&limit=" + limit;
		Response res = HttpsUtil.connect(parentIdUrl).proxy(proxyHost, proxyPort).cookies(cookies)
				.retry(MAX_RETRY, MILLISECONDS_SLEEP).execute();
		if (!URIUtils.statusIsOK(res.statusCode())) {
			System.out.println("连接URL失败：" + parentIdUrl);
			return imagesInfo;
		}
		for (JSONObject post : JSONObject.parseArray(res.body(), JSONObject.class)) {
			String imageid = post.getString("id");
			if (bypass_low_quality && Integer.parseInt(imageid) < MAX_LOW_QUALITY) {
				continue;
			}
			if (imageid.equals(parentImageId) && !childrenImageidLists.contains(parentImageId)) {
				String new_parent_imageid = post.getString("parent_id");
				if (!Judge.isEmpty(new_parent_imageid)) {
					childrenImageidLists.add(parentImageId);
					return GetParentImagesInfo(new_parent_imageid, childrenImageidLists);
				}
			}
			if (bypass_blacklabels
					&& Arrays.stream(post.getString("tags").split(" ")).anyMatch(l -> blacklabels.contains(l))) {
				continue;
			}
			if (bypass_usedid && usedIds.contains(imageid)) {
				continue;
			}
			if (!Judge.isEmpty(post.getString("file_url"))) {
				imagesInfo.add(post);
			}
		}
		return imagesInfo;
	}

	public static List<JSONObject> GetHeatdayImagesInfo(int year, int month, int day) {
		String heatdayUrl = "https://yande.re/post/popular_by_day.json?day=" + day + "&month=" + month + "&year="
				+ year;
		List<JSONObject> imagesInfo = new CopyOnWriteArrayList<>();
		Response res = JsoupUtil.connect(heatdayUrl).proxy(proxyHost, proxyPort).cookies(cookies)
				.retry(MAX_RETRY, MILLISECONDS_SLEEP).execute();
		if (!URIUtils.statusIsOK(res.statusCode())) {
			System.out.println("连接URL失败：" + heatdayUrl);
			return imagesInfo;
		}
		ExecutorService executorService = Executors.newFixedThreadPool(API_MAX_THREADS); // 限制多线程
		for (JSONObject post : JSONObject.parseArray(res.body(), JSONObject.class)) {
			executorService.execute(new Thread(() -> { // 程序
				String imageid = post.getString("id");
				if (bypass_low_quality && Integer.parseInt(imageid) < MAX_LOW_QUALITY) {
					return;
				}
				if (bypass_blacklabels
						&& Arrays.stream(post.getString("tags").split(" ")).anyMatch(l -> blacklabels.contains(l))) {
					return;
				}
				String parentImageid = post.getString("parent_id");
				if (!Judge.isEmpty(parentImageid)) {
					imagesInfo.addAll(YandeSubfunction.GetParentImagesInfo(parentImageid));
				} else if (post.getBoolean("has_children")) {
					imagesInfo.addAll(YandeSubfunction.GetParentImagesInfo(imageid));
				}
				if (bypass_usedid && usedIds.contains(imageid)) {
					return;
				}
				if (!Judge.isEmpty(post.getString("file_url"))) {
					imagesInfo.add(post);
				}
			}));
		}
		MultiThreadUtil.waitForEnd(executorService); // 等待线程结束
		return RemDuplication.HashSet(imagesInfo); // 多线程操作可能会存在重复项,需要去重处理
	}

	public static void download(JSONObject imageInfo) {
		String imageid = imageInfo.getString("id");
		String imageidUrl = "https://yande.re/post/show/" + imageid;
		logger.info("正在下载 ID: " + imageid + " URL: " + imageidUrl);
		usedIds.add(imageid);
		String imageUrl = imageInfo.getString("file_url");
		String md5 = imageInfo.getString("md5");
		NetworkUtil.Connection config = NetworkUtil.connect(imageUrl).proxy(proxyHost, proxyPort).hash(md5)
				.retry(MAX_RETRY, MILLISECONDS_SLEEP)
				.multithread(DOWN_THREADS);
		NetworkUtil.Response res;
		int statusCode;
		do {
			res = config.download(image_folderPath);
			statusCode = res.statusCode();
		} while (statusCode == HttpStatus.SC_TOO_MANY_REQUEST);
		if (statusCode == HttpStatus.SC_SERVER_RESOURCE_ERROR) {// 尝试修复服务端文件错误
			imageUrl = "https://files.yande.re/image/" + md5 + "/yande.re." + imageInfo.getString("file_ext");
			res.clear();
			statusCode = config.url(imageUrl).download(image_folderPath).statusCode();
		}
		if (URIUtils.statusIsOK(statusCode)) {
			App.imageCount.addAndGet(1);
			if (record_usedid) {
				ChildRout.WriteFileInfo(imageid + " " + md5, alreadyUsedIdFilePath);
			}
		} else {
			logger.error("下载失败 Status: " + statusCode + " URL: " + imageidUrl);
		}
	}

}
