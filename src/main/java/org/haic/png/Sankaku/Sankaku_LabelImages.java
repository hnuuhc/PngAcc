package org.haic.png.Sankaku;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.haic.often.FilesUtils;
import org.haic.often.Judge;
import org.haic.often.ReadWriteUtils;
import org.haic.often.Multithread.MultiThreadUtils;
import org.haic.often.Multithread.ParameterizedThread;
import org.haic.often.Network.JsoupUtil;
import org.haic.often.Network.NetworkFileUtil;
import org.haic.often.Network.URIUtils;
import org.haic.often.Tuple.ThreeTuple;
import org.haic.often.Tuple.Tuple;
import org.haic.png.App;
import org.haic.png.ChildRout;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sankaku_LabelImages {

	private static final String sankaku_url = App.sankaku_url;
	private static final String sankaku_api_url = "https://capi-v2.sankakucomplex.com/posts/keyset";

	private static final String image_folderPath = FilesUtils.getAbsolutePath(App.sankaku_image_folderPath);
	private static final String whitelabels_filePath = App.sankaku_whitelabels_filePath;
	private static final String blacklabels_filePath = App.sankaku_blacklabels_filePath;
	private static final String already_usedid_filePath = App.sankaku_already_usedid_filePath;

	private static final boolean record_usedid = App.sankaku_record_usedid; // 记录已下载的图片ID
	private static final boolean bypass_usedid = App.sankaku_bypass_usedid; // 跳过已记录的图片ID
	private static final boolean bypass_blacklabels = App.sankaku_bypass_blacklabels;

	private static final String proxyHost = App.proxyHost; // 代理
	private static final int proxyPort = App.proxyPort;

	private static final int limit = App.sankaku_api_limit; // API单页获取数量限制
	private static final int MAX_THREADS = App.MAX_THREADS; // 多线程下载
	private static final int DOWN_THREADS = App.DOWN_THREADS; // 单文件下载线程
	private static final boolean MAX_RETRY = App.MAX_RETRY; // 最大重试次数
	private static final int MILLISECONDS_SLEEP = App.MILLISECONDS_SLEEP; // 程序等待

	private static final Map<String, String> cookies = App.sankaku_cookies;

	private static List<String> blacklabel_lists = new ArrayList<>();
	private static List<String> usedid_lists = new CopyOnWriteArrayList<>();

	private static final Logger logger = LoggerFactory.getLogger(Sankaku_LabelImages.class);

	public static void ImagesDownload() {
		List<String> whitelabel_lists = ReadWriteUtils.orgin(whitelabels_filePath).list();
		whitelabel_lists.replaceAll(LabelWhite -> LabelWhite.replaceAll(" ", "_"));
		if (whitelabel_lists.isEmpty()) {
			System.out.println("File: " + whitelabels_filePath + " is null");
			return;
		}
		blacklabel_lists = ReadWriteUtils.orgin(blacklabels_filePath).list();
		blacklabel_lists.replaceAll(LabelBlack -> LabelBlack.replaceAll(" ", "_"));
		for (String whitelabel : whitelabel_lists) {
			if (blacklabel_lists.contains(whitelabel)) {
				System.out.println("标签冲突,白名单和黑名单存在相同值: " + whitelabel);
				blacklabel_lists.remove(whitelabel);
			}
		}
		usedid_lists = ReadWriteUtils.orgin(already_usedid_filePath).list();
		for (String whitelabel : whitelabel_lists) {
			executeProgram(whitelabel);
		}
		System.out.println("下载 Sankaku 标签图片 已完成 存储路径: " + image_folderPath);
	}

	private static void executeProgram(String whitelabel) {
		System.out.println("正在下载 Sankaku 标签白名单图片,当前标签: " + whitelabel + " 存储路径: " + image_folderPath);
		Set<ThreeTuple<String, String, String>> imagesInfo = GetLabelInfo(whitelabel);
		ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
		for (ThreeTuple<String, String, String> imageInfo : imagesInfo) {
			executorService.execute(new ParameterizedThread<>(imageInfo, (info) -> { // 执行多线程程
				download(info.first, info.second, info.third);
			}));
		}
		MultiThreadUtils.WaitForEnd(executorService); // 等待线程结束
	}

	private static String GetImageUrl(String imageid) {
		Document labelurl_doc = JsoupUtil.connect(sankaku_url + "cn/post/show/" + imageid).timeout(12000)
				.proxy(proxyHost, proxyPort).cookies(cookies)
				.retry(MAX_RETRY, MILLISECONDS_SLEEP).get();
		return "https:" + Objects.requireNonNull(labelurl_doc.selectFirst("a[id='image-link']")).attr("href");
	}

	private static Set<ThreeTuple<String, String, String>> GetLabelInfo(String whitelabel) {
		return GetLabelInfo(whitelabel, sankaku_api_url + "?tags=" + whitelabel + "&limit=" + limit);
	}

	private static Set<ThreeTuple<String, String, String>> GetLabelInfo(String whitelabel, String label_api_url) {
		Set<ThreeTuple<String, String, String>> imagesInfo = new HashSet<>();
		Document document = JsoupUtil.connect(label_api_url).timeout(12000).proxy(proxyHost, proxyPort).cookies(cookies)
				.retry(MAX_RETRY, MILLISECONDS_SLEEP)
				.get();
		JSONObject jsonObject = JSONObject.parseObject(document.text());
		String next = JSONObject.parseObject(jsonObject.getString("meta")).getString("next");
		if (next != null) {
			String next_label_api_url = sankaku_api_url + "?tags=" + whitelabel + "&limit=" + limit + "&next=" + next;
			imagesInfo.addAll(GetLabelInfo(whitelabel, next_label_api_url));
		}
		JSONArray jsonArray = jsonObject.getJSONArray("data");// 获取数组
		for_jsonArray: for (int i = 0; i < jsonArray.size(); i++) {
			JSONObject data_jsonObject = jsonArray.getJSONObject(i);
			String imageid = data_jsonObject.getString("id");
			if (bypass_usedid && usedid_lists.contains(imageid)) {
				continue;
			}
			JSONArray tags_jsonArray = data_jsonObject.getJSONArray("tags");// 获取数组
			StringBuilder filename = new StringBuilder("sankaku " + imageid);
			for (int j = 0; j < tags_jsonArray.size(); j++) {
				JSONObject tags_jsonObject = tags_jsonArray.getJSONObject(j);
				String label = tags_jsonObject.getString("name");
				if (bypass_blacklabels && blacklabel_lists.contains(label)) {
					continue for_jsonArray;
				}
				int type = Integer.parseInt(tags_jsonObject.getString("type"));
				if (type < 6) {
					if (filename.length() + label.length() < 180) {
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

	private static void download(String imageid, String imageUrl, String filename) {
		String imageidUrl = sankaku_url + "cn/post/show/" + imageid;
		logger.info("Sankaku - 正在下载 ID: " + imageid + " URL: " + imageidUrl);
		usedid_lists.add(imageid);
		imageUrl = Judge.isEmpty(imageUrl) ? GetImageUrl(imageid) : imageUrl;
		int statusCode = NetworkFileUtil.connect(imageUrl).proxy(proxyHost, proxyPort).filename(filename)
				.multithread(DOWN_THREADS)
				.retry(MAX_RETRY, MILLISECONDS_SLEEP).download(image_folderPath);
		if (URIUtils.statusIsOK(statusCode)) {
			App.imageCount.addAndGet(1);
			if (record_usedid) {
				ChildRout.WriteFileInfo(imageid, already_usedid_filePath);
			}
		} else {
			logger.error("Sankaku - 下载失败 ID: " + imageid + " URL: " + imageidUrl);
		}
	}

}
