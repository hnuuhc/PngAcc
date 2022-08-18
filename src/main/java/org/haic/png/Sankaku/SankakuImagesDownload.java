package org.haic.png.Sankaku;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.haic.often.FilesUtils;
import org.haic.often.ReadWriteUtils;
import org.haic.often.Multithread.MultiThreadUtils;
import org.haic.often.Multithread.ParameterizedThread;
import org.haic.often.Tuple.ThreeTuple;
import org.haic.png.App;

public class SankakuImagesDownload {

    private static final String image_folderPath = FilesUtils.getAbsolutePath(App.sankaku_image_folderPath);
    private static final String whitelabels_filePath = App.sankaku_whitelabels_filePath;

    private static final int MAX_THREADS = App.MAX_THREADS; // 多线程下载

    public static void label() {
        SankakuSubfunction.initialization(); // 初始化参数
        List<String> whitelabel_lists = ReadWriteUtils.orgin(whitelabels_filePath).list();
        whitelabel_lists.replaceAll(LabelWhite -> LabelWhite.replaceAll(" ", "_"));
        for (String whitelabel : whitelabel_lists) {
            if (SankakuSubfunction.blacklabels.contains(whitelabel)) {
                System.out.println("标签冲突,白名单和黑名单存在相同值: " + whitelabel);
                whitelabel_lists.remove(whitelabel);
            }
        }
        for (String whitelabel : whitelabel_lists) {
            System.out.println("[Schedule] 正在下载 Sankaku 标签白名单图片,当前标签: " + whitelabel + " 存储路径: " + image_folderPath);
            Set<ThreeTuple<String, String, String>> imagesInfo = SankakuSubfunction.getLabelInfo(whitelabel);
            ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
            for (ThreeTuple<String, String, String> imageInfo : imagesInfo) {
                executorService.execute(new ParameterizedThread<>(imageInfo, (info) -> { // 执行多线程程
                    SankakuSubfunction.download(info.first, info.second, info.third);
                }));
            }
            MultiThreadUtils.WaitForEnd(executorService); // 等待线程结束
        }
        System.out.println("下载 Sankaku 标签图片 已完成 存储路径: " + image_folderPath);
    }

}
