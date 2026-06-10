package com.hailong.chatsystem.utils;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
public class ImageCompressUtils {

    /**
     * 压缩头像图片
     * @param file 原文件
     * @param maxWidth 最大宽度
     * @param maxHeight 最大高度
     * @param quality 质量(0.0-1.0)
     * @return 压缩后的字节数组
     */
    public static byte[] compressAvatar(MultipartFile file, int maxWidth, int maxHeight, double quality) {
        try {
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            if (originalImage == null) {
                throw new RuntimeException("无法读取图片文件");
            }

            // 如果图片本身就很小，直接返回原图字节
            if (originalImage.getWidth() <= maxWidth && originalImage.getHeight() <= maxHeight) {
                return file.getBytes();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Thumbnails.of(originalImage)
                    .size(maxWidth, maxHeight)
                    .outputQuality(quality)
                    .outputFormat(getOutputFormat(file.getOriginalFilename()))
                    .toOutputStream(outputStream);

            byte[] compressed = outputStream.toByteArray();
            log.debug("图片压缩完成: {} -> {} bytes, 原大小: {} bytes",
                    file.getOriginalFilename(), compressed.length, file.getSize());

            return compressed;

        } catch (IOException e) {
            log.error("图片压缩失败", e);
            throw new RuntimeException("图片压缩失败", e);
        }
    }

    /**
     * 生成缩略图 (用于聊天图片预览)
     */
    public static byte[] generateThumbnail(byte[] imageData, int size) {
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(imageData);
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            Thumbnails.of(input)
                    .size(size, size)
                    .outputQuality(0.6)
                    .toOutputStream(output);

            return output.toByteArray();
        } catch (IOException e) {
            log.error("生成缩略图失败", e);
            // 失败时返回原图
            return imageData;
        }
    }

    /**
     * 获取输出格式 (默认jpg)
     */
    private static String getOutputFormat(String filename) {
        if (filename == null) return "jpg";
        String ext = FileHashUtils.getFileExtension(filename).toLowerCase();
        // 统一转为jpg以获得更好压缩率，除非是透明背景的png
        if (ext.equals("png") && !filename.toLowerCase().endsWith(".png")) {
            return "png";
        }
        return "jpg";
    }
}