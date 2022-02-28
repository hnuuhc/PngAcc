package org.haic.png.Yande;

import org.haic.often.FilesUtils;
import org.haic.often.Judge;
import org.haic.often.Multithread.MultiThreadUtils;
import org.haic.often.Multithread.ParameterizedThread;
import org.haic.often.Network.HttpStatus;
import org.haic.often.Network.JsoupUtil;
import org.haic.often.Network.NetworkFileUtil;
import org.haic.often.Network.URIUtils;
import org.haic.often.ReadWriteUtils;
import org.haic.png.App;
import org.haic.png.ChildRout;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class YandeSubfunction {

	private static final boolean record_usedid = App.yande_record_usedid; // 记录已下载的图片ID
	private static final boolean bypass_usedid = App.yande_bypass_usedid; // 跳过已记录的图片ID
	private static final boolean bypass_blacklabels = App.yande_bypass_blacklabels; // 标签黑名单

	private static final String image_folderPath = FilesUtils.getAbsolutePath(App.yande_image_folderPath);
	private static final String blacklabelFilePath = App.yande_blacklabels_filePath; // 黑名单文件
	private static final String alreadyUsedIdFilePath = App.yande_already_usedid_filePath; // 记录ID文件
	private static final String proxyHost = App.proxyHost;

	private static final int proxyPort = App.proxyPort;
	private static final int DOWN_THREADS = App.DOWN_THREADS; // 单文件下载线程
	private static final boolean MAX_RETRY = App.MAX_RETRY; // 最大重试次数
	private static final int MILLISECONDS_SLEEP = App.MILLISECONDS_SLEEP; // 程序等待
	private static final int API_MAX_THREADS = App.yande_api_maxthreads; // 访问API最大线程
	private static final int limit = App.yande_api_limit; // API单页获取数量限制

	private static boolean isInitialization; // 判断参数是否已初始化

	public static Map<String, String> cookies = new HashMap<>();
	public static Map<String, String> filesMd5 = new HashMap<>();

	public static List<String> blacklabels = new ArrayList<>();
	public static List<String> usedIds = new CopyOnWriteArrayList<>();

	private static final Logger logger = LoggerFactory.getLogger(YandeSubfunction.class);

	public static void initialization() {
		if (!isInitialization) {
			cookies = YandeLogin.GetCookies();
			blacklabels = ReadWriteUtils.orgin(blacklabelFilePath).list();
			blacklabels.replaceAll(label -> label.replaceAll(" ", "_"));
			usedIds = ReadWriteUtils.orgin(alreadyUsedIdFilePath).list().parallelStream().map(info -> info.split(" ")[0]).collect(Collectors.toList());
			isInitialization = true;
		}
	}

	public static Map<String, String> GetLabelImagesInfo(String whitelabel) {
		Map<String, String> imagesInfo = new ConcurrentHashMap<>();
		String url = "https://yande.re/post.xml?tags=" + whitelabel + "&limit=1";
		Document doc = JsoupUtil.connect(url).proxy(proxyHost, proxyPort).cookies(cookies).retry(MAX_RETRY, MILLISECONDS_SLEEP).get();
		int postCount = Integer.parseInt(Objects.requireNonNull(doc.selectFirst("posts")).attr("count"));
		ExecutorService executorService = Executors.newFixedThreadPool(API_MAX_THREADS); // 限制多线程
		for (int i = 1; i <= (int) Math.ceil((double) postCount / (double) limit); i++, MultiThreadUtils.WaitForThread(36)) {
			executorService.execute(new ParameterizedThread<>(i, (index) -> { // 执行多线程程
				String whitelabelUrl = "https://yande.re/post.xml?tags=" + whitelabel + "&page=" + index + "&limit=" + limit;
				Elements posts = JsoupUtil.connect(whitelabelUrl).proxy(proxyHost, proxyPort).cookies(cookies).retry(MAX_RETRY, MILLISECONDS_SLEEP).get()
						.select("post");
				forPosts:
				for (Element post : posts) {
					String imageid = post.attr("id");
					if (bypass_usedid && usedIds.contains(imageid)) {
						continue;
					}
					String[] image_labels = post.attr("tags").split(" ");
					for (String image_label : image_labels) {
						if (bypass_blacklabels && blacklabels.contains(image_label)) {
							continue forPosts;
						}
					}
					filesMd5.put(imageid, post.attr("md5"));
					imagesInfo.put(imageid, post.attr("file_url"));
				}
			}));
		}
		MultiThreadUtils.WaitForEnd(executorService); // 等待线程结束
		return new HashMap<>(imagesInfo);
	}

	public static Map<String, String> GetParentImagesInfo(String parentImageId) {
		return GetParentImagesInfo(parentImageId, new ArrayList<>());
	}

	public static Map<String, String> GetParentImagesInfo(String parentImageId, List<String> childrenImageidLists) {
		Map<String, String> imagesInfo = new HashMap<>();
		String parentIdUrl = "https://yande.re/post.xml?tags=parent%3A" + parentImageId + "&limit=" + limit;
		Document doc = JsoupUtil.connect(parentIdUrl).proxy(proxyHost, proxyPort).cookies(cookies).retry(MAX_RETRY, MILLISECONDS_SLEEP).get();
		if (Judge.isNull(doc)) {
			System.out.println("连接URL失败：" + parentIdUrl);
			return imagesInfo;
		}
		forPosts:
		for (Element post : doc.select("post")) {
			String imageid = post.attr("id");
			if (imageid.equals(parentImageId) && !childrenImageidLists.contains(parentImageId)) {
				String new_parent_imageid = post.attr("parent_id");
				if (!new_parent_imageid.equals("")) {
					childrenImageidLists.add(parentImageId);
					return GetParentImagesInfo(new_parent_imageid, childrenImageidLists);
				}
			}
			String[] image_labels = post.attr("tags").split(" ");
			for (String image_label : image_labels) {
				if (bypass_blacklabels && blacklabels.contains(image_label)) {
					continue forPosts;
				}
			}
			if (bypass_usedid && usedIds.contains(imageid)) {
				continue;
			}
			String imageUrl = post.attr("file_url");
			if (!Judge.isEmpty(imageUrl)) {
				filesMd5.put(imageid, post.attr("md5"));
				imagesInfo.put(imageid, imageUrl);
			}
		}
		return imagesInfo;
	}

	public static Map<String, String> GetHeatdayImagesInfo(int year, int month, int day) {
		String heatdayUrl = "https://yande.re/post/popular_by_day.xml?day=" + day + "&month=" + month + "&year=" + year;
		Map<String, String> imagesInfo = new ConcurrentHashMap<>();
		Document doc = JsoupUtil.connect(heatdayUrl).proxy(proxyHost, proxyPort).cookies(cookies).retry(MAX_RETRY, MILLISECONDS_SLEEP).get();
		if (Judge.isNull(doc)) {
			System.out.println("连接URL失败：" + heatdayUrl);
			return imagesInfo;
		}
		ExecutorService executorService = Executors.newFixedThreadPool(API_MAX_THREADS); // 限制多线程
		for (Element post : doc.select("post")) { // 执行多线程程序
			executorService.execute(new Thread(() -> { // 程序
				String imageid = post.attr("id");
				for (String imagelabel : post.attr("tags").split(" ")) {
					if (bypass_blacklabels && blacklabels.contains(imagelabel)) {
						return;
					}
				}
				String parentImageid = post.attr("parent_id");
				if (!parentImageid.equals("")) {
					Map<String, String> parent_imageInfos = YandeSubfunction.GetParentImagesInfo(parentImageid);
					if (!parent_imageInfos.isEmpty()) {
						imagesInfo.putAll(parent_imageInfos);
					}
				} else if (Boolean.parseBoolean(post.attr("has_children"))) {
					Map<String, String> parent_imageInfos = YandeSubfunction.GetParentImagesInfo(imageid);
					if (!parent_imageInfos.isEmpty()) {
						imagesInfo.putAll(parent_imageInfos);
					}
				} else {
					if (bypass_usedid && usedIds.contains(imageid)) {
						return;
					}
					String imageUrl = post.attr("file_url");
					if (!Judge.isEmpty(imageUrl)) {
						filesMd5.put(imageid, post.attr("md5"));
						imagesInfo.put(imageid, imageUrl);
					}
				}
			}));
		}
		MultiThreadUtils.WaitForEnd(executorService); // 等待线程结束
		return new HashMap<>(imagesInfo);
	}

	public static void download(String imageid, String imageUrl) {
		String imageidUrl = "https://yande.re/post/show/" + imageid;
		logger.info("Yande - 正在下载 ID: " + imageid + " URL: " + imageidUrl);
		usedIds.add(imageid);
		int statusCode = NetworkFileUtil.connect(imageUrl).proxy(proxyHost, proxyPort).hash(filesMd5.get(imageid)).retry(MAX_RETRY, MILLISECONDS_SLEEP)
				.multithread(DOWN_THREADS).download(image_folderPath);
		if (statusCode == HttpStatus.SC_TOO_MANY_REQUEST) {
			statusCode = NetworkFileUtil.connect(imageUrl).proxy(proxyHost, proxyPort).retry(MAX_RETRY, MILLISECONDS_SLEEP).header("accept-encoding", "")
					.hash(filesMd5.get(imageid)).multithread(DOWN_THREADS).download(image_folderPath);
			if (statusCode == HttpStatus.SC_TOO_MANY_REQUEST) {
				System.out.println("Error: 429 URL: " + imageUrl);
				System.exit(1);
			}
		}
		if (URIUtils.statusIsOK(statusCode)) {
			App.imageCount.addAndGet(1);
			if (record_usedid) {
				ChildRout.WriteFileInfo(imageid + " " + filesMd5.get(imageid), alreadyUsedIdFilePath);
			}
		} else {
			logger.error("Yande - 下载失败 ID: " + imageid + " URL: " + imageidUrl);
		}
	}

}