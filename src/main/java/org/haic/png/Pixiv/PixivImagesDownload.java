package org.haic.png.Pixiv;

import org.haic.often.FilesUtil;
import org.haic.often.Multithread.ConsumerThread;
import org.haic.often.Multithread.MultiThreadUtil;
import org.haic.often.ReadWriteUtil;
import org.haic.often.Tuple.ThreeTuple;
import org.haic.png.App;
import org.haic.png.ChildRout;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PixivImagesDownload {

	private static final int MAX_THREADS = App.MAX_THREADS; // 多线程下载
	private static final int MAX_API = App.pixiv_api_maxthreads; // API线程

	private static final String startDate = App.pixiv_start_date;
	private static final String imageFolderPath = FilesUtil.getAbsolutePath(App.pixiv_image_folderPath);
	private static final String authorsUidFilePath = App.pixiv_authors_uid_filePath;
	private static final String record_date_filePath = App.pixiv_record_date_filePath;
	private static final String whitelabels_filePath = App.pixiv_whitelabels_filePath;

	private static final boolean record_date = App.pixiv_record_date; // 记录已完成的日期
	private static final boolean bypass_record_date = App.pixiv_bypass_record_date; // 跳过记录的日期
	private static final boolean unbypass_within_aweek = App.pixiv_unbypass_within_aweek; // 不跳过一星期内的日期
	private static final boolean synchronized_attention = App.pixiv_synchronized_attention;

	public static void author() {
		PixivSubfunction.initialization(); // 初始化参数
		List<String> authorsUids = ReadWriteUtil.orgin(authorsUidFilePath).readAsLine();
		authorsUids.removeIf(l -> l.equals(""));
		List<String> followUserIds = PixivSubfunction.GetFollowUserIds();
		List<String> userIds = new ArrayList<>(followUserIds);
		userIds.removeAll(authorsUids);
		if (!ReadWriteUtil.orgin(authorsUidFilePath).writeAsLine(userIds)) { // 同步账户关注的用户
			throw new RuntimeException("文件写入失败");
		}
		userIds.addAll(authorsUids);
		authorsUids.removeAll(followUserIds);
		if (synchronized_attention) {
			ExecutorService bookmarkAddExecutorService = Executors.newFixedThreadPool(MAX_API);
			for (String authorsUid : authorsUids) { // 关注用户
				bookmarkAddExecutorService.execute(new Thread(() -> { // 执行多线程程
					String info = "关注用户:" + authorsUid + " - ";
					info += PixivSubfunction.bookmarkAdd(authorsUid) ? "true" : "false";
					System.out.println(info);
				}));
			}
			MultiThreadUtil.waitForEnd(bookmarkAddExecutorService); // 等待线程结束
		}
		authorsUids = userIds;
		int len = authorsUids.size();
		for (int i = 0; i < len; i++) {
			String authorsUid = authorsUids.get(i);
			System.out.print("[Schedule] 正在下载 Pixiv 作者图片,当前作者UID: " + authorsUid + " 进度：" + (i + 1) + "/" + len);
			Set<ThreeTuple<String, List<String>, String>> imagesInfo = PixivSubfunction.GetAuthorInfos(authorsUid);
			System.out.println(" 图片数量：" + imagesInfo.stream().mapToInt(l -> l.second.size()).sum());
			MultiThreadDownload(imagesInfo);
		}
	}

	public static void label() {
		PixivSubfunction.initialization(); // 初始化参数
		List<String> whitelabel_lists = ReadWriteUtil.orgin(whitelabels_filePath).readAsLine();
		whitelabel_lists.replaceAll(LabelWhite -> LabelWhite.replaceAll(" ", "_"));
		for (String whitelabel : whitelabel_lists) {
			if (PixivSubfunction.blacklabels.contains(whitelabel)) {
				System.out.println("标签冲突,白名单和黑名单存在相同值: " + whitelabel);
				whitelabel_lists.remove(whitelabel);
			}
		}
		int len = whitelabel_lists.size();
		for (int i = 0; i < len; i++) {
			String whitelabel = whitelabel_lists.get(i);
			System.out.print("[Schedule] 正在下载 Pixiv 标签白名单图片,当前标签: " + whitelabel + " 进度：" + (i + 1) + "/" + len);
			Set<ThreeTuple<String, List<String>, String>> imagesInfo = PixivSubfunction.GetLabelImagesInfo(whitelabel);
			System.out.println(" 图片数量：" + imagesInfo.size() + " 存储路径: " + imageFolderPath);
			MultiThreadNoSuffixDownload(imagesInfo);
		}
	}

	public static void optimal() {
		PixivSubfunction.initialization(); // 初始化参数
		System.out.println("[Schedule] 正在下载 Pixiv 最佳图片 存储路径: " + imageFolderPath);
		MultiThreadNoSuffixDownload(PixivSubfunction.GetOptimalImageInfos());
	}

	public static void popularDaily() {
		PixivSubfunction.initialization(); // 初始化参数
		// 初始化日期
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		LocalDate system_date = LocalDate.now();
		LocalDate current_date = LocalDate.parse(startDate);
		LocalDate within_aweek_date = null;
		if (unbypass_within_aweek) {
			within_aweek_date = system_date.minusDays(7);
		}
		List<String> record_date_lists = ReadWriteUtil.orgin(record_date_filePath).readAsLine();
		while (current_date.isBefore(system_date)) {
			String current_date_str = current_date.format(format);
			if (bypass_record_date && record_date_lists.contains(current_date_str)) {
				if (!unbypass_within_aweek || current_date.isBefore(Objects.requireNonNull(within_aweek_date))) {
					current_date = current_date.plusDays(1);
					continue;
				}
			}
			System.out.println("[Schedule] 正在下载 Pixiv 每日热门图片,当前日期 : " + current_date_str + " 存储路径: " + imageFolderPath);
			MultiThreadNoSuffixDownload(PixivSubfunction.GetHeatdayImageInfos(current_date_str.replaceAll("-", "")));
			if (record_date && !record_date_lists.contains(current_date_str)) {
				ChildRout.WriteFileInfo(current_date_str, record_date_filePath);
				record_date_lists.add(current_date_str);
			}
			current_date = current_date.plusDays(1);
		}
	}

	public static void suggestion() {
		PixivSubfunction.initialization(); // 初始化参数
		System.out.println("[Schedule] 正在下载 Pixiv 推荐图片 存储路径: " + imageFolderPath);
		MultiThreadNoSuffixDownload(PixivSubfunction.GetSuggestionImageInfos());
	}

	private static void MultiThreadDownload(Set<ThreeTuple<String, List<String>, String>> imagesInfo) {
		ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
		for (ThreeTuple<String, List<String>, String> imageInfo : imagesInfo) {
			executorService.execute(new ConsumerThread<>(imageInfo, (info) -> { // 执行多线程程
				PixivSubfunction.download(info.first, info.second, info.third);
			}));
		}
		MultiThreadUtil.waitForEnd(executorService); // 等待线程结束
	}

	private static void MultiThreadNoSuffixDownload(Set<ThreeTuple<String, List<String>, String>> imagesInfo) {
		ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
		for (ThreeTuple<String, List<String>, String> imageInfo : imagesInfo) {
			executorService.execute(new ConsumerThread<>(imageInfo, (info) -> { // 执行多线程程
				PixivSubfunction.noSuffixDownload(info.first, info.second, info.third);
			}));
		}
		MultiThreadUtil.waitForEnd(executorService); // 等待线程结束
	}

}