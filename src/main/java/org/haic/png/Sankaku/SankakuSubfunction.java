package org.haic.png.Sankaku;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.haic.often.Judge;
import org.haic.often.logger.Logger;
import org.haic.often.logger.LoggerFactory;
import org.haic.often.net.URIUtil;
import org.haic.often.net.download.SionDownload;
import org.haic.often.net.http.HttpsUtil;
import org.haic.often.parser.json.JSONArray;
import org.haic.often.parser.json.JSONObject;
import org.haic.often.tuple.Tuple;
import org.haic.often.tuple.record.ThreeTuple;
import org.haic.often.util.FileUtil;
import org.haic.often.util.ReadWriteUtil;
import org.haic.png.App;
import org.haic.png.ChildRout;

public class SankakuSubfunction {

	private static final String sankaku_url = App.sankaku_url;
	private static final String sankaku_api_url = "https://capi-v2.sankakucomplex.com/posts"; // posts/keyset

	private static final String image_folderPath = FileUtil.getAbsolutePath(App.sankaku_image_folderPath);

	private static final String blacklabels_filePath = App.sankaku_blacklabels_filePath;
	private static final String already_usedid_filePath = App.sankaku_already_usedid_filePath;

	private static final boolean record_usedid = App.sankaku_record_usedid; // 记录已下载的图片ID
	private static final boolean bypass_usedid = App.sankaku_bypass_usedid; // 跳过已记录的图片ID
	private static final boolean bypass_blacklabels = App.sankaku_bypass_blacklabels;
	private static boolean isInitialization; // 判断参数是否已初始化

	private static final String proxyHost = App.proxyHost; // 代理
	private static final int proxyPort = App.proxyPort;

	private static final int limit = App.sankaku_api_limit; // API单页获取数量限制
	private static final int DOWN_THREADS = App.DOWN_THREADS; // 单文件下载线程
	private static final boolean MAX_RETRY = App.MAX_RETRY; // 最大重试次数
	private static final int MILLISECONDS_SLEEP = App.MILLISECONDS_SLEEP; // 程序等待

	private static Map<String, String> cookies = App.sankaku_cookies;

	public static List<String> blacklabels = new ArrayList<>();
	private static List<String> usedIds = new CopyOnWriteArrayList<>();

	private static final Logger logger = LoggerFactory.getLogger(SankakuSubfunction.class);

	public static void initialization() {
		if (!isInitialization) {
			cookies = SankakuLogin.GetCookies();
			blacklabels = ReadWriteUtil.orgin(blacklabels_filePath).readAsLine();
			blacklabels.replaceAll(label -> label.replaceAll(" ", "_"));
			usedIds = ReadWriteUtil.orgin(already_usedid_filePath).readAsLine().parallelStream()
					.map(info -> info.split(" ")[0]).collect(Collectors.toList());
			isInitialization = true;
		}
	}

	public static String getImageUrl(String imageid) {
		var labelurl_doc = HttpsUtil.connect(sankaku_url + "cn/post/show/" + imageid).timeout(12000).proxy(proxyHost, proxyPort).cookies(cookies).retry(MAX_RETRY, MILLISECONDS_SLEEP).get().parse();
		return "https:" + Objects.requireNonNull(labelurl_doc.selectFirst("a[id='image-link']")).attr("href");
	}

	public static Set<ThreeTuple<String, String, String>> getLabelInfo(String whitelabel) {
		return getLabelInfo(whitelabel, sankaku_api_url + "?tags=" + whitelabel + "&limit=" + limit);
	}

	private static Set<ThreeTuple<String, String, String>> getLabelInfo(String whitelabel, String label_api_url) {
		Set<ThreeTuple<String, String, String>> imagesInfo = new HashSet<>();
		var jsonObject = HttpsUtil.connect(label_api_url).timeout(12000).proxy(proxyHost, proxyPort).cookies(cookies)
				.retry(MAX_RETRY, MILLISECONDS_SLEEP)
				.get().json();
		var next = JSONObject.parseObject(jsonObject.getString("meta")).getString("next");
		if (next != null) {
			String next_label_api_url = sankaku_api_url + "?tags=" + whitelabel + "&limit=" + limit + "&next=" + next;
			imagesInfo.addAll(getLabelInfo(whitelabel, next_label_api_url));
		}
		JSONArray jsonArray = jsonObject.getJSONArray("data");// 获取数组
		for_jsonArray: for (int i = 0; i < jsonArray.size(); i++) {
			JSONObject data_jsonObject = jsonArray.getJSONObject(i);
			String imageid = data_jsonObject.getString("id");
			if (bypass_usedid && usedIds.contains(imageid)) {
				continue;
			}
			JSONArray tags_jsonArray = data_jsonObject.getJSONArray("tags");// 获取数组
			StringBuilder filename = new StringBuilder("sankaku " + imageid);
			for (int j = 0; j < tags_jsonArray.size(); j++) {
				JSONObject tags_jsonObject = tags_jsonArray.getJSONObject(j);
				String label = tags_jsonObject.getString("name");
				if (bypass_blacklabels && blacklabels.contains(label)) {
					continue for_jsonArray;
				}
				int type = Integer.parseInt(tags_jsonObject.getString("type"));
				if (type < 6) {
					if (FileUtil.nameLength(filename.toString()) + FileUtil.nameLength(label) + 1 < 220) {
						filename.append(" ").append(label);
					} else {
						break;
					}
				}
			}
			String file_type = data_jsonObject.getString("file_type");
			file_type = "." + file_type.substring(file_type.lastIndexOf("/") + 1);
			if (file_type.equals("jpeg")) {
				file_type = "jpg";
			}
			String suffix = "." + file_type;
			filename.append(" ").append(suffix);
			String imagefile_url = data_jsonObject.getString("file_url");
			imagesInfo.add(Tuple.of(imageid, imagefile_url, filename.toString()));
		}
		return imagesInfo;
	}

	public static void download(String imageid, String imageUrl, String filename) {
		String imageidUrl = sankaku_url + "cn/post/show/" + imageid;
		logger.info("正在下载: " + imageidUrl);
		usedIds.add(imageid);
		imageUrl = Judge.isEmpty(imageUrl) ? getImageUrl(imageid) : imageUrl;
		int statusCode = SionDownload.connect(imageUrl).proxy(proxyHost, proxyPort).fileName(filename)
				.thread(DOWN_THREADS).retry(MAX_RETRY, MILLISECONDS_SLEEP)
				.folder(image_folderPath).execute().statusCode();
		if (URIUtil.statusIsOK(statusCode)) {
			App.imageCount.addAndGet(1);
			if (record_usedid) {
				ChildRout.WriteFileInfo(imageid, already_usedid_filePath);
			}
		} else {
			logger.error("下载失败 Status: " + statusCode + " URL: " + imageidUrl);
		}
	}

}
