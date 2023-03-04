package org.haic.png.Yande;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.haic.often.parser.json.JSONObject;
import org.haic.often.util.FileUtil;
import org.haic.often.util.ReadWriteUtil;
import org.haic.often.util.ThreadUtil;
import org.haic.png.App;
import org.haic.png.ChildRout;

public class YandeImagesDownload {

	private static final int MAX_THREADS = App.MAX_THREADS; // 多线程下载

	private static final boolean record_date = App.yande_record_date; // 记录已完成的日期
	private static final boolean bypass_record_date = App.yande_bypass_record_date; // 跳过记录的日期
	private static final boolean unbypass_within_aweek = App.yande_unbypass_within_aweek; // 不跳过一星期内的日期

	private static final String start_date = App.yande_start_date; // 开始日期
	private static final String image_folderPath = FileUtil.getAbsolutePath(App.yande_image_folderPath); // 图片文件夹
	private static final String record_date_filePath = App.yande_record_date_filePath; // 日期文件
	private static final String whitelabels_filePath = App.yande_whitelabels_filePath; // 白名单文件

	private static final Consumer<List<JSONObject>> download = imagesInfo -> {
		ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS); // 限制多线程
		for (JSONObject imageInfo : imagesInfo) { // 下载
			executorService.execute(new Thread(() -> { // 程序
				YandeSubfunction.download(imageInfo);
			}));
		}
		ThreadUtil.waitEnd(executorService); // 等待线程结束
	};

	public static void label() {
		YandeSubfunction.initialization(); // 初始化参数
		List<String> whitelabels = ReadWriteUtil.orgin(whitelabels_filePath).readAsLine();
		whitelabels.replaceAll(LabelWhite -> LabelWhite.replaceAll(" ", "_"));
		for (String whitelabel : whitelabels) {
			if (YandeSubfunction.blacklabels.contains(whitelabel)) {
				System.out.println("标签冲突,白名单和黑名单存在相同值: " + whitelabel);
				whitelabels.remove(whitelabel);
			}
		}
		int len = whitelabels.size();
		for (int i = 0; i < len; i++) {
			String whitelabel = whitelabels.get(i);
			System.out.print("[Schedule] 正在下载 Yande 标签白名单图片,当前标签: " + whitelabel + " 进度：" + (i + 1) + "/" + len);
			List<JSONObject> imagesInfo = YandeSubfunction.GetLabelImagesInfo(whitelabel);
			imagesInfo = imagesInfo.stream()
					.sorted(Comparator.comparing(l -> l.getInteger("id"), Comparator.reverseOrder()))
					.collect(Collectors.toList());
			System.out.println(" 图片数量：" + imagesInfo.size() + " 存储路径: " + image_folderPath);
			download.accept(imagesInfo);
		}

	}

	public static void label(int start, int len) {
		YandeSubfunction.initialization(); // 初始化参数
		List<String> whitelabels = ReadWriteUtil.orgin(whitelabels_filePath).readAsLine();
		whitelabels.replaceAll(LabelWhite -> LabelWhite.replaceAll(" ", "_"));
		for (String whitelabel : whitelabels) {
			if (YandeSubfunction.blacklabels.contains(whitelabel)) {
				System.out.println("标签冲突,白名单和黑名单存在相同值: " + whitelabel);
				whitelabels.remove(whitelabel);
			}
		}
		System.out.print("[Schedule] 正在下载 Yande 标签白名单图片");
		List<JSONObject> imagesInfo = YandeSubfunction.GetLabelImagesInfoAsGlobal(whitelabels, start, len);
		imagesInfo = imagesInfo.stream()
				.sorted(Comparator.comparing(l -> l.getInteger("id"), Comparator.reverseOrder()))
				.collect(Collectors.toList());
		System.out.println(" 图片数量：" + imagesInfo.size() + " 存储路径: " + image_folderPath);
		download.accept(imagesInfo);
	}

	public static void blackGlobal(int start, int len) {
		YandeSubfunction.initialization(); // 初始化参数
		System.out.print("[Schedule] 正在下载 Yande 图片");
		List<JSONObject> imagesInfo = YandeSubfunction.GetLabelImagesInfoAsGlobal(new ArrayList<>(), start, len);
		imagesInfo = imagesInfo.stream()
				.sorted(Comparator.comparing(l -> l.getInteger("id"), Comparator.reverseOrder()))
				.collect(Collectors.toList());
		System.out.println(" 图片数量：" + imagesInfo.size() + " 存储路径: " + image_folderPath);
		download.accept(imagesInfo);
	}

	public static void popularDaily() {
		YandeSubfunction.initialization(); // 初始化参数
		// 初始化日期
		DateTimeFormatter date_format = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		LocalDate systemDate = LocalDate.now();
		LocalDate currentDate = LocalDate.parse(start_date);
		LocalDate withinAweekDate = systemDate.minusDays(7);
		List<String> recordDateLists = ReadWriteUtil.orgin(record_date_filePath).readAsLine();
		while (!currentDate.isAfter(systemDate)) {
			String current_date_str = currentDate.format(date_format);
			if (bypass_record_date && recordDateLists.contains(current_date_str)) {
				if (!unbypass_within_aweek || currentDate.isBefore(Objects.requireNonNull(withinAweekDate))) {
					currentDate = currentDate.plusDays(1);
					continue;
				}
			}
			int year = currentDate.getYear();
			int month = currentDate.getMonthValue();
			int day = currentDate.getDayOfMonth();
			System.out.print("[Schedule] 正在下载每日热门图片,当前日期 : " + year + "-" + month + "-" + day);
			List<JSONObject> imagesInfo = YandeSubfunction.GetHeatdayImagesInfo(year, month, day);
			System.out.println(" 图片数量：" + imagesInfo.size() + " 存储路径: " + image_folderPath);
			download.accept(imagesInfo);
			if (record_date && !recordDateLists.contains(current_date_str)) {
				ChildRout.WriteFileInfo(current_date_str, record_date_filePath);
				recordDateLists.add(current_date_str);
			}
			currentDate = currentDate.plusDays(1);
		}
	}

}
